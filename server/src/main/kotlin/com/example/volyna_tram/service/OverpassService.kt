package com.example.volyna_tram.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URLEncoder

class OverpassService(private val httpClient: HttpClient) {

    private val overpassEndpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    suspend fun fetchWarsawTramNetwork(): String? {
        val rawQuery = """
            [out:json][timeout:25][bbox:52.09,20.85,52.37,21.27];
            (
              way["railway"="tram"];
              node["railway"="tram_stop"];
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        val formBody = "data=" + URLEncoder.encode(rawQuery, "UTF-8")

        for (endpoint in overpassEndpoints) {
            try {
                println("[OVERPASS]  Próba pobrania z: $endpoint")

                val response: HttpResponse = httpClient.post(endpoint) {
                    headers {
                        append(HttpHeaders.UserAgent, "VolynaTramApp/1.0 (contact: admin@volynatram.com)")
                        append(HttpHeaders.Accept, "application/json")
                    }
                    setBody(formBody)
                    contentType(ContentType.Application.FormUrlEncoded)
                }

                if (response.status == HttpStatusCode.OK) {
                    val osmJson = response.bodyAsText()
                    if (osmJson.contains("elements")) {
                        println("[OVERPASS]  Sukces! Pobrano ${osmJson.length} znaków z $endpoint")
                        return osmJson
                    } else {
                        println("[OVERPASS] ⚠ Odpowiedź z $endpoint nie zawiera poprawnych danych OSM JSON.")
                    }
                } else {
                    println("[OVERPASS] ⚠ Błąd $endpoint Status: ${response.status}")
                }
            } catch (e: Exception) {
                println("[OVERPASS] ⚠ Wyjątek połączenia z $endpoint: ${e.message}")
            }
        }

        println("[OVERPASS]  Wszystkie endpointy Overpass zawiodły.")
        return null
    }
}