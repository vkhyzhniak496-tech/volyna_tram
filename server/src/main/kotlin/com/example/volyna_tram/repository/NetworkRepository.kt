package com.example.volyna_tram.repository

import java.io.File
import java.io.InputStream

object NetworkRepository {

    private const val CACHE_FILE_NAME = "network.geojson"

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

            if (cacheFile.exists() && cacheFile.length() > 0) {
                // 1. Priorytet: Zapisana świeża sieć z dysku
                cachedBaseMapJson = cacheFile.readText()
                networkVersion = cacheFile.lastModified()
                println("[NETWORK_REPO] 💾 Załadowano najnowszą sieć z pliku cache ($CACHE_FILE_NAME). Wersja: $networkVersion")
            } else {
                // 2. Fallback: Domyślny plik z zasobów projektowych
                val mapStream: InputStream? = this::class.java.classLoader.getResourceAsStream("export.geojson")
                if (mapStream != null) {
                    cachedBaseMapJson = mapStream.bufferedReader().use { it.readText() }
                    println("[NETWORK_REPO] 📦 Załadowano domyślną sieć fabryczną z resources (export.geojson).")
                } else {
                    println("[NETWORK_REPO] ⚠️ Brak pliku fabrycznego w resources.")
                }
            }

            // Ładowanie peronów
            val platformStream: InputStream? = this::class.java.classLoader.getResourceAsStream("platforms.geojson")
            if (platformStream != null) {
                cachedPlatformsJson = platformStream.bufferedReader().use { it.readText() }
            }

        } catch (e: Exception) {
            println("[NETWORK_REPO] ❌ Błąd podczas ładowania zasobów sieci: ${e.message}")
        }
    }

    fun getBaseMapGeoJson(): String = cachedBaseMapJson

    fun getPlatformsGeoJson(): String = cachedPlatformsJson

    /**
     * Podmienia sieć w RAM oraz zapisuje ją na dysku serwera.
     */
    fun updateNetwork(newMapJson: String, newPlatformsJson: String = "") {
        if (newMapJson.isNotEmpty()) {
            cachedBaseMapJson = newMapJson

            // Zapisujemy na dysk, żeby po restarcie serwera zmiana nie przepadła!
            try {
                File(CACHE_FILE_NAME).writeText(newMapJson)
                println("[NETWORK_REPO] 💾 Zapisano nową sieć do pliku cache na dysku.")
            } catch (e: Exception) {
                println("[NETWORK_REPO] ⚠️ Nie udało się zapisać cache na dysku: ${e.message}")
            }
        }

        if (newPlatformsJson.isNotEmpty()) {
            cachedPlatformsJson = newPlatformsJson
        }

        networkVersion = System.currentTimeMillis()
        println("[NETWORK_REPO] 🔄 Zaktualizowano sieć w RAM! Nowa wersja: $networkVersion")
    }
}