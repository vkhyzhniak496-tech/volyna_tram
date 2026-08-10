package com.example.volyna_tram.data.tram

import com.example.volyna_tram.domain.model.Tram
import kotlinx.serialization.json.Json

private val jsonDecoder = Json { ignoreUnknownKeys = true }


fun parseTramGeoJson(jsonString: String): List<Tram> {
    val response = jsonDecoder.decodeFromString<GeoJsonTramResponse>(jsonString)
    return response.features.mapNotNull { it.toDomain() }
}