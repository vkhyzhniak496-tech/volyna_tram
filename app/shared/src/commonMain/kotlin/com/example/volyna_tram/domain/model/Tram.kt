package com.example.volyna_tram.domain.model

import kotlinx.serialization.Serializable

import kotlin.time.Clock

/**
 * Czysty, docelowy model domenowy tramwaju używany przez Frontend i Canvas.
 */
data class Tram(
    val id: String,          // Unikalny klucz: "Linia_Brygada" (np. "17_4")
    val line: String,        // np. "17"
    val brigade: String,     // np. "4"
    val lat: Double,
    val lon: Double,
    val speed: Double = 0.0,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)
