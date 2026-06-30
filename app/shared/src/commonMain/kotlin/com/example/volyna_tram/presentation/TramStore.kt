package com.example.volyna_tram.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.example.volyna_tram.domain.model.Tram

object TramStore {
    private val _tabor = MutableStateFlow(
        listOf(
            Tram("13", "13", "Cmentarz Wolski", "Kawęczyńska-Bazylika", "On Time"),
            Tram("26", "26", "Metro Młociny", "Wiatraczna", "Delayed"),
            Tram("4", "4", "Żerań Wschodni", "Wyścigi", "On Time")
        )
    )
    val tabor: StateFlow<List<Tram>> = _tabor.asStateFlow()

    // Globalne zmienne na funkcje sieciowe, które wstrzykniemy z zewnątrz
    var networkFetchAction: (() -> Unit)? = null
    var networkPatchAction: ((String, String) -> Unit)? = null

    fun updateTaborList(nowaLista: List<Tram>) {
        _tabor.value = nowaLista
    }

    fun updateTramState(linia: String, newState: String) {
        val statusNaSerwer = when (newState) {
            "On Time" -> "Płynnie"
            "Delayed" -> "Opóźnienie na Targowej"
            else -> "W trasie"
        }

        // Odpalamy strzał na serwer przez wstrzykniętą akcję, jeśli istnieje
        networkPatchAction?.invoke(linia, statusNaSerwer)

        _tabor.update { list ->
            list.map { tram ->
                if (tram.id == linia) tram.copy(state = newState) else tram
            }
        }
    }
}