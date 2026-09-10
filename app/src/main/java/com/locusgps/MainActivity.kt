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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val locationRepository by lazy { LocationRepository(applicationContext) }
    private val apiClient by lazy { ApiClient() }
    private var apiStatus by mutableStateOf("Comprobando API…")
    private var route by mutableStateOf<RouteResult?>(null)
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
                hasMapTilerKey = BuildConfig.MAPTILER_KEY.isNotBlank(),
                apiStatus = apiStatus,
                onRequestLocation = ::requestLocation,
                onRequestDemoRoute = { requestDemoRoute(location) },
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
