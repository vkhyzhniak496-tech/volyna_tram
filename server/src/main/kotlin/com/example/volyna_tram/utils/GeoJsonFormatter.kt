package com.example.volyna_tram.utils

import com.example.volyna_tram.model.LiveTram

object GeoJsonFormatter {

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
}