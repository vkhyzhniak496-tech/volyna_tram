package com.example.volyna_tram

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.http.*

import com.example.volyna_tram.repository.NetworkRepository
import com.example.volyna_tram.service.TramLiveService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {

    val tramLiveService = TramLiveService()
    tramLiveService.startLiveTracking(this)

    val networkRepository = NetworkRepository

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

        // Warstwa geometrii (serwowana błyskawicznie z RAM)
        route("/api/network") {
            get("/map") {
                val geoJson = networkRepository.getBaseMapGeoJson()
                if (geoJson.isNotEmpty()) {
                    call.respondText(geoJson, ContentType.Application.Json)
                } else {
                    call.respondText("{\"error\": \"Plik grafu nie znaleziony w RAM\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            get("/map/platforms") {
                val platformsJson = networkRepository.getPlatformsGeoJson()
                if (platformsJson.isNotEmpty()) {
                    call.respondText(platformsJson, ContentType.Application.Json)
                } else {
                    call.respondText("{\"error\": \"Plik peronów nie został znaleziony w RAM\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            get("/version") {
                call.respondText("""{"version": ${networkRepository.networkVersion}}""", ContentType.Application.Json)
            }
        }

        // Warstwa ruchu taboru live
        route("/api/trams") {
            get("/live") {
                val geoJsonTrams = tramLiveService.getTramsAsGeoJson()
                call.respondText(geoJsonTrams, ContentType.Application.Json)
            }
        }
    }
}