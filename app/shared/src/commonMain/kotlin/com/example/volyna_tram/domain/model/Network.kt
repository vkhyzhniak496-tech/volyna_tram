package com.example.volyna_tram.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class GeoJsonCollection(val features: List<GeoJsonFeature> = emptyList())

@Serializable
data class GeoJsonFeature(
    val geometry: GeoJsonGeometry? = null,
    val properties: JsonObject? = null
)

@Serializable
data class GeoJsonGeometry(
    val type: String,
    val coordinates: JsonElement
)

sealed class TramElement {
    data class Track(val points: List<Pair<Double, Double>>) : TramElement()
    data class Stop(val name: String?, val lat: Double, val lon: Double) : TramElement()
}

fun parseNetworkGeoJson(jsonString: String): List<TramElement> {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val elements = mutableListOf<TramElement>()

    try {
        val collection = json.decodeFromString<GeoJsonCollection>(jsonString)

        for (feature in collection.features) {
            val geom = feature.geometry ?: continue // jeśli brak geometrii, leć dalej

            when (geom.type) {
                "LineString" -> {
                    val coordsArray = geom.coordinates.jsonArray
                    val points = coordsArray.map { coordElement ->
                        val pointArray = coordElement.jsonArray
                        val lon = pointArray[0].jsonPrimitive.double
                        val lat = pointArray[1].jsonPrimitive.double
                        Pair(lat, lon)
                    }
                    if (points.isNotEmpty()) {
                        elements.add(TramElement.Track(points))
                    }
                }
                "Point" -> {
                    val pointArray = geom.coordinates.jsonArray
                    val lon = pointArray[0].jsonPrimitive.double
                    val lat = pointArray[1].jsonPrimitive.double

                    // Bezpieczne wyciąganie nazwy przystanku z JsonObject
                    val name = feature.properties?.get("name")?.jsonPrimitive?.contentOrNull

                    elements.add(TramElement.Stop(name, lat, lon))
                }
            }
        }
    } catch (e: Exception) {
        // W środowisku WASM/JS wypisujemy błąd bezpośrednio w konsoli przeglądarki
        println("BLAD PARSERA GEOJSON: ${e.message}")
        e.printStackTrace()
    }

    return elements
}