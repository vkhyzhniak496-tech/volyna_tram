package com.example.volyna_tram.client

import com.example.volyna_tram.model.LiveTram
import com.example.volyna_tram.utils.AppConfig
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class WawApiClient {

    private val resourceId = "f2e5503e-927d-4ad3-9500-4ab9e55deb59"
    private val apiKey = AppConfig.apiKey
    private val jsonConfig = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 5000
            connectTimeoutMillis = 5000
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            exponentialDelay()
            retryIf { _, response -> response.status.value == 504 }
        }
    }

    suspend fun fetchLiveTrams(): List<LiveTram> {
        val url = "https://api.um.warszawa.pl/api/action/busestrams_get/" +
                "?resource_id=$resourceId" +
                "&apikey=$apiKey" +
                "&type=2"

        val response: HttpResponse = httpClient.post(url) {
            headers { append(HttpHeaders.CacheControl, "no-cache") }
        }

        if (response.status != HttpStatusCode.OK) return emptyList()

        val rawJson = response.bodyAsText()
        val baseElement = jsonConfig.parseToJsonElement(rawJson) as? JsonObject
        val resultElement = baseElement?.get("result") as? JsonArray ?: return emptyList()

        val now = System.currentTimeMillis()
        val parsedTrams = mutableListOf<LiveTram>()

        for (vehicleElement in resultElement) {
            val vehicleObject = vehicleElement as? JsonObject ?: continue
            val line = vehicleObject["Lines"]?.jsonPrimitive?.content?.trim()
            val brigade = vehicleObject["Brigade"]?.jsonPrimitive?.content?.trim()
            val lat = vehicleObject["Lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val lon = vehicleObject["Lon"]?.jsonPrimitive?.content?.toDoubleOrNull()

            if (!line.isNullOrEmpty() && !brigade.isNullOrEmpty() && lat != null && lon != null) {
                parsedTrams.add(
                    LiveTram(
                        line = line,
                        brigade = brigade,
                        lat = lat,
                        lon = lon,
                        timestamp = now
                    )
                )
            }
        }

        return parsedTrams
    }
}