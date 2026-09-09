package com.locusgps.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float?,
    val accuracyMeters: Float,
)

/** Local-only location source. It never transmits a GPS update. */
class LocationRepository(context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val _location = MutableStateFlow<UserLocation?>(null)
    val location = _location.asStateFlow()

    private val listener = LocationListener { location -> _location.value = location.toUserLocation() }
    private var isStarted = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (isStarted) return
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        manager.getLastKnownLocation(provider)?.let { _location.value = it.toUserLocation() }
        manager.requestLocationUpdates(provider, 2_000L, 3f, listener, Looper.getMainLooper())
        isStarted = true
    }

    fun stop() {
        if (!isStarted) return
        manager.removeUpdates(listener)
        isStarted = false
    }

    private fun Location.toUserLocation() = UserLocation(
        latitude = latitude,
        longitude = longitude,
        bearing = if (hasBearing()) bearing else null,
        accuracyMeters = accuracy,
    )
}
