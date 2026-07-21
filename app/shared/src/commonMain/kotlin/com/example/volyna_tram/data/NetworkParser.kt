package com.example.volyna_tram.data.parser

import com.example.volyna_tram.data.NetworkGeoJsonCollection
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
            val name = feature.properties?.get("name")?.jsonPrimitive?.contentOrNull ?: "Nieznany"

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