package com.locusgps.map

import android.location.Location
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.locusgps.location.UserLocation
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.location.LocationComponentActivationOptions

@Composable
fun MapScreen(location: UserLocation?, hasMapTilerKey: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val latestLocation = rememberUpdatedState(location)
    val isLocationPuckReady = remember { mutableStateOf(false) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            onCreate(null)
            getMapAsync { map ->
                MapStyleConfig.styleUrl()?.let { styleUrl ->
                    map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                        map.locationComponent.activateLocationComponent(
                            LocationComponentActivationOptions.builder(context, style)
                                // The app owns GPS sampling; MapLibre only renders the location puck.
                                .useDefaultLocationEngine(false)
                                .build(),
                        )
                        map.locationComponent.isLocationComponentEnabled = true
                        isLocationPuckReady.value = true
                        latestLocation.value?.let { map.locationComponent.forceLocationUpdate(it.toAndroidLocation()) }
                    }
                }
            }
        }
    }

    DisposableEffect(mapView) {
        mapView.onStart()
        onDispose {
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(location, hasMapTilerKey, isLocationPuckReady.value) {
        if (hasMapTilerKey && location != null) {
            mapView.getMapAsync { map ->
                if (isLocationPuckReady.value) {
                    map.locationComponent.forceLocationUpdate(location.toAndroidLocation())
                }
                map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(location.latitude, location.longitude))
                            .zoom(15.5)
                            .bearing(location.bearing?.toDouble() ?: 0.0)
                            .build(),
                    ),
                )
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun UserLocation.toAndroidLocation() = Location("locus-local").apply {
    latitude = this@toAndroidLocation.latitude
    longitude = this@toAndroidLocation.longitude
    accuracy = accuracyMeters
    bearing?.let { bearing = it }
}
