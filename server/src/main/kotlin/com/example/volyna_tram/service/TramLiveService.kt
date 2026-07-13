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

class TramLiveService {

    private val activeTrams = ConcurrentHashMap<String, LiveTram>()

    // Oficjalny identyfikator zasobu z akademickiej dokumentacji
    private val resourceId = "f2e5503e-927d-4ad3-9500-4ab9e55deb59"

    // 🔑 MIEJSCE NA TWÓJ KLUCZ Z api.um.warszawa.pl
    private val apiKey = "6e4bb8bb-34f8-4e05-b232-351d3d9febb6"

    // Klient HTTP dedykowany do strzałów na zewnątrz serwera (z timeoutem na leniwe API)
    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 5000
            connectTimeoutMillis = 5000
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            exponentialDelay() // Uruchamia Exponential Backoff
            retryIf { request, response ->
                response.status.value == 504
            }
        }

        // Walidator odpowiedzi do zarządzania UI i logami
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, request ->
                val clientException =
                    exception as? ResponseException ?: return@handleResponseExceptionWithRequest
                val exceptionResponse = clientException.response

                if (exceptionResponse.status == HttpStatusCode.GatewayTimeout) {

                    // 1. Zalogowanie zdarzenia do naszego trzeciego filaru monitoringu (Tracing)
                    // W tym miejscu wpinamy nasz system logowania
                    println("TRACING: Wykryto błąd 504 Gateway Timeout dla żądania: ${request.url}")

                    // 2. Przekazanie informacji do UI aplikacji
                    // Rzucamy własny wyjątek, który warstwa UI (np. ViewModel) złapie i wyświetli jako Toast/Snackbar
                    throw GISTimeoutException("Otwórz klapę")
                }
            }
        }
    }
    class GISTimeoutException(message: String) : Exception(message)

    init {
        // Nasz tabor startowy na wypadek, gdybyśmy chcieli mieć fallback w RAM-ie
        updateTram(LiveTram("17", "03", 52.219, 21.001))
        updateTram(LiveTram("9", "12", 52.231, 21.005))
        updateTram(LiveTram("19", "01", 52.225, 21.003))
    }

    fun updateTram(tram: LiveTram) {
        val key = "${tram.line}_${tram.brigade}"
        activeTrams[key] = tram
    }

    fun getAllTrams(): List<LiveTram> {
        return activeTrams.values.toList()
    }
    fun getTramsAsGeoJson(): String {
        val trams = getAllTrams()

        return buildString {
            append("""{"type":"FeatureCollection","features":[""")

            trams.forEachIndexed { index, tram ->
                append("""{""")
                append("""  "type":"Feature",""")
                append("""  "geometry":{""")
                append("""    "type":"Point",""")
                append("""    "coordinates":[${tram.lon},${tram.lat}]""") // Kolejność: LON, LAT!
                append("""  },""")
                append("""  "properties":{""")
                append("""    "line":"${tram.line}",""")
                append("""    "brigade":"${tram.brigade}"""")
                append("""  }""")
                append("""}""")

                if (index < trams.lastIndex) {
                    append(",")
                }
            }

            append("]}")
        }
    }
    // 🚀 PRAWDZIWY SILNIK SIECIOWY W TLE
    fun startLiveTracking(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            println("[SILNIK] Prawdziwy nasłuch Warszawy uruchomiony w tle.")

            while (isActive) {
                try {
                    println("[SILNIK] Pobieranie świeżych danych z api.um.warszawa.pl...")

                    // 1. Sklejamy URL ze slashem przed pytajnikiem
                    val fullUrl = "https://api.um.warszawa.pl/api/action/busestrams_get/" +
                            "?resource_id=$resourceId" +
                            "&apikey=$apiKey" +
                            "&type=2"

                    // 2. Strzelamy czystym POST-em na sklejony adres
                    val response: HttpResponse = httpClient.post(fullUrl) {
                        headers {
                            append(HttpHeaders.CacheControl, "no-cache")
                        }
                    }

                    if (response.status == HttpStatusCode.OK) {
                        val rawJson = response.bodyAsText()
                        println("[SILNIK] Odebrano dane z miasta! Rozmiar paczki: ${rawJson.length} znaków.")

                        try {
                            val jsonConfig = Json { ignoreUnknownKeys = true }

                            // 3. Parsujemy dynamicznie na JsonObject
                            val baseElement = jsonConfig.parseToJsonElement(rawJson) as? JsonObject
                            val resultElement = baseElement?.get("result")

                            // 4. Wyciągamy dane bezpośrednio z kluczy obiektów (format dla type=2)
                            if (resultElement is JsonArray) {
                                var updatedCount = 0

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
                                                lon = lon
                                            )
                                            updateTram(tram)
                                            updatedCount++
                                        }
                                    }
                                }
                                println("[SILNIK] Parser zakończył robotę. Zaktualizowano pojazdów w RAM: $updatedCount")
                            } else {
                                println("[SILNIK] Miasto przesłało komunikat zamiast danych. Treść: $resultElement")
                            }

                        } catch (parseException: Exception) {
                            println("[SILNIK] Parser wywalił się wewnętrznie: ${parseException.message}")
                        }
                    } else {
                        println("[SILNIK] Miasto odpowiedziało błędem HTTP: ${response.status}")
                    }

                } catch (e: CancellationException) {
                    println("[SILNIK] Zatrzymano zadanie tła.")
                    throw e
                } catch (e: Exception) {
                    println("[SILNIK] Błąd sieciowy/połączenia: ${e.message}")
                }

                // ⏱️ Zawsze czekamy 10 sekund przed kolejną pętlą
                delay(10000)
            }
        }
    }
}

