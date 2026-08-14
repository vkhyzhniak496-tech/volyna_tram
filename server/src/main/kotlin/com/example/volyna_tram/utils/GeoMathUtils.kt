package com.example.volyna_tram.utils

import kotlin.math.*

object GeoMathUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Oblicza odległość w metrach między dwoma punktami GPS (wzór Haversine).
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Oblicza prędkość w km/h z filtrem strefy martwej GPS (Deadband).
     */
    fun calculateSpeedKmH(
        lat1: Double, lon1: Double, t1Ms: Long,
        lat2: Double, lon2: Double, t2Ms: Long
    ): Double {
        val timeDiffSeconds = (t2Ms - t1Ms) / 1000.0
        if (timeDiffSeconds <= 0.0 || timeDiffSeconds > 60.0) return 0.0

        val distanceMeters = calculateDistanceMeters(lat1, lon1, lat2, lon2)

        // 🛑 JEŚLI RUCH WYNOSI MNIEJ NIŻ 8 METRÓW W 10S -> TO JEST SZUM GPS W SPOCZYNKU
        if (distanceMeters < 8.0) {
            return 0.0
        }

        val speedKmH = (distanceMeters / timeDiffSeconds) * 3.6

        // 🛑 ODCINAMY PRĘDKOŚCI PONIŻEJ 3.5 km/h
        return if (speedKmH < 5) 0.0 else speedKmH
    }

    /**
     * Oblicza kąt kierunku (Azymut 0-360°).
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLonRad = Math.toRadians(lon2 - lon1)

        val y = sin(dLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)

        var bearing = Math.toDegrees(atan2(y, x)).toFloat()
        bearing = (bearing + 360f) % 360f
        return bearing
    }
}