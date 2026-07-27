package com.example.volyna_tram.repository

import java.io.InputStream

object NetworkRepository {

    @Volatile
    private var cachedBaseMapJson: String = ""

    @Volatile
    private var cachedPlatformsJson: String = ""

    // Stempel czasowy wersji sieci (pozwoli klientowi w przyszłości pytać o zmiany)
    @Volatile
    var networkVersion: Long = System.currentTimeMillis()
        private set

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        try {
            // Wczytywanie domyślnych zasobów z resources przy starcie serwera
            val mapStream: InputStream? = this::class.java.classLoader.getResourceAsStream("export.geojson")
            if (mapStream != null) {
                cachedBaseMapJson = mapStream.bufferedReader().use { it.readText() }
                println("[NETWORK_REPO] Pomyślnie załadowano export.geojson do RAM.")
            } else {
                println("[NETWORK_REPO] Nie znaleziono export.geojson w resources.")
            }

            val platformStream: InputStream? = this::class.java.classLoader.getResourceAsStream("platforms.geojson")
            if (platformStream != null) {
                cachedPlatformsJson = platformStream.bufferedReader().use { it.readText() }
                println("[NETWORK_REPO] Pomyślnie załadowano platforms.geojson do RAM.")
            } else {
                println("[NETWORK_REPO] Nie znaleziono platforms.geojson w resources.")
            }

        } catch (e: Exception) {
            println("[NETWORK_REPO] Błąd podczas ładowania zasobów sieci: ${e.message}")
        }
    }

    fun getBaseMapGeoJson(): String = cachedBaseMapJson

    fun getPlatformsGeoJson(): String = cachedPlatformsJson

    /**
     * Zezwala na dynamiczną podmianę geometrii w RAM podczas działania serwera.
     */
    fun updateNetwork(newMapJson: String, newPlatformsJson: String = "") {
        if (newMapJson.isNotEmpty()) {
            cachedBaseMapJson = newMapJson
        }
        if (newPlatformsJson.isNotEmpty()) {
            cachedPlatformsJson = newPlatformsJson
        }
        networkVersion = System.currentTimeMillis()
        println("[NETWORK_REPO] Zaktualizowano sieć w pamięci RAM! Wersja: $networkVersion")
    }
}