package com.example.volyna_tram.utils

import com.example.volyna_tram.model.LiveTram
import kotlinx.serialization.json.*

object GeoJsonFormatter {

    /**
     * Zbudowany wcześniej format dla dynamicznych pozycji tramwajów (Live)
     */
    fun formatTramsToGeoJson(trams: List<LiveTram>): String {
        return buildString {
            append("""{"type":"FeatureCollection","features":[""")

            trams.forEachIndexed { index, tram ->
                append("""{""")
                append("""  "type":"Feature",""")
                append("""  "geometry":{""")
                append("""    "type":"Point",""")
                append("""    "coordinates":[${tram.lon},${tram.lat}]""")
                append("""  },""")
                append("""  "properties":{""")
                append("""    "line":"${tram.line}",""")
                append("""    "brigade":"${tram.brigade}",""")
                append("""    "speed":${tram.speed},""")
                append("""    "timestamp":${tram.timestamp}""")
                append("""  }""")
                append("""}""")

                if (index < trams.lastIndex) append(",")
            }

            append("]}")
        }
    }


    fun formatOsmToGeoJson(rawOsmJson: String): String {
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(rawOsmJson).jsonObject
        val elements = root["elements"]?.jsonArray ?: return """{"type":"FeatureCollection","features":[]}"""

        val nodeMap = mutableMapOf<Long, Pair<Double, Double>>()
        elements.forEach { el ->
            val obj = el.jsonObject
            if (obj["type"]?.jsonPrimitive?.content == "node") {
                val id = obj["id"]?.jsonPrimitive?.longOrNull
                val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull
                val lon = obj["lon"]?.jsonPrimitive?.doubleOrNull
                if (id != null && lat != null && lon != null) {
                    nodeMap[id] = Pair(lon, lat)
                }
            }
        }

        val features = mutableListOf<JsonObject>()

        elements.forEach { el ->
            val obj = el.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content

            if (type == "way") {
                val nodes = obj["nodes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.longOrNull } ?: emptyList()
                val coordinates = nodes.mapNotNull { nodeMap[it] }

                if (coordinates.size >= 2) {
                    val coordArray = JsonArray(coordinates.map { (lon, lat) ->
                        JsonArray(listOf(JsonPrimitive(lon), JsonPrimitive(lat)))
                    })

                    val geometry = buildJsonObject {
                        put("type", "LineString")
                        put("coordinates", coordArray)
                    }

                    val tags = obj["tags"]?.jsonObject ?: buildJsonObject {}
                    val properties = buildJsonObject {
                        tags.forEach { (key, value) -> put(key, value) }
                        val wayId = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                        put("@id", JsonPrimitive(wayId))
                    }

                    val feature = buildJsonObject {
                        put("type", "Feature")
                        put("geometry", geometry)
                        put("properties", properties)
                    }
                    features.add(feature)
                }
            }
            else if (type == "node") {
                val tags = obj["tags"]?.jsonObject
                if (tags != null && tags.containsKey("railway")) {
                    val id = obj["id"]?.jsonPrimitive?.longOrNull
                    val pos = nodeMap[id]
                    if (pos != null) {
                        val geometry = buildJsonObject {
                            put("type", "Point")
                            put("coordinates", JsonArray(listOf(JsonPrimitive(pos.first), JsonPrimitive(pos.second))))
                        }
                        val feature = buildJsonObject {
                            put("type", "Feature")
                            put("geometry", geometry)
                            put("properties", tags)
                        }
                        features.add(feature)
                    }
                }
            }
        }

        val geoJsonResult = buildJsonObject {
            put("type", "FeatureCollection")
            put("features", JsonArray(features))
        }

        return geoJsonResult.toString()
    }
}