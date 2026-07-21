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
// W obiekcie TramStore aktualizujemy/podmieniamy tylko konkretny wóz w Mapie O(1)
    fun updateTaborList(trams: List<Tram>) {
        val newMap = trams.associateBy { it.id }
        _taborMap.value = newMap
    }
}