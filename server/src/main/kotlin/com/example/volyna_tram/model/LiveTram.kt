package com.example.volyna_tram.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveTram(
    val line: String,
    val brigade: String,
    val lat: Double,
    val lon: Double,
    val speed: Double = 0.0, // Prędkość chwilowa przeliczona na serwerze (km/h)
    val timestamp: Long = System.currentTimeMillis()
)