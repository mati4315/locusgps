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
import com.locusgps.api.RouteResult
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.location.LocationComponentActivationOptions

@Composable
fun MapScreen(location: UserLocation?, route: RouteResult?, hasMapTilerKey: Boolean, modifier: Modifier = Modifier) {
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
                        route?.let { style.addRoute(it) }
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

    LaunchedEffect(route) {
        if (route != null) {
            mapView.getMapAsync { map -> map.style?.addRoute(route) }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun Style.addRoute(route: RouteResult) {
    if (route.geometry.size < 2) return
    val points = route.geometry.map { Point.fromLngLat(it.longitude, it.latitude) }
    val sourceId = "locus-route-source"
    val layerId = "locus-route-layer"
    val feature = Feature.fromGeometry(LineString.fromLngLats(points))
    getSource(sourceId)?.let { (it as GeoJsonSource).setGeoJson(FeatureCollection.fromFeature(feature)); return }
    addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeature(feature)))
    addLayer(LineLayer(layerId, sourceId).withProperties(lineColor("#4FC3F7"), lineWidth(5f)))
}

private fun UserLocation.toAndroidLocation() = Location("locus-local").apply {
    latitude = this@toAndroidLocation.latitude
    longitude = this@toAndroidLocation.longitude
    accuracy = accuracyMeters
    bearing?.let { bearing = it }
}
