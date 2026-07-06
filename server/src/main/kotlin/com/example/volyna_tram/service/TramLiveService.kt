package com.example.volyna_tram.service

import com.example.volyna_tram.model.LiveTram
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class WarsawResponse(
    val result: List<WarsawVehicleObject>
)

@Serializable
data class WarsawVehicleObject(
    val values: List<WarsawKeyValuePair>
)

@Serializable
data class WarsawKeyValuePair(
    val value: String,
    val key: String
)
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
    }

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

    // 🚀 PRAWDZIWY SILNIK SIECIOWY W TLE
    fun startLiveTracking(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            println("[SILNIK] Prawdziwy nasłuch Warszawy uruchomiony w tle.")

            while (isActive) {
                try {
                    println("[SILNIK] Pobieranie świeżych danych z api.um.warszawa.pl...")

                    // 1. Sklejamy URL dokładnie tak, jak w dokumentacji (bez slasha na końcu przed pytajnikiem!)
                    val fullUrl = "https://api.um.warszawa.pl/api/action/busestrams_get" +
                            "?resource_id=$resourceId" +
                            "&apikey=$apiKey" +
                            "&type=2"

                    // 2. Strzelamy czystym POST-em na ten adres, body zostaje puste
                    val response: HttpResponse = httpClient.post(fullUrl) {
                        headers {
                            append(HttpHeaders.CacheControl, "no-cache")
                        }
                    }

                    if (response.status == HttpStatusCode.OK) {
                        val rawJson = response.bodyAsText()
                        println("[SILNIK] Odebrano dane z miasta! Rozmiar paczki: ${rawJson.length} znaków.")

                        // Tutaj wkrótce wrzucimy podgląd tego, co faktycznie przyleciało
                        if (rawJson.length < 200) {
                            println("[SILNIK] Treść małej paczki: $rawJson")
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

                delay(10000)
            }
        }
    }
}