package com.example.volyna_tram.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.volyna_tram.presentation.TramStore
import com.example.volyna_tram.domain.model.Tram
import kotlinx.browser.document
import com.example.volyna_tram.App

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(url, callback) => { " +
        "  fetch(url)" +
        "    .then(r => r.json())" +
        "    .then(data => {" +
        "      if (data && data.features) {" +
        "        let serialized = data.features.map(f => {" +
        "          let line = f.properties.line || '??';" +
        "          let brigade = f.properties.brigade || '??';" +
        "          let lon = f.geometry.coordinates[0] || 0.0;" +
        "          let lat = f.geometry.coordinates[1] || 0.0;" +
        "          return line + ',' + brigade + ',' + lat + ',' + lon;" +
        "        }).join(';');" +
        "        callback(serialized);" +
        "      }" +
        "    }).catch(e => console.error(e));" +
        "}")
external fun fetchTaborFromJs(url: String, callback: (String) -> Unit)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    setupWebNetwork()
    val body = document.body ?: return
    ComposeViewport(viewportContainer = body) { App() }
}

fun setupWebNetwork() {
    val acerIp = "192.168.0.132:8080"

    TramStore.networkFetchAction = {
        // Uderzamy pod właściwy endpoint czasu rzeczywistego
        fetchTaborFromJs("http://$acerIp/api/trams/live") { rawData ->
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

                    // 🚀 Tworzymy unikalne ID składu i pakujemy w nowy, czysty format!
                    val id = "${line}_${brigade}"

                    nowaLista.add(
                        Tram(
                            id = id,
                            line = line,
                            brigade = brigade,
                            lat = lat,
                            lon = lon,
                            state = "W ruchu"
                        )
                    )
                }
            }

            if (nowaLista.isNotEmpty()) {
                TramStore.updateTaborList(nowaLista)
            }
        }
    }
}