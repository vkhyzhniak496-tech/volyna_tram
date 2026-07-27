package com.example.volyna_tram.config

object NetworkConfig {
    private const val SERVER_IP = "192.168.0.132"
    private const val PORT = 8080

    val baseUrl: String
        get() = "http://$SERVER_IP:$PORT"
}