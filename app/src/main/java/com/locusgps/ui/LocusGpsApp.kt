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
import com.locusgps.map.MapScreen
import com.locusgps.navigation.NavigationEngine

private val DarkColors = darkColorScheme(
    primary = Color(0xFF67D4FF),
    surface = Color(0xFF101419),
    surfaceVariant = Color(0xFF1A222B),
)

@Composable
fun LocusGpsApp(location: UserLocation?, route: RouteResult?, mapPoints: List<MapPoint>, searchResults: List<SearchPlace>, searching: Boolean, voiceEnabled: Boolean, hasMapTilerKey: Boolean, apiStatus: String, onRequestLocation: () -> Unit, onRequestDemoRoute: () -> Unit, onSearch: (String) -> Unit, onSelectPlace: (SearchPlace) -> Unit, onToggleVoice: () -> Unit, onSaveCurrentPoint: () -> Unit) {
    MaterialTheme(colorScheme = DarkColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                Box(modifier = Modifier.weight(1f)) {
                    MapScreen(location = location, route = route, mapPoints = mapPoints, hasMapTilerKey = hasMapTilerKey)
                    SearchBar(modifier = Modifier.align(Alignment.TopCenter), results = searchResults, searching = searching, onSearch = onSearch, onSelectPlace = onSelectPlace)
                    ApiStatus(modifier = Modifier.align(Alignment.TopCenter).padding(top = 78.dp), status = apiStatus)
                    route?.let { NavigationSummary(modifier = Modifier.align(Alignment.TopStart).padding(top = 128.dp), distanceMeters = it.distanceMeters, durationSeconds = it.durationSeconds) }
                    route?.let { NavigationEngine.update(it, location)?.let { state -> NavigationStateBanner(modifier = Modifier.align(Alignment.TopStart).padding(top = 182.dp), state.offRoute) } }
                    LocationButton(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        onRequestLocation = onRequestLocation,
                    )
                    Button(onClick = onRequestDemoRoute, modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) { Text("Ruta demo") }
                    Button(onClick = onToggleVoice, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 92.dp, bottom = 28.dp)) { Text(if (voiceEnabled) "Voz: ON" else "Voz: OFF") }
                    Button(onClick = onSaveCurrentPoint, modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 84.dp)) { Text("Guardar punto") }
                    if (!hasMapTilerKey) MissingKeyMessage(modifier = Modifier.align(Alignment.Center))
                }
                BottomNavigation()
            }
        }
    }
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
private fun NavigationSummary(modifier: Modifier, distanceMeters: Double, durationSeconds: Int) = Surface(
    modifier = modifier.padding(horizontal = 16.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    shape = RoundedCornerShape(14.dp),
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(formatDistance(distanceMeters), color = Color.White)
        Text(formatDuration(durationSeconds), color = Color(0xFFBBC7D3))
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
