package com.example.volyna_tram

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.http.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

// Prosta klasa reprezentująca nasz model danych tramwaju
data class Tramwaj(val linia: String, var trasa: String, var status: String)

fun Application.module() {

    // NASZA BAZA NA GOŁYM CIELE - żyje w pamięci RAM serwera
    val tramwajeBaza = mutableListOf(
        Tramwaj("13", "Cmentarz Wolski - Kawęczyńska-Bazylika", "W trasie"),
        Tramwaj("26", "Metro Młociny - Wiatraczna", "Opóźnienie na Targowej"),
        Tramwaj("4", "Żerań Wschodni - Wyścigi", "Płynnie")
    )

    routing {

        // Strona główna z ładnym panelem
        get("/") {
            println("\n[PRAGA-LOG] 🚋 Ktoś wszedł na panel główny")
            val htmlContent = """
                <html>
                    <head><title>Ctor REST Panel</title></head>
                    <body style="background: #121212; color: #fff; font-family: sans-serif; padding: 30px;">
                        <h1>Ctor REST API v2.0 🚋</h1>
                        <p>Symulator bezpiecznie odpalony w pamięci podręcznej.</p>
                        <p>Zasób bazy dostępny pod: <a href="/api/trams" style="color: #00ff66;">/api/trams</a></p>
                    </body>
                </html>
            """.trimIndent()
            call.respondText(htmlContent, ContentType.Text.Html)
        }

        // ==========================================
        // CZĘŚĆ REST - OPERACJE NA ZASOBIE /api/trams
        // ==========================================
        route("/api/trams") {

            // 1. GET - Pobierz wszystkie tramwaje
            get {
                println("\n[REST - GET] 📊 Ktoś pobiera listę wszystkich tramwajów")

                // Ręcznie mapujemy obiekty na format JSON (na gołym ciele)
                val json = tramwajeBaza.joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") {
                    """  {"linia": "${it.linia}", "trasa": "${it.trasa}", "status": "${it.status}"}"""
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // 2. POST - Dodaj nowy tramwaj do bazy (np. wyjazd z zajezdni)
            // Testując to, musimy przesłać parametry w adresie: ?linia=6&trasa=Praga-Gocławek&status=Płynnie
            post {
                println("\n[REST - POST] ➕ Próba dodania nowego składu do bazy!")
                val params = call.request.queryParameters
                val nowaLinia = params["linia"]
                val nowaTrasa = params["trasa"] ?: "Nieznana"
                val nowyStatus = params["status"] ?: "W zajezdni"

                if (nowaLinia != null) {
                    val nowyTramwaj = Tramwaj(nowaLinia, nowaTrasa, nowyStatus)
                    tramwajeBaza.add(nowyTramwaj)
                    println("[REST - POST] SUCCESS: Dodano linię $nowaLinia do bazy RAM!")
                    call.respondText("{\"message\": \"Dodano pomyślnie linię $nowaLinia\"}", ContentType.Application.Json, HttpStatusCode.Created)
                } else {
                    println("[REST - POST] FAILED: Brak podanego numeru linii!")
                    call.respondText("{\"error\": \"Brak parametru 'linia'\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }

            // Operacje na konkretnym tramwaju po jego numerze linii, np. /api/trams/26
            route("/{linia}") {

                // 3. GET - Pobierz tylko jeden konkretny tramwaj
                get {
                    val numer = call.parameters["linia"]
                    println("\n[REST - GET] 📍 Sprawdzanie szczegółów dla linii: $numer")
                    val tramwaj = tramwajeBaza.find { it.linia == numer }

                    if (tramwaj != null) {
                        val json = """{"linia": "${tramwaj.linia}", "trasa": "${tramwaj.trasa}", "status": "${tramwaj.status}"}"""
                        call.respondText(json, ContentType.Application.Json)
                    } else {
                        call.respondText("{\"error\": \"Nie znaleziono linii $numer\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                    }
                }

                // 4. PATCH - Aktualizacja statusu/pozycji istniejącego tramwaju
                // Wywołanie: /api/trams/26?status=Zatrzymanie ruchu na Nowowiejskiej
// 4. PATCH - Elastyczna modyfikacja statusu i/lub trasy istniejącego tramwaju
// Wywołanie: /api/trams/26?status=Korek&trasa=Metro Młociny - Rondo Żaba
                patch {
                    val numer = call.parameters["linia"]
                    println("\n[REST - PATCH] 🛠️ Modyfikacja danych linii: $numer")

                    val params = call.request.queryParameters
                    val nowyStatus = params["status"]
                    val nowaTrasa = params["trasa"]

                    val tramwaj = tramwajeBaza.find { it.linia == numer }

                    if (tramwaj != null) {
                        var cosZmieniono = false

                        // Jeśli podano nowy status, to go aktualizujemy
                        if (nowyStatus != null) {
                            tramwaj.status = nowyStatus
                            println("[REST - PATCH] -> Zmieniono status na: $nowyStatus")
                            cosZmieniono = true
                        }

                        // Jeśli podano nową trasę, to ją aktualizujemy
                        if (nowaTrasa != null) {
                            tramwaj.trasa = nowaTrasa
                            println("[REST - PATCH] -> Zmieniono trasę na: $nowaTrasa")
                            cosZmieniono = true
                        }

                        if (cosZmieniono) {
                            println("[REST - PATCH] SUCCESS: Dane linii $numer zaktualizowane w pamięci RAM.")
                            call.respondText(
                                "{\"message\": \"Zaktualizowano linię $numer\", \"trasa\": \"${tramwaj.trasa}\", \"status\": \"${tramwaj.status}\"}",
                                ContentType.Application.Json
                            )
                        } else {
                            println("[REST - PATCH] FAILED: Nie podano żadnych parametrów do zmiany (ani status, ani trasa).")
                            call.respondText("{\"error\": \"Brak parametrów 'status' lub 'trasa'\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                        }
                    } else {
                        println("[REST - PATCH] FAILED: Nie znaleziono linii $numer")
                        call.respondText("{\"error\": \"Nie znaleziono linii $numer\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                    }
                }

                // 5. DELETE - Usunięcie tramwaju z bazy (zjazd do zajezdni)
                delete {
                    val numer = call.parameters["linia"]
                    println("\n[REST - DELETE] ❌ Usuwanie z ruchu linii: $numer")
                    val usunięto = tramwajeBaza.removeIf { it.linia == numer }

                    if (usunięto) {
                        println("[REST - DELETE] SUCCESS: Linia $numer zjechała z trasy do zajezdni.")
                        call.respondText("{\"message\": \"Linia $numer usunięta z systemu\"}", ContentType.Application.Json)
                    } else {
                        call.respondText("{\"error\": \"Nie ma takiej linii w systemie\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}