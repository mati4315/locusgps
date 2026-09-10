package com.locusgps

import android.Manifest
import android.content.pm.PackageManager
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
import com.locusgps.settings.SettingsRepository
import kotlinx.coroutines.launch

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
            LocusGpsApp(
                location = location,
                route = route,
                searchResults = searchResults,
                searching = searching,
                mapPoints = mapPoints,
                pointAlert = pointAlert,
                voiceEnabled = voiceEnabled,
                hasMapTilerKey = BuildConfig.MAPTILER_KEY.isNotBlank(),
                apiStatus = apiStatus,
                onRequestLocation = ::requestLocation,
                onRequestDemoRoute = { requestDemoRoute(location) },
                onSearch = { query -> search(query, location) },
                onSelectPlace = { place -> selectPlace(place, location) },
                onToggleVoice = { voiceEnabled = !voiceEnabled; settingsRepository.voiceEnabled = voiceEnabled; voiceEngine.enabled = voiceEnabled },
                onSaveCurrentPoint = { saveCurrentPoint(location) },
                onFinishNavigation = ::finishNavigation,
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
                    apiClient.mapPoints(RoutePoint(currentLocation.latitude, currentLocation.longitude))
                        .onSuccess { mapPoints = it }
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
    }

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

    private fun finishNavigation() {
        route = null
        destination = null
        pointAlert = null
        lastRecalculationAt = 0L
        apiStatus = "Navegación finalizada"
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
