package com.example.volyna_tram.data.tram

import com.example.volyna_tram.domain.model.Tram
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TramRepository {
    private val _taborMap = MutableStateFlow<Map<String, Tram>>(emptyMap())
    val taborMap: StateFlow<Map<String, Tram>> = _taborMap.asStateFlow()

    fun updateTabor(trams: List<Tram>) {
        _taborMap.value = trams.associateBy { it.id }
    }
}