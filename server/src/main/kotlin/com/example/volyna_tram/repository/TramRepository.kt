package com.example.volyna_tram.repository

import com.example.volyna_tram.model.LiveTram
import com.example.volyna_tram.utils.GeoMathUtils
import java.util.concurrent.ConcurrentHashMap

class TramRepository {

    private val activeTrams = ConcurrentHashMap<String, LiveTram>()

    init {
        // Dane początkowe do testów
        upsertTram(LiveTram("17", "03", 52.219, 21.001, speed = 25.0, bearing = 180f))
        upsertTram(LiveTram("9", "12", 52.231, 21.005, speed = 0.0, bearing = 90f))
        upsertTram(LiveTram("19", "01", 52.225, 21.003, speed = 42.0, bearing = 270f))
    }

    fun upsertTram(newTram: LiveTram) {
        val key = "${newTram.line}_${newTram.brigade}"
        val previousTram = activeTrams[key]

        val speed: Double
        val bearing: Float

        if (previousTram != null) {
            speed = GeoMathUtils.calculateSpeedKmH(
                lat1 = previousTram.lat, lon1 = previousTram.lon, t1Ms = previousTram.timestamp,
                lat2 = newTram.lat, lon2 = newTram.lon, t2Ms = newTram.timestamp
            )

            // Wyliczamy nowy bearing tylko jeśli pojazd zmienił pozycję i jedzie (> 1 km/h)
            val isMoved = previousTram.lat != newTram.lat || previousTram.lon != newTram.lon
            bearing = if (isMoved && speed > 1.0) {
                GeoMathUtils.calculateBearing(
                    lat1 = previousTram.lat,
                    lon1 = previousTram.lon,
                    lat2 = newTram.lat,
                    lon2 = newTram.lon
                )
            } else {
                previousTram.bearing // zachowaj ostatni kierunek
            }
        } else {
            speed = newTram.speed
            bearing = newTram.bearing
        }

        activeTrams[key] = newTram.copy(speed = speed, bearing = bearing)
    }

    // --- TE METODY BYŁY POTRZEBNE DLA SERWISU I ROUTINGU ---

    /** Zwraca wszystkie tramwaje w pamięci RAM */
    fun getAll(): List<LiveTram> = activeTrams.values.toList()

    /** Zwraca tramwaj po ID (np. "17_03") */
    fun getById(id: String): LiveTram? = activeTrams[id]

    /** Zwraca tramwaje danej linii (np. "9") */
    fun getByLine(line: String): List<LiveTram> =
        activeTrams.values.filter { it.line == line.trim() }

    fun removeStaleTrams(ttlMs: Long = 90000) {
        val expirationThreshold = System.currentTimeMillis() - ttlMs
        activeTrams.values.removeIf { it.timestamp < expirationThreshold }
    }
}