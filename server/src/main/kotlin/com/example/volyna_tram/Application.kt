package com.example.volyna_tram

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.http.*

import io.ktor.client.*
import io.ktor.client.engine.cio.*

import com.example.volyna_tram.model.LiveTram
import com.example.volyna_tram.repository.NetworkRepository
import com.example.volyna_tram.service.OverpassService
import com.example.volyna_tram.service.TramLiveService
import com.example.volyna_tram.utils.GeoJsonFormatter
import io.ktor.server.http.content.staticResources
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {

    val httpClient = HttpClient(CIO)
    val overpassService = OverpassService(httpClient)
    val tramLiveService = TramLiveService()

    // Uruchomienie pętli pobierającej pozycje z ZTM w tle
    tramLiveService.startLiveTracking(this)
    val networkRepository = NetworkRepository

    routing {
        // Obsługa CORS dla przeglądarki (Web / Wasm)
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

        // Serwowanie aplikacji frontendowej (Wasm / HTML)
        staticResources("/", "", index = "index.html")

        // ==========================================
        // 1. Warstwa Statyczna: Infrastruktura i Tory
        // ==========================================
        route("/api/network") {
            get("/map") {
                val geoJson = networkRepository.getBaseMapGeoJson()
                if (geoJson.isNotEmpty()) {
                    call.respondText(geoJson, ContentType.Application.Json)
                } else {
                    call.respondText("""{"error": "Graf sieci nie został jeszcze załadowany"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            post("/refresh") {
                println("[NETWORK] Pobieranie sieci z Overpass API...")
                val rawOsmData = overpassService.fetchWarsawTramNetwork()

                if (rawOsmData != null) {
                    println("[NETWORK] Konwertowanie OSM do GeoJSON...")
                    val formattedGeoJson = GeoJsonFormatter.formatOsmToGeoJson(rawOsmData)
                    networkRepository.updateNetwork(formattedGeoJson)

                    call.respondText(
                        """{"status": "success", "networkVersion": ${networkRepository.networkVersion}}""",
                        ContentType.Application.Json
                    )
                } else {
                    call.respondText("""{"status": "error"}""", ContentType.Application.Json, HttpStatusCode.InternalServerError)
                }
            }

            get("/map/platforms") {
                val platformsJson = networkRepository.getPlatformsGeoJson()
                if (platformsJson.isNotEmpty()) {
                    call.respondText(platformsJson, ContentType.Application.Json)
                } else {
                    call.respondText("""{"error": "Plik peronów nie został znaleziony"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            get("/version") {
                call.respondText("""{"version": ${networkRepository.networkVersion}}""", ContentType.Application.Json)
            }
        }

        // ==========================================
        // 2. Warstwa Dynamiczna: Ruch Taboru Live
        // ==========================================
        route("/api/trams") {

            // A) GeoJSON dla warstwy Canvas (wszystkie pojazdy na mapie)
            get("/live") {
                val geoJsonTrams = tramLiveService.getTramsAsGeoJson()
                call.respondText(geoJsonTrams, ContentType.Application.Json)
            }

            // B) Szczegóły konkretnego tramwaju (dla Follow-Cam i Panelu SIP)
            // Przykład: GET /api/trams/17_03
            get("/{id}") {
                val tramId = call.parameters["id"]
                if (tramId.isNullOrBlank()) {
                    call.respondText("""{"error": "Brak ID tramwaju"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@get
                }

                val tram = tramLiveService.getTramById(tramId)
                if (tram != null) {
                    call.respondText(Json.encodeToString(tram), ContentType.Application.Json)
                } else {
                    call.respondText("""{"error": "Pojazd $tramId nie jest obecnie aktywny"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            // C) Filtrowanie po linii dla kafelków UI (lub pobranie wszystkich jako tablica DTO)
            // Przykłady: GET /api/trams?line=9  albo  GET /api/trams
            get {
                val lineQuery = call.request.queryParameters["line"]
                val trams = if (!lineQuery.isNullOrBlank()) {
                    tramLiveService.getTramsByLine(lineQuery)
                } else {
                    tramLiveService.getAllTrams()
                }

                call.respondText(Json.encodeToString(trams), ContentType.Application.Json)
            }
        }
    }
}