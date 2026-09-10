package com.locusgps.api

import com.locusgps.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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
