package com.example.volyna_tram.data.tram


import com.example.volyna_tram.domain.model.Tram
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class GeoJsonTramResponse(
    val type: String = "FeatureCollection",
    val features: List<TramFeature> = emptyList()
)

@Serializable
data class TramFeature(
    val type: String = "Feature",
    val geometry: TramGeometry,
    val properties: TramProperties
)

@Serializable
data class TramGeometry(
    val type: String = "Point",
    val coordinates: List<Double>
)

@Serializable
data class TramProperties(
    val line: String,
    val brigade: String,
    val speed: Double? = null,
    val timestamp: Long? = null
)

/**
 * Mapowanie DTO na model domenowy.
 */



fun TramFeature.toDomain(): Tram? {
    val lon = geometry.coordinates.getOrNull(0) ?: return null
    val lat = geometry.coordinates.getOrNull(1) ?: return null
    val line = properties.line.trim()
    val brigade = properties.brigade.trim()

    // Używamy czasu przysłanego z serwera, a gdyby go brakowało - czasu lokalnego
    val timestamp = properties.timestamp ?: Clock.System.now().toEpochMilliseconds()

    // Przekazujemy realną prędkość przeliczoną przez serwer (lub 0.0 jako fallback)
    val parsedSpeed = properties.speed ?: 0.0

    return Tram(
        id = "${line}_${brigade}",
        line = line,
        brigade = brigade,
        lat = lat,
        lon = lon,
        timestamp = timestamp,
        speed = parsedSpeed // <-- Podpinamy zmienną z wyliczoną prędkością!
    )
}