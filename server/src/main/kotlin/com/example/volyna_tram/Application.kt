package com.example.volyna_tram

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import com.example.volyna_tram.model.LiveTram
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

// 🚋 Nowy, czysty model pod realne współrzędne z warszawskiego API


fun Application.module() {

    // Nasza tymczasowa baza w RAM na start, zanim ruszy asynchroniczny fetcher w tle
    val liveTramsBaza = mutableListOf(
        LiveTram("17", "03", 52.219, 21.001),
        LiveTram("9", "12", 52.231, 21.005),
        LiveTram("19", "01", 52.225, 21.003)
    )

    routing {
        // 🌐 ABSOLUTNIE ODPORNY NA WERSJE MOSTEK CORS
        intercept(ApplicationCallPipeline.Plugins) {
            val call = this.call

            call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*", safeOnly = false)
            call.response.headers.append(HttpHeaders.AccessControlAllowMethods, "GET, POST, PATCH, PUT, DELETE, OPTIONS", safeOnly = false)
            call.response.headers.append(HttpHeaders.AccessControlAllowHeaders, "*", safeOnly = false)

            if (call.request.httpMethod == HttpMethod.Options) {
                call.respond(HttpStatusCode.OK)
                return@intercept
            }
        }

        // ==========================================
        // 🗺️ WARSTWA GEOMETRII I TOPOLOGII (STATYCZNA)
        // ==========================================
        route("/api/network/map") {

            // Główne zapytanie o tory: /api/network/map
            get {
                val inputStream = this::class.java.classLoader.getResourceAsStream("export.geojson")
                if (inputStream != null) {
                    val geoJsonText = inputStream.bufferedReader().use { it.readText() }
                    call.respondText(geoJsonText, ContentType.Application.Json)
                } else {
                    call.respondText("{\"error\": \"Plik grafu nie znaleziony\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            // Podścieżka dla peronów: /api/network/map/platforms
            get("/platforms") {
                val inputStream = this::class.java.classLoader.getResourceAsStream("platforms.geojson")
                if (inputStream != null) {
                    val platformsJsonText = inputStream.bufferedReader().use { it.readText() }
                    call.respondText(platformsJsonText, ContentType.Application.Json)
                } else {
                    call.respondText("{\"error\": \"Plik peronów nie został znaleziony\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }
        }

        // ==========================================
        // 🚀 WARSTWA RUCHU TABORU (DYNAMICZNA)
        // ==========================================
        route("/api/trams") {

            // 📍 NOWY ENDPOINT: /api/trams/live
            // Czysty, bez zbędnego parsowania na piechotę. Zwraca obiekty LiveTram.
            get("/live") {
                // Na razie wypluwamy naszą testową listę z RAM-u.
                // Kiedy Ktor 3+ dostanie w konfiguracji ContentNegotiation z JSON,
                // będzie można robić bezpośrednio `call.respond(liveTramsBaza)`.
                // Bezpieczny fallback na gołym ciele, gwarantujący brak wywrotek na frontendzie:
                val json = liveTramsBaza.joinToString(
                    prefix = "[\n",
                    postfix = "\n]",
                    separator = ",\n"
                ) {
                    """  {"line": "${it.line}", "brigade": "${it.brigade}", "lat": ${it.lat}, "lon": ${it.lon}}"""
                }
                call.respondText(json, ContentType.Application.Json)
            }
        }
    }
}