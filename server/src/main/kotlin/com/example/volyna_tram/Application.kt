package com.example.volyna_tram

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.http.*

// Importujemy model oraz nasz nowy serwis
import com.example.volyna_tram.model.LiveTram
import com.example.volyna_tram.service.TramLiveService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {

    // Inicjalizujemy serwis zarządzający tramwajami w pamięci RAM
    val tramLiveService = TramLiveService()
    tramLiveService.startLiveTracking(this)

    routing {
        // Mostek CORS (zostaje bez zmian)
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

        // Warstwa geometrii (mapa i platformy zostają bez zmian)
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

        // Warstwa ruchu taboru – teraz spięta z Thread-Safe Managerem
        route("/api/trams") {
            get("/live") {
                // Pobieramy dane bezpośrednio z serwisu
                val aktualneTramwaje = tramLiveService.getAllTrams()

                val json = aktualneTramwaje.joinToString(
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