package com.example.volyna_tram.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class NetworkGeoJsonCollection(val features: List<NetworkGeoJsonFeature> = emptyList())

@Serializable
data class NetworkGeoJsonFeature(
    val geometry: NetworkGeoJsonGeometry? = null,
    val properties: JsonObject? = null
)

@Serializable
data class NetworkGeoJsonGeometry(
    val type: String,
    val coordinates: JsonElement
)