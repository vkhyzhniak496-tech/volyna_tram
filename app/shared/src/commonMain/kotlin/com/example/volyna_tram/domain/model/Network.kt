package com.example.volyna_tram.domain.model

sealed interface TramElement {
    data class Track(val points: List<Pair<Double, Double>>) : TramElement {
        val cumulativeDistances: DoubleArray = DoubleArray(points.size)
        val totalLength: Double

        init {
            var dist = 0.0
            cumulativeDistances[0] = 0.0
            for (i in 1 until points.size) {
                val p1 = points[i - 1]
                val p2 = points[i]
                val dLat = p2.first - p1.first
                val dLon = p2.second - p1.second
                dist += kotlin.math.sqrt(dLat * dLat + dLon * dLon)
                cumulativeDistances[i] = dist
            }
            totalLength = dist
        }
    }

    data class Stop(
        val lat: Double,
        val lon: Double,
        val name: String
    ) : TramElement

    data class Platform(
        val polygonPoints: List<Pair<Double, Double>>,
        val name: String
    ) : TramElement
}