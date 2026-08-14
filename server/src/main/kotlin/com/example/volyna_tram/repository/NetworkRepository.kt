package com.example.volyna_tram.repository

import java.io.File
import java.io.InputStream

object NetworkRepository {

    private const val CACHE_FILE_NAME = "cached_network.geojson"

    @Volatile
    private var cachedBaseMapJson: String = ""

    @Volatile
    private var cachedPlatformsJson: String = ""

    @Volatile
    var networkVersion: Long = System.currentTimeMillis()
        private set

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        try {
            val cacheFile = File(CACHE_FILE_NAME)

            // 1. Jeśli na dysku jest plik cache z nową siecią, wczytaj go!
            if (cacheFile.exists() && cacheFile.length() > 0) {
                cachedBaseMapJson = cacheFile.readText()
                networkVersion = cacheFile.lastModified()
                println("[NETWORK_REPO] 💾 Załadowano najnowszą zaktualizowaną sieć z pliku $CACHE_FILE_NAME.")
            } else {
                // 2. Jeśli plik cache nie istnieje, wczytaj fabryczny schemat z resources
                val mapStream: InputStream? = this::class.java.classLoader.getResourceAsStream("export.geojson")
                if (mapStream != null) {
                    cachedBaseMapJson = mapStream.bufferedReader().use { it.readText() }
                    println("[NETWORK_REPO] 📦 Załadowano domyślną sieć z resources (export.geojson).")
                }
            }

            val platformStream: InputStream? = this::class.java.classLoader.getResourceAsStream("platforms.geojson")
            if (platformStream != null) {
                cachedPlatformsJson = platformStream.bufferedReader().use { it.readText() }
            }

        } catch (e: Exception) {
            println("[NETWORK_REPO] ❌ Błąd podczas ładowania sieci: ${e.message}")
        }
    }

    fun getBaseMapGeoJson(): String = cachedBaseMapJson
    fun getPlatformsGeoJson(): String = cachedPlatformsJson

    /**
     * Zapisuje nową sieć do RAM oraz do pliku cache na dysku
     */
    fun updateNetwork(newMapJson: String, newPlatformsJson: String = "") {
        if (newMapJson.isNotEmpty()) {
            cachedBaseMapJson = newMapJson

            // Zapisujemy na dysk, żeby po restarcie serwera wczytała się nowa sieć!
            try {
                File(CACHE_FILE_NAME).writeText(newMapJson)
                println("[NETWORK_REPO]  Zapisano nową sieć z Overpassa do pliku $CACHE_FILE_NAME.")
            } catch (e: Exception) {
                println("[NETWORK_REPO] ⚠ Błąd zapisu pliku cache: ${e.message}")
            }
        }

        if (newPlatformsJson.isNotEmpty()) {
            cachedPlatformsJson = newPlatformsJson
        }

        networkVersion = System.currentTimeMillis()
        println("[NETWORK_REPO]  Zaktualizowano wersję sieci w RAM: $networkVersion")
    }
}