package com.example.volyna_tram.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URLEncoder

class OverpassService(private val httpClient: HttpClient) {

    // Lista serwerów Overpass (jeśli pierwszy zgłosi 504, próbuje drugiego)
    private val overpassEndpoints = listOf(
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass-api.de/api/interpreter"
    )

    suspend fun fetchWarsawTramNetwork(): String? {
        // Bounding Box dla obszaru Warszawy: (South, West, North, East)
        // Zapytanie z BBOX wykonuje się w 1 sekundę i nie przeciąża serwera!
        val rawQuery = """
            [out:json][timeout:25];
            (
              way["railway"="tram"](52.09,20.85,52.37,21.27);
              node["railway"="tram_stop"](52.09,20.85,52.37,21.27);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        val formBody = "data=" + URLEncoder.encode(rawQuery, "UTF-8")

        for (endpoint in overpassEndpoints) {
            try {
                println("[OVERPASS] 🚀 Próba pobrania z: $endpoint")
                val response: HttpResponse = httpClient.post(endpoint) {
                    setBody(formBody)
                    contentType(ContentType.Application.FormUrlEncoded)
                }

                if (response.status == HttpStatusCode.OK) {
                    val osmJson = response.bodyAsText()
                    println("[OVERPASS] ✅ Sukces! Pobrano ${osmJson.length} znaków z $endpoint")
                    return osmJson
                } else {
                    println("[OVERPASS] ⚠️ Błąd $endpoint Status: ${response.status}")
                }
            } catch (e: Exception) {
                println("[OVERPASS] ⚠️ Wyjątek połączenia z $endpoint: ${e.message}")
            }
        }

        println("[OVERPASS] ❌ Wszystkie endpointy Overpass zawiodły.")
        return null
    }
}