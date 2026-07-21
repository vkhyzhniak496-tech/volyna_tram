package com.example.volyna_tram.data

import com.example.volyna_tram.domain.model.TramElement
import kotlinx.serialization.json.*

fun parseNetworkGeoJson(jsonString: String): List<TramElement> {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val elements = mutableListOf<TramElement>()

    try {
        val collection = json.decodeFromString<NetworkGeoJsonCollection>(jsonString)

        for (feature in collection.features) {
            val geom = feature.geometry ?: continue
            val properties = feature.properties
            val name = properties?.get("name")?.jsonPrimitive?.contentOrNull ?: "Nieznany"

            when (geom.type) {
                "LineString" -> {
                    val trackId = properties?.get("@id")?.jsonPrimitive?.contentOrNull ?: ""

                    // Odczytujemy zróżnicowane prędkości (15, 30, 50, etc.)
                    val rawMaxSpeed = properties?.get("maxspeed")?.jsonPrimitive?.contentOrNull
                    val maxSpeed = rawMaxSpeed?.toDoubleOrNull() ?: 50.0

                    val coordsArray = geom.coordinates.jsonArray
                    val points = coordsArray.map { coordElement ->
                        val pointArray = coordElement.jsonArray
                        val lon = pointArray[0].jsonPrimitive.double
                        val lat = pointArray[1].jsonPrimitive.double
                        Pair(lat, lon)
                    }

                    if (points.isNotEmpty()) {
                        elements.add(
                            TramElement.Track(
                                id = trackId,
                                points = points,
                                maxSpeed = maxSpeed
                            )
                        )
                    }
                }
                "Point" -> {
                    val pointArray = geom.coordinates.jsonArray
                    val lon = pointArray[0].jsonPrimitive.double
                    val lat = pointArray[1].jsonPrimitive.double
                    elements.add(TramElement.Stop(lat, lon, name))
                }
                "Polygon" -> {
                    val outerRing = geom.coordinates.jsonArray[0].jsonArray
                    val points = outerRing.map { coordElement ->
                        val pointArray = coordElement.jsonArray
                        val lon = pointArray[0].jsonPrimitive.double
                        val lat = pointArray[1].jsonPrimitive.double
                        Pair(lat, lon)
                    }
                    if (points.isNotEmpty()) {
                        elements.add(TramElement.Platform(points, name))
                    }
                }
            }
        }
    } catch (e: Exception) {
        println("BLAD PARSERA GEOJSON: ${e.message}")
        e.printStackTrace()
    }

    return elements
}