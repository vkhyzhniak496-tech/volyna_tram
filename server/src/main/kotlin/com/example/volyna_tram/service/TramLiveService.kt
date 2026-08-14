package com.example.volyna_tram.service

import com.example.volyna_tram.client.WawApiClient
import com.example.volyna_tram.model.LiveTram
import com.example.volyna_tram.repository.TramRepository
import com.example.volyna_tram.utils.GeoJsonFormatter
import kotlinx.coroutines.*

class TramLiveService(
    private val apiClient: WawApiClient = WawApiClient(),
    private val repository: TramRepository = TramRepository()
) {

    fun getTramsAsGeoJson(): String {
        return GeoJsonFormatter.formatTramsToGeoJson(repository.getAll())
    }

    fun getTramById(id: String): LiveTram? = repository.getById(id)

    fun getTramsByLine(line: String): List<LiveTram> = repository.getByLine(line)

    fun getAllTrams(): List<LiveTram> = repository.getAll()

    fun startLiveTracking(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            println("[SILNIK] Uruchomiono zrefaktoryzowany serwis śledzenia taboru.")

            while (isActive) {
                try {
                    val fetchedTrams = apiClient.fetchLiveTrams()
                    fetchedTrams.forEach { repository.upsertTram(it) }

                    if (fetchedTrams.isNotEmpty()) {
                        println("[SILNIK] Zaktualizowano w RAM: ${fetchedTrams.size} pojazdów.")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("[SILNIK] Błąd w pętli synchronizacji: ${e.message}")
                }

                delay(10000)
            }
        }
    }
}