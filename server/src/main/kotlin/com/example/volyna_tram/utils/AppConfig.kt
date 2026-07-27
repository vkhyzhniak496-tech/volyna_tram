package com.example.volyna_tram.utils

import java.io.File
import java.util.Properties

object AppConfig {
    private val properties = Properties().apply {
        val localFile = File("local.properties")
        if (localFile.exists()) {
            localFile.inputStream().use { load(it) }
        }
    }

    val apiKey: String = System.getenv("WAW_API_KEY")
        ?: properties.getProperty("WAW_API_KEY")
        ?: "BRAK_KLUCZA"

    val serverIp: String = properties.getProperty("SERVER_IP") ?: "192.168.0.132"
}