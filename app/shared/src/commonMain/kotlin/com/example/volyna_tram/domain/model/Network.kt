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

sealed interface TramElement {
    data class Track(val points: List<Pair<Double, Double>>) : TramElement
    data class Stop(val lat: Double, val lon: Double, val name: String) : TramElement

    // Nowy interfejs dla peronu wektorowego
    data class Platform(
        val polygonPoints: List<Pair<Double, Double>>,
        val name: String
    ) : TramElement
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
            val geom = feature.geometry ?: continue

            // Wyciągamy wspólną nazwę (przyda się i dla Stop, i dla Platform)
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

                    // POPRAWKA: Prawidłowa kolejność argumentów (lat, lon, name) zgodnie z data class
                    elements.add(TramElement.Stop(lat, lon, name))
                }
                "Polygon" -> {
                    // W GeoJSON Polygon to tablica tablic punktów (pierwsza tablica to zewnętrzny obrys)
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