package com.example.volyna_tram.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.volyna_tram.App
import com.example.volyna_tram.data.tram.TramRepository
import com.example.volyna_tram.domain.model.Tram
import kotlinx.browser.document
import kotlin.time.Clock

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(callback) => { " +
        "  fetch('config.json')" +
        "    .then(r => r.json())" +
        "    .then(config => {" +
        "      let ip = config.server_ip || '127.0.0.1:8080';" +
        "      return fetch('http://' + ip + '/api/trams/live');" +
        "    })" +
        "    .then(r => r.json())" +
        "    .then(data => {" +
        "      if (data && data.features) {" +
        "        let serialized = data.features.map(f => {" +
        "          let line = f.properties.line || '??';" +
        "          let brigade = f.properties.brigade || '??';" +
        "          let lon = f.geometry.coordinates[0] || 0.0;" +
        "          let lat = f.geometry.coordinates[1] || 0.0;" +
        "          let speed = f.properties.speed || 0.0;" +
        "          let timestamp = f.properties.timestamp || 0;" +
        "          return line + ',' + brigade + ',' + lat + ',' + lon + ',' + speed + ',' + timestamp;" +
        "        }).join(';');" +
        "        callback(serialized);" +
        "      }" +
        "    }).catch(e => console.error('Błąd pobierania config.json lub danych:', e));" +
        "}")
external fun fetchTaborFromJs(callback: (String) -> Unit)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    setupWebNetwork()
    val body = document.body ?: return
    ComposeViewport(viewportContainer = body) { App() }
}

fun setupWebNetwork() {
    // Wywołanie JS, który sam wczyta adres z config.json
    fetchTaborFromJs { rawData ->
        if (rawData.isEmpty()) return@fetchTaborFromJs

        val nowaLista = mutableListOf<Tram>()
        val tramwajeStringi = rawData.split(";")

        for (tramString in tramwajeStringi) {
            val pola = tramString.split(",")
            if (pola.size >= 4) {
                val line = pola[0].trim()
                val brigade = pola[1].trim()
                val lat = pola[2].toDoubleOrNull() ?: 0.0
                val lon = pola[3].toDoubleOrNull() ?: 0.0
                val speed = pola.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                val timestamp = pola.getOrNull(5)?.toLongOrNull()
                    ?.takeIf { it > 0 } ?: Clock.System.now().toEpochMilliseconds()

                val id = "${line}_${brigade}"

                nowaLista.add(
                    Tram(
                        id = id,
                        line = line,
                        brigade = brigade,
                        lat = lat,
                        lon = lon,
                        speed = speed,
                        timestamp = timestamp
                    )
                )
            }
        }

        if (nowaLista.isNotEmpty()) {
            TramRepository.updateTabor(nowaLista)
        }
    }
}