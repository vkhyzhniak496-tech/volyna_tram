package com.example.volyna_tram.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveTram(
    val line: String,
    val brigade: String,
    val lat: Double,
    val lon: Double,
    val timestamp: Long=System.currentTimeMillis()
)