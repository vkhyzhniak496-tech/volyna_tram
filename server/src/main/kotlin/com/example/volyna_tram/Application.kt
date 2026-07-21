package com.example.volyna_tram

import com.example.volyna_tram.service.TramLiveService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val tramLiveService = TramLiveService()
    tramLiveService.startLiveTracking(this)

    routing {
        // Obsługa CORS dla frontendu (Web / Android)
        intercept(ApplicationCallPipeline.Plugins) {
            val call = this.call
            call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*", safeOnly = false)
            call.response.headers.append(HttpHeaders.AccessControlAllowMethods, "GET, POST, OPTIONS", safeOnly = false)
            call.response.headers.append(HttpHeaders.AccessControlAllowHeaders, "*", safeOnly = false)
            if (call.request.httpMethod == HttpMethod.Options) {
                call.respond(HttpStatusCode.OK)
                return@intercept
            }
        }

        // 1. Warstwa Geometrii Infrastruktury (Overpass GeoJSON)
        route("/api/network/map") {
            get {
                val inputStream = this::class.java.classLoader.getResourceAsStream("export.geojson")
                if (inputStream != null) {
                    val geoJsonText = inputStream.bufferedReader().use { it.readText() }
                    call.respondText(geoJsonText, ContentType.Application.Json)
                } else {
                    call.respondText("{\"error\": \"Plik grafu nie znaleziony\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

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

        // 2. Warstwa Ruchu Taboru z Gotową Prędkością
        route("/api/trams") {
            get("/live") {
                val geoJsonTrams = tramLiveService.getTramsAsGeoJson()
                call.respondText(geoJsonTrams, ContentType.Application.Json)
            }
        }
    }
}