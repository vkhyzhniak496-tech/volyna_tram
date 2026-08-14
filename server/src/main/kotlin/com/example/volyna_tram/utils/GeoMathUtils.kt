package com.example.volyna_tram.utils

import kotlin.math.*

object GeoMathUtils {

    // Twoja istniejąca funkcja prędkości...
    fun calculateSpeedKmH(lat1: Double, lon1: Double, t1Ms: Long, lat2: Double, lon2: Double, t2Ms: Long): Double {
        val distMeters = calculateDistanceMeters(lat1, lon1, lat2, lon2)
        val timeSec = (t2Ms - t1Ms) / 1000.0
        if (timeSec <= 0 || distMeters <= 0.0) return 0.0
        return (distMeters / timeSec) * 3.6
    }

    /**
     * Wylicza azymut ruchu w stopniach (0° = Północ, 90° = Wschód, 180° = Południe, 270° = Zachód).
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)

        return ((Math.toDegrees(theta) + 360.0) % 360.0).toFloat()
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // metry
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}