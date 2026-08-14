package com.example.volyna_tram.client

import com.example.volyna_tram.model.LiveTram
import com.example.volyna_tram.utils.AppConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class WawApiClient {

    private val resourceId = "f2e5503e-927d-4ad3-9500-4ab9e55deb59"
    private val apiKey = AppConfig.apiKey
    private val jsonConfig = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 8000
            connectTimeoutMillis = 5000
        }
        install(HttpRequestRetry) {
            maxRetries = 2
            exponentialDelay()
            retryIf { _, response -> response.status.value == 504 }
        }
    }

    suspend fun fetchLiveTrams(): List<LiveTram> {
        val url = "https://api.um.warszawa.pl/api/action/busestrams_get/" +
                "?resource_id=$resourceId" +
                "&apikey=$apiKey" +
                "&type=2"

        return try {
            // ZTM Warszawa wymaga zapytania GET
            val response: HttpResponse = httpClient.get(url) {
                headers { append(HttpHeaders.CacheControl, "no-cache") }
            }

            if (response.status != HttpStatusCode.OK) {
                println("[ZTM API] Błąd HTTP: ${response.status}")
                return emptyList()
            }

            val rawJson = response.bodyAsText()
            val baseElement = jsonConfig.parseToJsonElement(rawJson) as? JsonObject
            val resultField = baseElement?.get("result")

            // Sprawdzenie czy ZTM nie zwrócił błędu tekstowego (np. o kluczu API)
            if (resultField is JsonPrimitive) {
                println("[ZTM API] Komunikat z bramki ZTM: ${resultField.content}")
                return emptyList()
            }

            val resultElement = resultField as? JsonArray ?: run {
                println("[ZTM API] Pole 'result' nie jest tablicą JSON.")
                return emptyList()
            }

            val now = System.currentTimeMillis()
            val parsedTrams = mutableListOf<LiveTram>()

            for (vehicleElement in resultElement) {
                val vehicleObject = vehicleElement as? JsonObject ?: continue
                val line = vehicleObject["Lines"]?.jsonPrimitive?.contentOrNull?.trim()
                val brigade = vehicleObject["Brigade"]?.jsonPrimitive?.contentOrNull?.trim()
                val lat = vehicleObject["Lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val lon = vehicleObject["Lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

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

            parsedTrams
        } catch (e: Exception) {
            println("[ZTM API] Wyjątek podczas pobierania pozycji: ${e.message}")
            emptyList()
        }
    }
}