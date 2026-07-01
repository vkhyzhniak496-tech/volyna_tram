package com.example.volyna_tram

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.volyna_tram.presentation.TramStore
import com.example.volyna_tram.domain.model.Tram
import kotlinx.browser.document

// 🌐 Pobieramy czysty JSON i bezpiecznie wyciągamy z niego tablicę płaskich tekstów przez natywny JS
@JsFun("(url, callback) => { " +
        "  fetch(url)" +
        "    .then(r => r.json())" +
        "    .then(data => {" +
        "      let arr = data.map(t => t.linia + '|' + t.trasa + '|' + t.status);" +
        "      callback(arr.join(';'));" +
        "    }).catch(e => console.error(e));" +
        "}")
external fun fetchTaborFromJs(url: String, callback: (String) -> Unit)

@JsFun("(url) => { fetch(url, { method: 'PATCH' }).catch(e => console.error(e)); }")
external fun sendPatchFromJs(url: String)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    setupWebNetwork()
    val body = document.body ?: return
    ComposeViewport(viewportContainer = body) { App() }
}

fun setupWebNetwork() {
    // ⚠️ Twoje tajne IP Acera (już wiemy, że serwer je widzi i odbiera ruch!)
    val acerIp = "192.168.0.132:8080"

    TramStore.networkFetchAction = {
        fetchTaborFromJs("http://$acerIp/api/trams") { rawData ->
            if (rawData.isEmpty()) return@fetchTaborFromJs

            val nowaLista = mutableListOf<Tram>()
            val tramwajeStringi = rawData.split(";")

            for (tramString in tramwajeStringi) {
                // Rozdzielamy unikalnym separatorem, żeby nie gryzło się z przecinkami w nazwach tras!
                val pola = tramString.split("|")
                if (pola.size >= 3) {
                    val linia = pola[0].trim()
                    val trasa = pola[1].trim()
                    val statusPolski = pola[2].trim()

                    val stateConverted = if (statusPolski.contains("Opóźnienie")) "Delayed" else "On Time"
                    val przystanki = trasa.split("-")
                    val start = przystanki.firstOrNull()?.trim() ?: "Zajezdnia"
                    val terminus = przystanki.lastOrNull()?.trim() ?: "Praga"

                    nowaLista.add(Tram(linia, linia, start, terminus, stateConverted))
                }
            }

            if (nowaLista.isNotEmpty()) {
                TramStore.updateTaborList(nowaLista)
            }
        }
    }
}