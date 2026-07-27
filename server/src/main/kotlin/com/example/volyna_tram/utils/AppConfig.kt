package com.example.volyna_tram.utils

import java.io.File
import java.util.Properties



object AppConfig {
    private val properties = Properties().apply {
        // Szukamy w głównym katalogu projektu, a jeśli nie ma - w katalogu wyżej
        val localFile = sequenceOf(
            File("local.properties"),
            File("../local.properties")
        ).firstOrNull { it.exists() }

        if (localFile != null) {
            localFile.inputStream().use { load(it) }
            println("[APP_CONFIG] ✅ Wczytano konfig z: ${localFile.absolutePath}")
        } else {
            println("[APP_CONFIG] ⚠️ Nie znaleziono pliku local.properties!")
        }
    }

    val apiKey: String = System.getenv("WAW_API_KEY")
        ?: properties.getProperty("WAW_API_KEY")
        ?: "BRAK_KLUCZA"

    val serverIp: String = properties.getProperty("SERVER_IP") ?: "127.0.0.1"
}