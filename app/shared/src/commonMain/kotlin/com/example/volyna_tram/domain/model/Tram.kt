package com.example.volyna_tram.domain.model

import kotlinx.serialization.Serializable

import kotlin.time.Clock

/**
 * Czysty, docelowy model domenowy tramwaju używany przez Frontend i Canvas.
 */
data class Tram(
    val id: String,          // "17_4"
    val line: String,
    val brigade: String,
    val lat: Double,
    val lon: Double,
    val speed: Double = 0.0,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val bearing: Float = 0f  // <-- Kąt w stopniach (0..360°)
)