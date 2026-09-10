package com.locusgps.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.locusgps.location.UserLocation
import com.locusgps.api.RouteResult
import com.locusgps.map.MapScreen

private val DarkColors = darkColorScheme(
    primary = Color(0xFF67D4FF),
    surface = Color(0xFF101419),
    surfaceVariant = Color(0xFF1A222B),
)

@Composable
fun LocusGpsApp(location: UserLocation?, route: RouteResult?, hasMapTilerKey: Boolean, apiStatus: String, onRequestLocation: () -> Unit, onRequestDemoRoute: () -> Unit) {
    MaterialTheme(colorScheme = DarkColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                Box(modifier = Modifier.weight(1f)) {
                    MapScreen(location = location, route = route, hasMapTilerKey = hasMapTilerKey)
                    SearchBarPlaceholder(modifier = Modifier.align(Alignment.TopCenter))
                    ApiStatus(modifier = Modifier.align(Alignment.TopCenter).padding(top = 78.dp), status = apiStatus)
                    LocationButton(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        onRequestLocation = onRequestLocation,
                    )
                    Button(onClick = onRequestDemoRoute, modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) { Text("Ruta demo") }
                    if (!hasMapTilerKey) MissingKeyMessage(modifier = Modifier.align(Alignment.Center))
                }
                BottomNavigation()
            }
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
private fun SearchBarPlaceholder(modifier: Modifier = Modifier) = Surface(
    modifier = modifier.fillMaxWidth().padding(16.dp),
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(18.dp),
    shadowElevation = 8.dp,
) {
    Text("Buscar lugar o dirección", modifier = Modifier.padding(18.dp), color = Color(0xFFE7EDF4))
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
