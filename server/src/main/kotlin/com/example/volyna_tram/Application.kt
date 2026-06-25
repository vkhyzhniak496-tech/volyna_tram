package com.example.volyna_tram

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

// 1. TEGO BRAKOWAŁO - silnik Netty musi wiedzieć, co ma odpalić!
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

// 2. Tutaj konfigurujesz całą resztę i logi
fun Application.module() {
    routing {
        // Główna strona
        get("/") {
            println("\n[PRAGA-LOG] 🚋 Ktoś właśnie wszedł na stację główną Ctora!")

            val htmlContent = """
                <html>
                    <head>
                        <title>Ctor - Tram Server</title>
                        <style>
                            body { background-color: #121212; color: #ffffff; font-family: sans-serif; padding: 40px; }
                            h1 { color: #00ff66; border-bottom: 2px solid #00ff66; padding-bottom: 10px; }
                            .status { background: #1e1e1e; padding: 15px; border-radius: 5px; margin-top: 20px; }
                            a { color: #00ff66; text-decoration: none; font-weight: bold; }
                            a:hover { text-decoration: underline; }
                        </style>
                    </head>
                    <body>
                        <h1>Ctor Centrala Nadawcza v1.0 🚋</h1>
                        <p>Witamy na węźle przesiadkowym Praga. Serwer działa stabilnie.</p>
                        <div class="status">
                            <p><strong>Status bazy:</strong> ONLINE</p>
                            <p><strong>Sprawdź aktywne linie:</strong> <a href="/api/trams">/api/trams</a></p>
                        </div>
                    </body>
                </html>
            """.trimIndent()

            call.respondText(htmlContent, ContentType.Text.Html)
        }

        // Endpoint z danymi tramwajów
        get("/api/trams") {
            println("\n[PRAGA-LOG] 📊 Żądanie danych o liniach tramwajowych!")

            val jsonResponse = """
                [
                    {"linia": "13", "trasa": "Cmentarz Wolski - Kawęczyńska-Bazylika", "status": "W trasie"},
                    {"linia": "26", "trasa": "Metro Młociny - Wiatraczna", "status": "Opóźnienie na Targowej"},
                    {"linia": "4", "trasa": "Żerań Wschodni - Wyścigi", "status": "Płynnie"}
                ]
            """.trimIndent()

            call.respondText(jsonResponse, ContentType.Application.Json)
        }
    }
}