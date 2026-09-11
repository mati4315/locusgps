package com.locusgps.map

import android.location.Location
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color as AndroidColor
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
import com.locusgps.api.MapPoint
import com.locusgps.api.RoutePoint
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.location.LocationComponentActivationOptions

@Composable
fun MapScreen(location: UserLocation?, route: RouteResult?, mapPoints: List<MapPoint>, recenterRequest: Int, hasMapTilerKey: Boolean, onLongPress: (RoutePoint) -> Unit, onCameraBearingChanged: (Float) -> Unit, onFollowChanged: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val latestLocation = rememberUpdatedState(location)
    val isLocationPuckReady = remember { mutableStateOf(false) }
    val lastHandledRecenter = remember { mutableStateOf(0) }
    val followLocation = remember { mutableStateOf(route != null) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            onCreate(null)
            getMapAsync { map ->
                map.addOnCameraIdleListener { onCameraBearingChanged(map.cameraPosition.bearing.toFloat()) }
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        followLocation.value = false
                        onFollowChanged(false)
                    }
                }
                map.addOnMapLongClickListener { point ->
                    onLongPress(RoutePoint(point.latitude, point.longitude))
                    true
                }
                MapStyleConfig.styleUrl()?.let { styleUrl ->
                    map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                        // Vista inicial del proyecto: Gold Coast, Queensland.
                        // La cámara solo seguirá al GPS durante una navegación activa.
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(-28.0167, 153.4000))
                            .zoom(10.5)
                            .build()
                        route?.let { style.addRoute(it) }
                        map.locationComponent.activateLocationComponent(
                            LocationComponentActivationOptions.builder(context, style)
                                // The app owns GPS sampling; MapLibre only renders the location puck.
                                .useDefaultLocationEngine(false)
                                .build(),
                        )
                        map.locationComponent.isLocationComponentEnabled = true
                        style.addMapPoints(mapPoints)
                        style.addDestination(route?.geometry?.lastOrNull())
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

    LaunchedEffect(location, route, recenterRequest, hasMapTilerKey, isLocationPuckReady.value) {
        if (hasMapTilerKey && location != null) {
            mapView.getMapAsync { map ->
                if (isLocationPuckReady.value) {
                    map.locationComponent.forceLocationUpdate(location.toAndroidLocation())
                }
                // Follow the user only during active navigation. A manual recenter
                // request still works while browsing the map without a route.
                if (recenterRequest != lastHandledRecenter.value) {
                    followLocation.value = true
                    onFollowChanged(true)
                }
                val shouldCenter = followLocation.value || recenterRequest != lastHandledRecenter.value
                if (shouldCenter) {
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(location.latitude, location.longitude))
                                .zoom(15.5)
                                .bearing(if (route != null) location.bearing?.toDouble() ?: 0.0 else map.cameraPosition.bearing)
                                .build(),
                        ),
                    )
                    lastHandledRecenter.value = recenterRequest
                }
            }
        }
    }

    LaunchedEffect(route) {
        followLocation.value = route != null
        onFollowChanged(route != null)
        if (route != null) {
            mapView.getMapAsync { map -> map.style?.addRoute(route) }
        }
    }

    LaunchedEffect(mapPoints) {
        mapView.getMapAsync { map -> map.style?.addMapPoints(mapPoints) }
    }

    LaunchedEffect(route) {
        mapView.getMapAsync { map -> map.style?.addDestination(route?.geometry?.lastOrNull()) }
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

private fun Style.addMapPoints(points: List<MapPoint>) {
    val sourceId = "locus-points-source"
    val layerId = "locus-points-layer"
    val features = points.map {
        Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)).apply {
            addBooleanProperty("isCamera", it.type in setOf("camera", "speed_camera", "traffic_light_camera"))
        }
    }
    val collection = FeatureCollection.fromFeatures(features)
    getSource(sourceId)?.let { (it as GeoJsonSource).setGeoJson(collection); return }
    addSource(GeoJsonSource(sourceId, collection))
    if (getImage("locus-camera-icon") == null) addImage("locus-camera-icon", createCameraIcon())
    addLayer(CircleLayer(layerId, sourceId).withProperties(circleColor("#FFB74D"), circleRadius(7f)))
    addLayer(
        SymbolLayer("locus-camera-icons-layer", sourceId)
            .withFilter(Expression.eq(Expression.get("isCamera"), Expression.literal(true)))
            .withProperties(iconImage("locus-camera-icon"), iconSize(0.72f), iconAllowOverlap(true), iconIgnorePlacement(true)),
    )
}

private fun createCameraIcon(): Bitmap {
    val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(48f, 48f, 44f, paint)
    paint.color = AndroidColor.rgb(211, 47, 47)
    canvas.drawRoundRect(20f, 33f, 76f, 70f, 8f, 8f, paint)
    val lens = Path().apply { moveTo(36f, 33f); lineTo(42f, 24f); lineTo(59f, 24f); lineTo(65f, 33f); close() }
    canvas.drawPath(lens, paint)
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(48f, 51f, 12f, paint)
    paint.color = AndroidColor.rgb(211, 47, 47)
    canvas.drawCircle(48f, 51f, 7f, paint)
    return bitmap
}

private fun Style.addDestination(destination: RoutePoint?) {
    val sourceId = "locus-destination-source"
    val layerId = "locus-destination-layer"
    val feature = destination?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
    val collection = feature?.let { FeatureCollection.fromFeature(it) } ?: FeatureCollection.fromFeatures(emptyList())
    getSource(sourceId)?.let { (it as GeoJsonSource).setGeoJson(collection); return }
    addSource(GeoJsonSource(sourceId, collection))
    addLayer(
        SymbolLayer(layerId, sourceId).withProperties(
            textField("⌂"),
            textSize(28f),
            textColor("#D32F2F"),
            textHaloColor("#FFFFFF"),
            textHaloWidth(2f),
        ),
    )
}

private fun UserLocation.toAndroidLocation() = Location("locus-local").apply {
    latitude = this@toAndroidLocation.latitude
    longitude = this@toAndroidLocation.longitude
    accuracy = accuracyMeters
    bearing?.let { bearing = it }
}
