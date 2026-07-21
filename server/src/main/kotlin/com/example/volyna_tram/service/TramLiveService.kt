package com.example.volyna_tram.service

import com.example.volyna_tram.model.LiveTram
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.*
import kotlin.math.*

class TramLiveService {

    private val activeTrams = ConcurrentHashMap<String, LiveTram>()

    private val resourceId = "f2e5503e-927d-4ad3-9500-4ab9e55deb59"
    private val apiKey = "6e4bb8bb-34f8-4e05-b232-351d3d9febb6"

    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 5000
            connectTimeoutMillis = 5000
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            exponentialDelay()
            retryIf { _, response ->
                response.status.value == 504
            }
        }
    }

    init {
        // Tabor startowy na zachętę
        updateTram(LiveTram("17", "03", 52.219, 21.001, speed = 25.0))
        updateTram(LiveTram("9", "12", 52.231, 21.005, speed = 0.0))
        updateTram(LiveTram("19", "01", 52.225, 21.003, speed = 42.0))
    }

    fun updateTram(tram: LiveTram) {
        val key = "${tram.line}_${tram.brigade}"
        val oldTram = activeTrams[key]

        val calculatedSpeed = if (oldTram != null) {
            calculateSpeedKmH(
                lat1 = oldTram.lat, lon1 = oldTram.lon, t1Ms = oldTram.timestamp,
                lat2 = tram.lat, lon2 = tram.lon, t2Ms = tram.timestamp
            )
        } else {
            0.0
        }

        activeTrams[key] = tram.copy(speed = calculatedSpeed)
    }

    fun getAllTrams(): List<LiveTram> = activeTrams.values.toList()

    // 🚀 BEZPIECZNE GENEROWANIE GEOJSON Z POPRAWNYM FORMATOWANIEM I PRĘDKOŚCIĄ
    fun getTramsAsGeoJson(): String {
        val trams = getAllTrams()

        return buildString {
            append("""{"type":"FeatureCollection","features":[""")

            trams.forEachIndexed { index, tram ->
                append("""{""")
                append("""  "type":"Feature",""")
                append("""  "geometry":{""")
                append("""    "type":"Point",""")
                append("""    "coordinates":[${tram.lon},${tram.lat}]""")
                append("""  },""")
                append("""  "properties":{""")
                append("""    "line":"${tram.line}",""")
                append("""    "brigade":"${tram.brigade}",""") // FIXED: Poprawiony przecinek!
                append("""    "speed":${tram.speed},""")         // NOWOŚĆ: Prędkość z serwera
                append("""    "timestamp":${tram.timestamp}""")
                append("""  }""")
                append("""}""")

                if (index < trams.lastIndex) {
                    append(",")
                }
            }

            append("]}")
        }
    }

    // 🧮 WZÓR HAVERSINE DO WYLICZANIA FIZYCZNEJ PRĘDKOŚCI PRZEBYCIA (km/h)
    private fun calculateSpeedKmH(
        lat1: Double, lon1: Double, t1Ms: Long,
        lat2: Double, lon2: Double, t2Ms: Long
    ): Double {
        val timeDiffSeconds = (t2Ms - t1Ms) / 1000.0
        if (timeDiffSeconds <= 0.5) return 0.0 // Ignorujemy zbyt częste lub błędne próbki

        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distanceMeters = earthRadiusMeters * c

        val speedMs = distanceMeters / timeDiffSeconds
        val speedKmH = speedMs * 3.6

        // Obcinamy nienaturalne skoki GPS (np. teleportacja > 90 km/h)
        return if (speedKmH in 0.0..90.0) {
            (speedKmH * 10.0).roundToInt() / 10.0 // Zaokrąglenie do 1 miejsca po przecinku
        } else {
            0.0
        }
    }

    fun startLiveTracking(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            println("[SILNIK] Uruchomiono pobieranie taboru z UM Warszawa.")

            while (isActive) {
                try {
                    val fullUrl = "https://api.um.warszawa.pl/api/action/busestrams_get/" +
                            "?resource_id=$resourceId" +
                            "&apikey=$apiKey" +
                            "&type=2"

                    val response: HttpResponse = httpClient.post(fullUrl) {
                        headers {
                            append(HttpHeaders.CacheControl, "no-cache")
                        }
                    }

                    if (response.status == HttpStatusCode.OK) {
                        val rawJson = response.bodyAsText()
                        val jsonConfig = Json { ignoreUnknownKeys = true }
                        val baseElement = jsonConfig.parseToJsonElement(rawJson) as? JsonObject
                        val resultElement = baseElement?.get("result")

                        if (resultElement is JsonArray) {
                            var updatedCount = 0
                            val now = System.currentTimeMillis()

                            resultElement.forEach { vehicleElement ->
                                val vehicleObject = vehicleElement as? JsonObject
                                if (vehicleObject != null) {
                                    val line = vehicleObject["Lines"]?.jsonPrimitive?.content
                                    val brigade = vehicleObject["Brigade"]?.jsonPrimitive?.content
                                    val latStr = vehicleObject["Lat"]?.jsonPrimitive?.content
                                    val lonStr = vehicleObject["Lon"]?.jsonPrimitive?.content

                                    if (line != null && brigade != null && latStr != null && lonStr != null) {
                                        val lat = latStr.toDoubleOrNull() ?: 0.0
                                        val lon = lonStr.toDoubleOrNull() ?: 0.0

                                        val tram = LiveTram(
                                            line = line.trim(),
                                            brigade = brigade.trim(),
                                            lat = lat,
                                            lon = lon,
                                            timestamp = now
                                        )
                                        updateTram(tram)
                                        updatedCount++
                                    }
                                }
                            }
                            println("[SILNIK] Zaktualizowano w RAM: $updatedCount pojazdów z wyliczoną prędkością.")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("[SILNIK] Błąd pobierania danych: ${e.message}")
                }

                delay(10000)
            }
        }
    }
}