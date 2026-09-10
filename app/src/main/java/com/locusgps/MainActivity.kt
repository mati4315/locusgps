package com.locusgps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.locusgps.location.LocationRepository
import com.locusgps.ui.LocusGpsApp
import com.locusgps.api.ApiClient
import com.locusgps.api.RoutePoint
import com.locusgps.api.RouteResult
import com.locusgps.api.SearchPlace
import com.locusgps.api.MapPoint
import com.locusgps.navigation.NavigationEngine
import com.locusgps.navigation.VoiceInstructionEngine
import com.locusgps.navigation.PointAlertEngine
import com.locusgps.navigation.RouteSimulator
import com.locusgps.navigation.SimulationCursor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.PI
import kotlin.random.Random
import com.locusgps.settings.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    private val locationRepository by lazy { LocationRepository(applicationContext) }
    private val apiClient by lazy { ApiClient() }
    private val voiceEngine by lazy { VoiceInstructionEngine(applicationContext) }
    private val pointAlertEngine = PointAlertEngine()
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private var apiStatus by mutableStateOf("Comprobando API…")
    private var route by mutableStateOf<RouteResult?>(null)
    private var searchResults by mutableStateOf<List<SearchPlace>>(emptyList())
    private var searching by mutableStateOf(false)
    private var mapPoints by mutableStateOf<List<MapPoint>>(emptyList())
    private var lastPointsFetchAt = 0L
    private var destination by mutableStateOf<RoutePoint?>(null)
    private var lastRecalculationAt = 0L
    private var recalculating = false
    private var pointAlert by mutableStateOf<String?>(null)
    private var simulationPoint by mutableStateOf<RoutePoint?>(null)
    private var simulationRunning by mutableStateOf(false)
    private var simulationSpeed by mutableStateOf(40f)
    private var simulationCursor = SimulationCursor()
    private var simulationDetourTicks = 0
    private var recenterRequest by mutableStateOf(0)
    private var contextPoint by mutableStateOf<RoutePoint?>(null)
    private var simulationJob: Job? = null
    private var voiceEnabled by mutableStateOf(false)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.any { it }) locationRepository.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        voiceEnabled = settingsRepository.voiceEnabled
        voiceEngine.enabled = voiceEnabled

        setContent {
            val location by locationRepository.location.collectAsStateWithLifecycle()
            val displayLocation = simulationPoint?.let { point -> com.locusgps.location.UserLocation(point.latitude, point.longitude, null, 5f) } ?: location
            LocusGpsApp(
                location = displayLocation,
                route = route,
                searchResults = searchResults,
                searching = searching,
                mapPoints = mapPoints,
                pointAlert = pointAlert,
                voiceEnabled = voiceEnabled,
                hasMapTilerKey = BuildConfig.MAPTILER_KEY.isNotBlank(),
                apiStatus = apiStatus,
                onRequestLocation = ::requestLocation,
                recenterRequest = recenterRequest,
                onCenterLocation = { recenterRequest++ ; requestLocation() },
                contextPoint = contextPoint,
                onLongPressMap = { contextPoint = it },
                onDismissContext = { contextPoint = null },
                onGoToContext = { point -> contextPoint = null; routeToPoint(point, displayLocation) },
                onSaveContext = { point -> contextPoint = null; saveMapPoint(point) },
                onRequestDemoRoute = { requestDemoRoute(location) },
                onSearch = { query -> search(query, location) },
                onSelectPlace = { place -> selectPlace(place, location) },
                onToggleVoice = { voiceEnabled = !voiceEnabled; settingsRepository.voiceEnabled = voiceEnabled; voiceEngine.enabled = voiceEnabled },
                onSaveCurrentPoint = { saveCurrentPoint(location) },
                onFinishNavigation = ::finishNavigation,
                simulationRunning = simulationRunning,
                simulationSpeed = simulationSpeed,
                onOpenSimulation = { },
                onSetSimulationSpeed = { simulationSpeed = it },
                onStartSimulation = ::startSimulation,
                onPauseSimulation = ::pauseSimulation,
                onStopSimulation = ::stopSimulation,
                onSimulateDetour = ::simulateDetour,
                onRandomDestination = { randomDestination(displayLocation) },
            )
        }
        lifecycleScope.launch {
            apiStatus = apiClient.health()
                .fold({ "API conectada" }, { "API no disponible" })
        }
        lifecycleScope.launch {
            locationRepository.location.collect { current ->
                val currentLocation = current ?: return@collect
                val now = System.currentTimeMillis()
                if (now - lastPointsFetchAt >= 60_000) {
                    lastPointsFetchAt = now
                    val currentPoint = RoutePoint(currentLocation.latitude, currentLocation.longitude)
                    var personalPoints = emptyList<MapPoint>()
                    apiClient.mapPoints(currentPoint)
                        .onSuccess { personalPoints = it }
                    apiClient.cameraLocations(currentPoint)
                        .onSuccess { officialPoints -> mapPoints = personalPoints + officialPoints }
                        .onFailure { mapPoints = personalPoints }
                }
                pointAlertEngine.update(mapPoints, RoutePoint(currentLocation.latitude, currentLocation.longitude))?.let { alert ->
                    pointAlert = "${alert.title} · ${alert.distanceMeters.toInt()} m"
                }
                val activeRoute = route
                val activeDestination = destination
                if (activeRoute == null || activeDestination == null || recalculating) return@collect
                val state = NavigationEngine.update(activeRoute, currentLocation) ?: return@collect
                voiceEngine.announceNextInstruction(activeRoute, currentLocation)
                val checkNow = System.currentTimeMillis()
                if (state.offRoute && checkNow - lastRecalculationAt >= 15_000) {
                    lastRecalculationAt = checkNow
                    recalculating = true
                    apiClient.route(RoutePoint(currentLocation.latitude, currentLocation.longitude), activeDestination)
                        .onSuccess { route = it; apiStatus = "Ruta recalculada" }
                        .onFailure { apiStatus = "No se pudo recalcular" }
                    recalculating = false
                }
            }
        }
        requestLocation()
        handleExternalNavigation(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalNavigation(intent)
    }

    private fun handleExternalNavigation(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val point = intent.data?.let(::pointFromUri) ?: return
        lifecycleScope.launch {
            val location = locationRepository.location.filterNotNull().first()
            routeToPoint(point, location)
        }
    }

    private fun pointFromUri(uri: Uri): RoutePoint? {
        if (uri.scheme == "locusgps" && uri.host == "navigate") {
            val latitude = uri.getQueryParameter("lat")?.toDoubleOrNull()
            val longitude = uri.getQueryParameter("lon")?.toDoubleOrNull()
            return validPoint(latitude, longitude)
        }
        if (uri.scheme == "geo") {
            val raw = uri.schemeSpecificPart.substringBefore('?')
            val direct = raw.split(',').takeIf { it.size >= 2 }
            val latitude = direct?.getOrNull(0)?.toDoubleOrNull()
            val longitude = direct?.getOrNull(1)?.toDoubleOrNull()
            if (latitude != null && longitude != null && (latitude != 0.0 || longitude != 0.0)) return validPoint(latitude, longitude)
            val query = uri.getQueryParameter("q")?.substringBefore('(')?.split(',')
            return validPoint(query?.getOrNull(0)?.toDoubleOrNull(), query?.getOrNull(1)?.toDoubleOrNull())
        }
        return null
    }

    private fun validPoint(latitude: Double?, longitude: Double?): RoutePoint? = if (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0) RoutePoint(latitude, longitude) else null

    private fun requestDemoRoute(location: com.locusgps.location.UserLocation?) {
        if (location == null) return
        val demoDestination = RoutePoint(location.latitude + 0.01, location.longitude + 0.01)
        destination = demoDestination
        lifecycleScope.launch {
            apiClient.route(RoutePoint(location.latitude, location.longitude), demoDestination)
                .onSuccess { route = it; voiceEngine.announceRoute(it.distanceMeters, it.durationSeconds); apiStatus = "Ruta lista" }
                .onFailure { apiStatus = "Error de ruta" }
        }
    }

    private fun search(query: String, location: com.locusgps.location.UserLocation?) {
        if (query.trim().length < 2) return
        lifecycleScope.launch {
            searching = true
            apiClient.search(query, location?.let { RoutePoint(it.latitude, it.longitude) })
                .onSuccess { searchResults = it }
                .onFailure { searchResults = emptyList(); apiStatus = "Error de búsqueda" }
            searching = false
        }
    }

    private fun selectPlace(place: SearchPlace, location: com.locusgps.location.UserLocation?) {
        searchResults = emptyList()
        if (location == null) return
        val selectedDestination = RoutePoint(place.latitude, place.longitude)
        destination = selectedDestination
        lifecycleScope.launch {
            apiClient.route(RoutePoint(location.latitude, location.longitude), selectedDestination)
                .onSuccess { route = it; voiceEngine.announceRoute(it.distanceMeters, it.durationSeconds); apiStatus = "Destino seleccionado" }
                .onFailure { apiStatus = "Error de ruta" }
        }
    }

    private fun saveCurrentPoint(location: com.locusgps.location.UserLocation?) {
        if (location == null) { apiStatus = "Ubicación no disponible"; return }
        lifecycleScope.launch {
            apiClient.createMapPoint(RoutePoint(location.latitude, location.longitude), "Punto guardado")
                .onSuccess { mapPoints = mapPoints + it; apiStatus = "Punto guardado" }
                .onFailure { apiStatus = "No se pudo guardar el punto" }
        }
    }

    private fun saveMapPoint(point: RoutePoint) {
        lifecycleScope.launch {
            apiClient.createMapPoint(point, "Punto guardado")
                .onSuccess { mapPoints = mapPoints + it; apiStatus = "Punto guardado" }
                .onFailure { apiStatus = "No se pudo guardar el punto" }
        }
    }

    private fun routeToPoint(point: RoutePoint, location: com.locusgps.location.UserLocation?) {
        if (location == null) { apiStatus = "Ubicación no disponible"; return }
        destination = point
        stopSimulation()
        lifecycleScope.launch {
            apiClient.route(RoutePoint(location.latitude, location.longitude), point)
                .onSuccess { route = it; apiStatus = "Destino seleccionado" }
                .onFailure { apiStatus = "No se pudo calcular la ruta" }
        }
    }

    private fun finishNavigation() {
        route = null
        destination = null
        pointAlert = null
        lastRecalculationAt = 0L
        apiStatus = "Navegación finalizada"
        stopSimulation()
    }

    private fun startSimulation() {
        val activeRoute = route ?: return
        if (simulationPoint == null) simulationPoint = activeRoute.geometry.firstOrNull()?.let { RoutePoint(it.latitude, it.longitude) }
        simulationRunning = true
        simulationJob?.cancel()
        simulationJob = lifecycleScope.launch {
            while (isActive && simulationRunning) {
                delay(1000)
                if (simulationDetourTicks > 0) {
                    simulationDetourTicks--
                } else {
                    val next = RouteSimulator.advance(activeRoute, simulationCursor, simulationSpeed, 1.0)
                    simulationCursor = next.first
                    simulationPoint = next.second
                }
            }
        }
    }

    private fun pauseSimulation() { simulationRunning = false; simulationJob?.cancel() }

    private fun stopSimulation() {
        simulationRunning = false
        simulationJob?.cancel()
        simulationJob = null
        simulationPoint = null
        simulationCursor = SimulationCursor()
        simulationDetourTicks = 0
    }

    private fun simulateDetour() {
        val point = simulationPoint ?: return
        simulationPoint = RoutePoint(point.latitude + 0.0025, point.longitude + 0.0025)
        simulationDetourTicks = 10
    }

    private fun randomDestination(location: com.locusgps.location.UserLocation?) {
        if (location == null) return
        val distanceMeters = Random.nextDouble(1_000.0, 25_000.0)
        val bearing = Random.nextDouble(0.0, 2 * PI)
        val latitudeDelta = (distanceMeters * kotlin.math.cos(bearing)) / 111_320.0
        val longitudeDelta = (distanceMeters * kotlin.math.sin(bearing)) / (111_320.0 * cos(Math.toRadians(location.latitude)).coerceAtLeast(0.1))
        val selectedDestination = RoutePoint(location.latitude + latitudeDelta, location.longitude + longitudeDelta)
        destination = selectedDestination
        stopSimulation()
        lifecycleScope.launch {
            apiClient.route(RoutePoint(location.latitude, location.longitude), selectedDestination)
                .onSuccess { route = it; apiStatus = "Destino aleatorio listo" }
                .onFailure { apiStatus = "No se encontró ruta aleatoria" }
        }
    }

    override fun onStop() {
        super.onStop()
        locationRepository.stop()
    }

    override fun onDestroy() {
        voiceEngine.shutdown()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        requestLocation()
    }

    private fun requestLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            locationRepository.start()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}
