package com.example.volyna_tram.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class OverpassService(private val httpClient: HttpClient) {

    private val overpassUrl = "https://overpass-api.de/api/interpreter"

    /**
     * Słowo 'suspend' zamiast błędnego 'async' naprawia wszystkie błędy kompilatora!
     */
    suspend fun fetchWarsawTramNetwork(): String? {
        val query = """
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
            val response: HttpResponse = httpClient.post(overpassUrl) {
                setBody(query)
                contentType(ContentType.Application.FormUrlEncoded)
            }
            if (response.status == HttpStatusCode.OK) {
                val osmJson = response.bodyAsText()
                println("[OVERPASS] ✅ Pomyślnie pobrano dane z OpenStreetMap!")
                osmJson
            } else {
                println("[OVERPASS] ❌ Błąd Overpass API: ${response.status}")
                null
            }
        } catch (e: Exception) {
            println("[OVERPASS] ❌ Błąd pobierania z Overpass: ${e.message}")
            null
        }
    }
}