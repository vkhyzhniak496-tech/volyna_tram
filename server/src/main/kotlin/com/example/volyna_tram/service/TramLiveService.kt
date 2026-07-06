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

    // 🚀 PRAWDZIWY SILNIK SIECIOWY W TLE (RUNDA 2)
    fun startLiveTracking(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { // Przełączamy się na wątki dedykowane operacjom sieciowym (I/O)
            println("[SILNIK] Prawdziwy nasłuch Warszawy uruchomiony w tle.")

            while (isActive) {
                try {
                    println("[SILNIK] Pobieranie świeżych danych z api.um.warszawa.pl...")

                    // 🛜 Wymuszony POST z parametrami w URL oraz czyszczeniem cache (zgodnie z instrukcją curl)
                    val response: HttpResponse = httpClient.post("https://api.um.warszawa.pl/api/action/busestrams_get/") {
                        parameter("apikey", apiKey)
                        parameter("type", "2")
                        parameter("resource_id", resourceId)
                        headers {
                            append(HttpHeaders.CacheControl, "no-cache")
                        }

                        // 2. Pakujemy parametry jako FORMULARZ (w body), a nie w adres URL
                        setBody(FormDataContent(Parameters.build {
                            append("apikey", apiKey)
                            append("type", "2")
                            append("resource_id", resourceId)
                        }))
                    }

                    if (response.status == HttpStatusCode.OK) {
                        val rawJson = response.bodyAsText()

                        // Logujemy na razie sam rozmiar paczki, żeby sprawdzić stabilność łącza
                        println("[SILNIK] Odebrano dane z miasta! Rozmiar paczki: ${rawJson.length} znaków.")

                        // TODO: W następnym kroku wjedzie tu parser, który rozbije strukturę i wywoła updateTram()
                    } else {
                        println("[SILNIK] Miasto odpowiedziało błędem HTTP: ${response.status}")
                    }

                } catch (e: CancellationException) {
                    println("[SILNIK] Zatrzymano zadanie tła.")
                    throw e
                } catch (e: Exception) {
                    // Blokada wywrotki – jeśli miasto rzuci błędem połączenia, serwer żyje dalej
                    println("[SILNIK] Błąd sieciowy/połączenia: ${e.message}")
                }

                // Urzędnicy potwierdzili odświeżanie co 10 sekund, więc lecimy gęstym strumieniem
                delay(10000)
            }
        }
    }
}