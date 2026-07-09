package com.example.volyna_tram.presentation

import com.example.volyna_tram.domain.model.Tram
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object TramStore {
    // Zmiana z List na Map zapewnia ultra-szybki czas dostępu O(1)
    private val _taborMap = MutableStateFlow<Map<String, Tram>>(emptyMap())
    val taborMap: StateFlow<Map<String, Tram>> = _taborMap.asStateFlow()

    var networkFetchAction: (() -> Unit)? = null

    /**
     * Nadpisuje całą mapę pojazdów nowymi danymi z API.
     */
    fun updateTaborList(noweTramwaje: List<Tram>) {
        _taborMap.value = noweTramwaje.associateBy { it.id }
    }

    /**
     * Błyskawiczna aktualizacja pozycji lub dodanie pojedynczego wozu bez przeszukiwania tablic!
     */
    fun updateSingleTram(tram: Tram) {
        _taborMap.update { currentMap ->
            currentMap + (tram.id to tram)
        }
    }

    /**
     * Zmiana stanu wozu (np. opóźnienie) prosto po unikalnym kluczu.
     */
    fun updateTramState(id: String, newState: String) {
        _taborMap.update { currentMap ->
            val tram = currentMap[id]
            if (tram != null) {
                currentMap + (id to tram.copy(state = newState))
            } else {
                currentMap
            }
        }
    }
}