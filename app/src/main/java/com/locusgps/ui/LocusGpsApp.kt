package com.locusgps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.locusgps.location.UserLocation
import com.locusgps.api.RouteResult
import com.locusgps.api.SearchPlace
import com.locusgps.api.MapPoint
import com.locusgps.api.RoutePoint
import com.locusgps.map.MapScreen
import com.locusgps.navigation.NavigationEngine
import kotlinx.coroutines.delay

private val DarkColors = darkColorScheme(
    primary = Color(0xFF67D4FF),
    surface = Color(0xFF101419),
    surfaceVariant = Color(0xFF1A222B),
)

@Composable
fun LocusGpsApp(location: UserLocation?, route: RouteResult?, mapPoints: List<MapPoint>, pointAlert: String?, searchResults: List<SearchPlace>, searching: Boolean, voiceEnabled: Boolean, simulationRunning: Boolean, simulationSpeed: Float, hasMapTilerKey: Boolean, apiStatus: String, onRequestLocation: () -> Unit, recenterRequest: Int, onCenterLocation: () -> Unit, contextPoint: RoutePoint?, onLongPressMap: (RoutePoint) -> Unit, onDismissContext: () -> Unit, onGoToContext: (RoutePoint) -> Unit, onSaveContext: (RoutePoint) -> Unit, onRequestDemoRoute: () -> Unit, onSearch: (String) -> Unit, onSelectPlace: (SearchPlace) -> Unit, onToggleVoice: () -> Unit, onSaveCurrentPoint: () -> Unit, onFinishNavigation: () -> Unit, onOpenSimulation: () -> Unit, onSetSimulationSpeed: (Float) -> Unit, onStartSimulation: () -> Unit, onPauseSimulation: () -> Unit, onStopSimulation: () -> Unit, onSimulateDetour: () -> Unit, onRandomDestination: () -> Unit) {
    var showLayers by remember { mutableStateOf(false) }
    var showSimulation by remember { mutableStateOf(false) }
    var enabledTypes by remember { mutableStateOf(setOf("favorite", "camera", "speed_camera", "traffic_light_camera", "danger", "school_zone", "fuel", "parking", "rest_area", "custom")) }
    val visiblePoints = mapPoints.filter { it.type in enabledTypes }
    MaterialTheme(colorScheme = DarkColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                Box(modifier = Modifier.weight(1f)) {
                    MapScreen(location = location, route = route, mapPoints = visiblePoints, recenterRequest = recenterRequest, hasMapTilerKey = hasMapTilerKey, onLongPress = onLongPressMap)
                    SearchBar(modifier = Modifier.align(Alignment.TopCenter), results = searchResults, searching = searching, onSearch = onSearch, onSelectPlace = onSelectPlace)
                    ApiStatus(modifier = Modifier.align(Alignment.TopCenter).padding(top = 78.dp), status = apiStatus)
                    Button(onClick = { showLayers = !showLayers }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 16.dp)) { Text("Capas") }
                    Button(onClick = { showSimulation = !showSimulation }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 94.dp)) { Text("Prueba") }
                    if (showLayers) {
                        LayerPanel(modifier = Modifier.align(Alignment.TopEnd).padding(top = 132.dp, end = 16.dp), enabledTypes = enabledTypes, onToggle = { type -> enabledTypes = if (type in enabledTypes) enabledTypes - type else enabledTypes + type })
                    }
                    if (showSimulation) SimulationPanel(modifier = Modifier.align(Alignment.TopEnd).padding(top = 132.dp, end = 94.dp), routeAvailable = route != null, running = simulationRunning, speed = simulationSpeed, onSpeed = onSetSimulationSpeed, onStart = onStartSimulation, onPause = onPauseSimulation, onStop = onStopSimulation, onDetour = onSimulateDetour, onRandomDestination = onRandomDestination)
                    route?.let { NavigationSummary(modifier = Modifier.align(Alignment.TopStart).padding(top = 128.dp), distanceMeters = it.distanceMeters, durationSeconds = it.durationSeconds, nextInstruction = it.instructions.firstOrNull()?.text) }
                    route?.let { NavigationEngine.update(it, location)?.let { state -> NavigationStateBanner(modifier = Modifier.align(Alignment.TopStart).padding(top = 182.dp), state.offRoute) } }
                    pointAlert?.let { PointAlertBanner(modifier = Modifier.align(Alignment.TopCenter).padding(top = 232.dp), text = it) }
                    route?.let { Button(onClick = onFinishNavigation, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp)) { Text("Finalizar") } }
                    LocationButton(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        onRequestLocation = onCenterLocation,
                    )
                    Button(onClick = onRequestDemoRoute, modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) { Text("Ruta demo") }
                    Button(onClick = onToggleVoice, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 92.dp, bottom = 28.dp)) { Text(if (voiceEnabled) "Voz: ON" else "Voz: OFF") }
                    Button(onClick = onSaveCurrentPoint, modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 84.dp)) { Text("Guardar punto") }
                    if (!hasMapTilerKey) MissingKeyMessage(modifier = Modifier.align(Alignment.Center))
                    contextPoint?.let { ContextPointPanel(modifier = Modifier.align(Alignment.Center), onGoTo = { onGoToContext(it) }, onSave = { onSaveContext(it) }, onDismiss = onDismissContext) }
                }
                BottomNavigation()
            }
        }
    }
}

