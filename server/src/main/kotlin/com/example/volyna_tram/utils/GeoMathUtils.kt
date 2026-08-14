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
     * Oblicza prędkość z filtrem szumów GPS i wygładzaniem (EMA).
     */
    fun calculateSpeedKmH(
        lat1: Double, lon1: Double, t1Ms: Long,
        lat2: Double, lon2: Double, t2Ms: Long,
        previousSpeed: Double = 0.0
    ): Double {
        val timeDiffSeconds = (t2Ms - t1Ms) / 1000.0
        // Ignorujemy niepoprawne lub zbyt odległe próbki czasowe (>90s)
        if (timeDiffSeconds < 2.0 || timeDiffSeconds > 90.0) return previousSpeed

        val distanceMeters = calculateDistanceMeters(lat1, lon1, lat2, lon2)

        // 🛑 Próg postoju: przemieszczenie < 14m w ~10-15s to w warunkach miejskich postój (dryf GPS)
        if (distanceMeters < 14.0) {
            return 0.0
        }

        val rawSpeedKmH = (distanceMeters / timeDiffSeconds) * 3.6

        // 🛑 Filtr anomalii: Tramwaj w Warszawie nie przekracza 70 km/h (odrzucamy teleportacje GPS)
        if (rawSpeedKmH > 75.0) {
            return previousSpeed.coerceAtMost(60.0)
        }

        // 🌊 Wygładzanie wykładnicze (EMA): 60% nowy odczyt + 40% poprzednia prędkość
        val smoothedSpeed = if (previousSpeed > 0.0) {
            (0.6 * rawSpeedKmH) + (0.4 * previousSpeed)
        } else {
            rawSpeedKmH
        }

        return if (smoothedSpeed < 4.0) 0.0 else smoothedSpeed
    }

    /**
     * Oblicza azymut geograficzny (0-360°).
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLonRad = Math.toRadians(lon2 - lon1)

        val y = sin(dLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)

        var bearing = Math.toDegrees(atan2(y, x)).toFloat()
        return (bearing + 360f) % 360f
    }

    /**
     * Wygładza kąt po okręgu (zapobiega skokom na granicy 0° / 360°).
     */
    fun smoothAngle(oldAngle: Float, newAngle: Float, weight: Float = 0.5f): Float {
        val diff = ((newAngle - oldAngle + 180f) % 360f) - 180f
        return (oldAngle + diff * weight + 360f) % 360f
    }
}