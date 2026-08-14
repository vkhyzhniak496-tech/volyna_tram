package com.example.volyna_tram.repository

import com.example.volyna_tram.model.LiveTram
import com.example.volyna_tram.utils.GeoMathUtils
import java.util.concurrent.ConcurrentHashMap

class TramRepository {

    private val activeTrams = ConcurrentHashMap<String, LiveTram>()

    fun upsertTram(newTram: LiveTram) {
        val key = "${newTram.line}_${newTram.brigade}"
        val previousTram = activeTrams[key]

        val speed: Double
        val bearing: Float

        if (previousTram != null) {
            val distance = GeoMathUtils.calculateDistanceMeters(
                previousTram.lat, previousTram.lon,
                newTram.lat, newTram.lon
            )

            speed = GeoMathUtils.calculateSpeedKmH(
                lat1 = previousTram.lat, lon1 = previousTram.lon, t1Ms = previousTram.timestamp,
                lat2 = newTram.lat, lon2 = newTram.lon, t2Ms = newTram.timestamp,
                previousSpeed = previousTram.speed
            )

            // Kąt przeliczamy TYLKO gdy pojazd realnie jedzie (min. 14m przemieszczenia i prędkość >= 5 km/h)
            bearing = if (distance >= 14.0 && speed >= 5.0) {
                val calculatedBearing = GeoMathUtils.calculateBearing(
                    previousTram.lat, previousTram.lon,
                    newTram.lat, newTram.lon
                )
                // Wygładzamy kąt z poprzednim kierunkiem jazdy
                if (previousTram.bearing > 0f) {
                    GeoMathUtils.smoothAngle(previousTram.bearing, calculatedBearing, weight = 0.6f)
                } else {
                    calculatedBearing
                }
            } else {
                previousTram.bearing // W spoczynku lub mikro-dryfie zachowujemy stały kąt
            }
        } else {
            speed = 0.0
            bearing = 0f
        }

        activeTrams[key] = newTram.copy(speed = speed, bearing = bearing)
    }

    fun removeStaleTrams(ttlMs: Long = 90_000) {
        val expirationThreshold = System.currentTimeMillis() - ttlMs
        activeTrams.values.removeIf { it.timestamp < expirationThreshold }
    }

    fun getAll(): List<LiveTram> = activeTrams.values.toList()

    fun getById(id: String): LiveTram? = activeTrams[id]

    fun getByLine(line: String): List<LiveTram> =
        activeTrams.values.filter { it.line == line.trim() }
}