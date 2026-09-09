package com.locusgps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.locusgps.location.LocationRepository
import com.locusgps.ui.LocusGpsApp

class MainActivity : ComponentActivity() {
    private val locationRepository by lazy { LocationRepository(applicationContext) }
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
                hasMapTilerKey = BuildConfig.MAPTILER_KEY.isNotBlank(),
                onRequestLocation = ::requestLocation,
            )
        }
        requestLocation()
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
