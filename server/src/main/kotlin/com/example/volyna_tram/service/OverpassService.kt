package com.example.volyna_tram.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URLEncoder

class OverpassService(private val httpClient: HttpClient) {

    private val overpassUrl = "https://overpass-api.de/api/interpreter"

    suspend fun fetchWarsawTramNetwork(): String? {
        val rawQuery = """
            [out:json][timeout:60];
            area["name"="Warszawa"]["admin_level"="8"]->.searchArea;
            (
              way["railway"="tram"](area.searchArea);
              node["railway"="tram_stop"](area.searchArea);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        return try {
            // Overpass oczekuje parametru data=zakodowane_zapytanie
            val formBody = "data=" + URLEncoder.encode(rawQuery, "UTF-8")

            val response: HttpResponse = httpClient.post(overpassUrl) {
                setBody(formBody)
                contentType(ContentType.Application.FormUrlEncoded)
            }

            if (response.status == HttpStatusCode.OK) {
                val osmJson = response.bodyAsText()
                println("[OVERPASS] ✅ Pomyślnie pobrano dane z OpenStreetMap (${osmJson.length} znaków)!")
                osmJson
            } else {
                val errorBody = response.bodyAsText()
                println("[OVERPASS] ❌ Błąd Overpass API Status: ${response.status}")
                println("[OVERPASS] ❌ Odpowiedź serwera: $errorBody")
                null
            }
        } catch (e: Exception) {
            println("[OVERPASS] ❌ Wyjątek podczas połączenia z Overpass: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}