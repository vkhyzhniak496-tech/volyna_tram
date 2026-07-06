package com.example.volyna_tram.domain.model

import kotlinx.serialization.Serializable

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
) {
    val lon: Double get() = coordinates.getOrNull(0) ?: 0.0
    val lat: Double get() = coordinates.getOrNull(1) ?: 0.0
}

@Serializable
data class TramProperties(
    val line: String,
    val brigade: String
)