package com.example.volyna_tram.utils

import com.example.volyna_tram.model.LiveTram
import java.util.concurrent.ConcurrentHashMap

class TramRepository {

    private val activeTrams = ConcurrentHashMap<String, LiveTram>()

    init {
        // Dane początkowe do testów
        upsertTram(LiveTram("17", "03", 52.219, 21.001, speed = 25.0))
        upsertTram(LiveTram("9", "12", 52.231, 21.005, speed = 0.0))
        upsertTram(LiveTram("19", "01", 52.225, 21.003, speed = 42.0))
    }

    /**
     * Wstawia lub aktualizuje wóz na podstawie klucza (Linia + Brygada).
     * Wylicza prędkość na podstawie poprzedniej pozycji i czasu.
     */
    fun upsertTram(newTram: LiveTram) {
        val key = "${newTram.line}_${newTram.brigade}"
        val previousTram = activeTrams[key]

        val speed = if (previousTram != null) {
            GeoMathUtils.calculateSpeedKmH(
                lat1 = previousTram.lat, lon1 = previousTram.lon, t1Ms = previousTram.timestamp,
                lat2 = newTram.lat, lon2 = newTram.lon, t2Ms = newTram.timestamp
            )
        } else {
            newTram.speed
        }

        activeTrams[key] = newTram.copy(speed = speed)
    }

    /**
     * Zwraca listę wszystkich aktualnych tramwajów w RAM.
     */
    fun getAll(): List<LiveTram> = activeTrams.values.toList()
}