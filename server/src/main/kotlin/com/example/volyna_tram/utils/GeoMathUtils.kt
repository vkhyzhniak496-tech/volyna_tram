package com.example.volyna_tram.utils

import kotlin.math.*

object GeoMathUtils {
    fun calculateSpeedKmH(
        lat1: Double, lon1: Double, t1Ms: Long,
        lat2: Double, lon2: Double, t2Ms: Long
    ): Double {
        val timeDiffSeconds = (t2Ms - t1Ms) / 1000.0
        if (timeDiffSeconds <= 0.5) return 0.0

        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distanceMeters = earthRadiusMeters * c

        val speedMs = distanceMeters / timeDiffSeconds
        val speedKmH = speedMs * 3.6

        // Odrzucamy nierealne skoki GPS (teleportacja > 90 km/h)
        return if (speedKmH in 0.0..90.0) {
            (speedKmH * 10.0).roundToInt() / 10.0
        } else {
            0.0
        }
    }
}