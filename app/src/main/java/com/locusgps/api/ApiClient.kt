package com.locusgps.api

import com.locusgps.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class RoutePoint(val latitude: Double, val longitude: Double)
data class RouteResult(
    val distanceMeters: Double,
    val durationSeconds: Int,
    val geometry: List<RoutePoint>,
)

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
