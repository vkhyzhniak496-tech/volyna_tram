package com.example.volyna_tram.domain.model

import kotlinx.serialization.Serializable

/**
 * Czysty, docelowy model domenowy tramwaju używany przez Frontend i Canvas.
 */
data class Tram(
    val id: String,          // Unikalny klucz: "Linia_Brygada" (np. "17_4")
    val line: String,        // np. "17"
    val brigade: String,     // np. "4"
    val lat: Double,
    val lon: Double,
    val state: String = "W ruchu"
)

// --- STRUKTURY DTO DLA MOSTKA SERWERA (GeoJSON) ---

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
    val brigade: String
)

/**
 * Mapowanie obiektu sieciowego DTO na nasz wydajny model aplikacyjny.
 */
fun TramFeature.toDomain(): Tram? {
    val lon = geometry.coordinates.getOrNull(0) ?: return null
    val lat = geometry.coordinates.getOrNull(1) ?: return null
    val line = properties.line.trim()
    val brigade = properties.brigade.trim()

    return Tram(
        id = "${line}_${brigade}", // Generujemy unikalne ID biznesowe
        line = line,
        brigade = brigade,
        lat = lat,
        lon = lon
    )
}