@Composable
private fun LayerPanel(modifier: Modifier, enabledTypes: Set<String>, onToggle: (String) -> Unit) = Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shape = RoundedCornerShape(14.dp)) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("camera" to "Cámaras", "danger" to "Peligros", "favorite" to "Favoritos", "fuel" to "Gasolineras", "parking" to "Parkings", "custom" to "Personalizados").forEach { (type, label) ->
            Button(onClick = { onToggle(type) }, modifier = Modifier.fillMaxWidth()) { Text(if (type in enabledTypes) "✓ $label" else "  $label") }
        }
    }
}

@Composable
private fun SimulationPanel(modifier: Modifier, routeAvailable: Boolean, running: Boolean, speed: Float, onSpeed: (Float) -> Unit, onStart: () -> Unit, onPause: () -> Unit, onStop: () -> Unit, onDetour: () -> Unit, onRandomDestination: () -> Unit) = Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
    shape = RoundedCornerShape(14.dp),
) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Simulación local", color = Color.White)
        Text(if (routeAvailable) "Velocidad: ${speed.toInt()} km/h" else "Calcula una ruta primero", color = Color(0xFFBBC7D3))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(20f, 40f, 80f, 110f, 130f).forEach { value ->
                Button(onClick = { onSpeed(value) }, enabled = routeAvailable) { Text("${value.toInt()}") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onStart, enabled = routeAvailable && !running) { Text("Iniciar") }
            Button(onClick = onPause, enabled = running) { Text("Pausa") }
            Button(onClick = onStop, enabled = running) { Text("Detener") }
        }
        Button(onClick = onDetour, enabled = running && routeAvailable, modifier = Modifier.fillMaxWidth()) { Text("Tomar desvío") }
        Button(onClick = onRandomDestination, enabled = routeAvailable, modifier = Modifier.fillMaxWidth()) { Text("Destino aleatorio ≤25 km") }
    }
}

@Composable
private fun PointAlertBanner(modifier: Modifier, text: String) = Surface(modifier = modifier.padding(horizontal = 16.dp), color = Color(0xFF6D3D16), shape = RoundedCornerShape(12.dp)) {
    Text("⚠ $text", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = Color.White)
}

@Composable
private fun NavigationStateBanner(modifier: Modifier, offRoute: Boolean) {
    if (offRoute) {
        Surface(modifier = modifier.padding(horizontal = 16.dp), color = Color(0xFF7D2D2D), shape = RoundedCornerShape(12.dp)) {
            Text("Fuera de ruta · preparando recálculo", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White)
        }
    }
}

@Composable
private fun ApiStatus(modifier: Modifier, status: String) = Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    shape = RoundedCornerShape(12.dp),
) {
    Text(status, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color(0xFFBBC7D3))
}

@Composable
private fun NavigationSummary(modifier: Modifier, distanceMeters: Double, durationSeconds: Int, nextInstruction: String?) = Surface(
    modifier = modifier.padding(horizontal = 16.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    shape = RoundedCornerShape(14.dp),
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Text(nextInstruction ?: "Ruta activa", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(formatDistance(distanceMeters), color = Color.White)
                Text(formatDuration(durationSeconds), color = Color(0xFFBBC7D3))
            }
        }
    }
}

private fun formatDistance(meters: Double): String = if (meters >= 1000) {
    "%.1f km".format(meters / 1000)
} else {
    "%.0f m".format(meters)
}

private fun formatDuration(seconds: Int): String {
    val minutes = (seconds / 60).coerceAtLeast(1)
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

@Composable
private fun SearchBar(modifier: Modifier, results: List<SearchPlace>, searching: Boolean, onSearch: (String) -> Unit, onSelectPlace: (SearchPlace) -> Unit) {
    var query by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            delay(350)
            onSearch(query)
        }
    }
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Buscar lugar o dirección") })
            Button(onClick = { onSearch(query) }, enabled = !searching && query.trim().length >= 2) { Text(if (searching) "…" else "Buscar") }
        }
        if (results.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), shadowElevation = 8.dp) {
                Column {
                    results.forEach { place ->
                        Text(place.address, modifier = Modifier.fillMaxWidth().padding(14.dp).clickable { onSelectPlace(place) }, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextPointPanel(modifier: Modifier, onGoTo: () -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) = Surface(modifier = modifier.padding(24.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), shape = RoundedCornerShape(18.dp), shadowElevation = 12.dp) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Punto seleccionado", color = Color.White)
        Button(onClick = onGoTo, modifier = Modifier.fillMaxWidth()) { Text("Ir aquí") }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Guardar punto") }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}

@Composable
private fun LocationButton(modifier: Modifier, onRequestLocation: () -> Unit) = Button(
    modifier = modifier.padding(20.dp).size(58.dp),
    onClick = onRequestLocation,
    contentPadding = PaddingValues(0.dp),
    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
) { Text("◎", color = MaterialTheme.colorScheme.primary) }

@Composable
private fun MissingKeyMessage(modifier: Modifier) = Surface(
    modifier = modifier.padding(28.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(16.dp),
) {
    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Configura MapTiler para mostrar el mapa")
        Text("Copia maptiler.properties.example a maptiler.properties", color = Color(0xFFBBC7D3))
    }
}

@Composable
private fun BottomNavigation() = NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
    listOf("Mapa", "Navegar", "Lugares", "Más").forEachIndexed { index, label ->
        NavigationBarItem(selected = index == 0, onClick = {}, icon = {}, label = { Text(label) })
    }
}
