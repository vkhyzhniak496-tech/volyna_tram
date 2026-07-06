package com.example.volyna_tram.service

import com.example.volyna_tram.model.LiveTram
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class TramLiveService {

    private val activeTrams = ConcurrentHashMap<String, LiveTram>()

    init {
        // Nasz tabor startowy
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

    // 🚀 SILNIK DZIAŁAJĄCY W TLE SERWERA
    fun startSimulation(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            println("[SILNIK] Uruchomiono asynchroniczny proces tła na serwerze.")

            while (isActive) {
                // Małe przesunięcie dla testu (symulujemy, że tramwaje jadą)
                activeTrams.forEach { (key, tram) ->
                    // Losowo zmieniamy pozycję o mikro-wartość (około kilku metrów)
                    val newLat = tram.lat + (Random.nextDouble() - 0.5) * 0.0005
                    val newLon = tram.lon + (Random.nextDouble() - 0.5) * 0.0005

                    // Nadpisujemy obiekt w pamięci RAM
                    updateTram(LiveTram(tram.line, tram.brigade, newLat, newLon))
                }

                // 📝 LOGI NA BACKENDZIE - to zobaczysz w terminalu Acera
                println("[SILNIK] Pozycje zaktualizowane w pamięci RAM. Aktualny stan linii 17: lat=${String.format("%.5f", activeTrams["17_03"]?.lat)}, lon=${String.format("%.5f", activeTrams["17_03"]?.lon)}")

                // Odczekaj 2 sekundy przed kolejną aktualizacją
                delay(2000)
            }
        }
    }
}