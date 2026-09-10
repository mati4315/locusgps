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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val locationRepository by lazy { LocationRepository(applicationContext) }
    private val apiClient by lazy { ApiClient() }
    private var apiStatus by mutableStateOf("Comprobando API…")
    private var route by mutableStateOf<RouteResult?>(null)
    private var searchResults by mutableStateOf<List<SearchPlace>>(emptyList())
    private var searching by mutableStateOf(false)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.any { it }) locationRepository.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val location by locationRepository.location.collectAsStateWithLifecycle()
            LocusGpsApp(
                location = location,
                route = route,
                searchResults = searchResults,
                searching = searching,
                hasMapTilerKey = BuildConfig.MAPTILER_KEY.isNotBlank(),
                apiStatus = apiStatus,
                onRequestLocation = ::requestLocation,
                onRequestDemoRoute = { requestDemoRoute(location) },
                onSearch = { query -> search(query, location) },
                onSelectPlace = { place -> selectPlace(place, location) },
            )
        }
        lifecycleScope.launch {
            apiStatus = apiClient.health()
                .fold({ "API conectada" }, { "API no disponible" })
        }
        requestLocation()
    }

    private fun requestDemoRoute(location: com.locusgps.location.UserLocation?) {
        if (location == null) return
        lifecycleScope.launch {
            val destination = RoutePoint(location.latitude + 0.01, location.longitude + 0.01)
            apiClient.route(RoutePoint(location.latitude, location.longitude), destination)
                .onSuccess { route = it; apiStatus = "Ruta lista" }
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
        lifecycleScope.launch {
            apiClient.route(RoutePoint(location.latitude, location.longitude), RoutePoint(place.latitude, place.longitude))
                .onSuccess { route = it; apiStatus = "Destino seleccionado" }
                .onFailure { apiStatus = "Error de ruta" }
        }
    }

    override fun onStop() {
        super.onStop()
        locationRepository.stop()
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
