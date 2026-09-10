package com.locusgps.api

import com.locusgps.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class RoutePoint(val latitude: Double, val longitude: Double)
data class RouteInstruction(val text: String, val distanceMeters: Double, val intervalStart: Int)
data class RouteResult(
    val distanceMeters: Double,
    val durationSeconds: Int,
    val geometry: List<RoutePoint>,
    val instructions: List<RouteInstruction> = emptyList(),
)
data class SearchPlace(val id: String, val name: String, val address: String, val latitude: Double, val longitude: Double)
data class MapPoint(val id: Long, val type: String, val name: String, val latitude: Double, val longitude: Double, val alertEnabled: Boolean)

/** Lightweight API boundary. GPS remains local unless a future use explicitly calls an endpoint. */
class ApiClient(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val token: String = BuildConfig.API_TOKEN,
) {
    suspend fun health(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("$baseUrl/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                useCaches = false
            }
            try {
                val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                    .bufferedReader()
                    .use { it.readText() }
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: $body")
                body
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun favorites(): Result<String> = authenticatedGet("/api/favorites")

    suspend fun search(query: String, location: RoutePoint? = null): Result<List<SearchPlace>> = withContext(Dispatchers.IO) {
        runCatching {
            require(token.isNotBlank()) { "API_TOKEN no configurado" }
            val encoded = java.net.URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
            val suffix = if (location != null) "&lat=${location.latitude}&lon=${location.longitude}" else ""
            val connection = (URL("$baseUrl/api/search?q=$encoded$suffix").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                    .bufferedReader().use { it.readText() }
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: $body")
                val results = JSONObject(body).getJSONArray("results")
                List(results.length()) { index ->
                    val item = results.getJSONObject(index)
                    SearchPlace(item.getString("id"), item.getString("name"), item.getString("address"), item.getDouble("latitude"), item.getDouble("longitude"))
                }
            } finally { connection.disconnect() }
        }
    }

    suspend fun mapPoints(location: RoutePoint, radiusMeters: Int = 1000): Result<List<MapPoint>> = withContext(Dispatchers.IO) {
        runCatching {
            require(token.isNotBlank()) { "API_TOKEN no configurado" }
            val url = URL("$baseUrl/api/map-points?lat=${location.latitude}&lon=${location.longitude}&radius=$radiusMeters")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: $body")
                val trimmed = body.trim()
                val items = if (trimmed.startsWith("[")) org.json.JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("results")
                List(items.length()) { index ->
                    val item = items.getJSONObject(index)
                    MapPoint(item.getLong("id"), item.getString("type"), item.getString("name"), item.getDouble("latitude"), item.getDouble("longitude"), item.optBoolean("alertEnabled", true))
                }
            } finally { connection.disconnect() }
        }
    }

    suspend fun cameraLocations(location: RoutePoint, radiusMeters: Int = 30_000): Result<List<MapPoint>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("$baseUrl/api/camera-locations?lat=${location.latitude}&lon=${location.longitude}&radius=$radiusMeters")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
            }
            try {
                val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: $body")
                val items = JSONObject(body).getJSONArray("results")
                List(items.length()) { index ->
                    val item = items.getJSONObject(index)
                    MapPoint(item.getLong("id"), item.getString("type"), item.getString("name"), item.getDouble("latitude"), item.getDouble("longitude"), item.optBoolean("alertEnabled", true))
                }
            } finally { connection.disconnect() }
        }
    }

    suspend fun createMapPoint(point: RoutePoint, name: String, type: String = "custom"): Result<MapPoint> = withContext(Dispatchers.IO) {
        runCatching {
            require(token.isNotBlank()) { "API_TOKEN no configurado" }
            val connection = (URL("$baseUrl/api/map-points").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                val request = JSONObject().apply {
                    put("type", type)
                    put("name", name.trim())
                    put("latitude", point.latitude)
                    put("longitude", point.longitude)
                    put("alert_enabled", true)
                    put("enabled", true)
                }
                connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: $body")
                val item = JSONObject(body)
                MapPoint(item.getLong("id"), item.getString("type"), item.getString("name"), item.getDouble("latitude"), item.getDouble("longitude"), item.optBoolean("alertEnabled", true))
            } finally { connection.disconnect() }
        }
    }

    suspend fun route(origin: RoutePoint, destination: RoutePoint, mode: String = "driving"): Result<RouteResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(token.isNotBlank()) { "API_TOKEN no configurado" }
            val connection = (URL("$baseUrl/api/routes").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                val request = JSONObject().apply {
                    put("origin", JSONObject().put("latitude", origin.latitude).put("longitude", origin.longitude))
                    put("destination", JSONObject().put("latitude", destination.latitude).put("longitude", destination.longitude))
                    put("mode", mode)
                }
                connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                    .bufferedReader().use { it.readText() }
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: $body")
                val json = JSONObject(body)
                val coordinates = json.getJSONArray("geometry")
                RouteResult(
                    distanceMeters = json.getDouble("distanceMeters"),
                    durationSeconds = json.getInt("durationSeconds"),
                    geometry = List(coordinates.length()) { index ->
                        val point = coordinates.getJSONObject(index)
                        RoutePoint(point.getDouble("latitude"), point.getDouble("longitude"))
                    },
                    instructions = json.optJSONArray("instructions")?.let { instructions ->
                        List(instructions.length()) { index ->
                            val item = instructions.getJSONObject(index)
                            RouteInstruction(item.getString("text"), item.optDouble("distanceMeters", 0.0), item.optInt("intervalStart", 0))
                        }
                    } ?: emptyList(),
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun authenticatedGet(path: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(token.isNotBlank()) { "API_TOKEN no configurado" }
            val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                    .bufferedReader().use { it.readText() }
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: $body")
                body
            } finally {
                connection.disconnect()
            }
        }
    }
}
