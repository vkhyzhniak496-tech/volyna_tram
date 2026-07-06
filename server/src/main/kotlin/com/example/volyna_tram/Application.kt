package com.example.volyna_tram

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.http.*

// Importujemy nasz zaktualizowany serwis
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
        // Mostek CORS (zabezpiecza komunikację z frontendem)
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

        // Warstwa geometrii (statyczna siatka połączeń i perony)
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

        // Warstwa ruchu taboru na żywo – spięta ze standardem GeoJSON pod obiekty na mapie
        route("/api/trams") {
            get("/live") {
                // Pobieramy dane przetworzone na standard geodezyjny FeatureCollection [LON, LAT]
                val geoJsonTrams = tramLiveService.getTramsAsGeoJson()

                // Wypluwamy gotową paczkę obiektów przestrzennych do frontendu
                call.respondText(geoJsonTrams, ContentType.Application.Json)
            }
        } // <-- Tutaj brakowało tej klamry domykającej trasę!
    }
}