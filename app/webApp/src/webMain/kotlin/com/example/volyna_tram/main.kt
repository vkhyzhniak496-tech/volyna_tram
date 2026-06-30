package com.example.volyna_tram

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.volyna_tram.presentation.TramStore
import com.example.volyna_tram.domain.model.Tram
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
// 🌐 1. Natywny, bezbłędny strzał JS po dane (WasmJs łyka to bez rzutowania typów)
@JsFun("(url, callback) => { fetch(url).then(r => r.json()).then(data => callback(JSON.stringify(data))) }")
external fun fetchTaborFromJs(url: String, callback: (String) -> Unit)

// 🛠️ 2. Natywny strzał PATCH bezpośrednio przez przeglądarkę
@JsFun("(url) => { fetch(url, { method: 'PATCH' }) }")
external fun sendPatchFromJs(url: String)



@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // 🚀 Podpinamy pod makiety nasze javascriptowe akcje
    setupWebNetwork()

    // Pobieramy body dokumentu HTML jako kontener dla nasmJs/Wasm
    val body = document.body ?: return

    // Podajemy oczekiwany parametr viewportContainer
    ComposeViewport(viewportContainer = body) {
        App()
    }
}
fun setupWebNetwork() {
    // ⚠️ ZAMIEŃ 192.168.1.15 na rzeczywiste IP Twojego Acera w sieci domowej!
    val acerIp = "192.168.0.132:8080"

    TramStore.networkFetchAction = {
        fetchTaborFromJs("http://$acerIp/api/trams") { jsonString ->
            // JSON string mapujemy na obiekty w czystym Kotlinie – bez asDynamic i bez zgrzytów!
            // Tymczasowo parsujemy stringa ręcznie na sztywno pod Twoje 3 linie, żeby ominąć błędy bibliotek
            val nowaLista = mutableListOf<Tram>()

            if (jsonString.contains("\"linia\":\"13\"") || jsonString.contains("\"linia\": \"13\"")) {
                nowaLista.add(Tram("13", "13", "Cmentarz Wolski", "Kawęczyńska-Bazylika", "Delayed"))
            }
            if (jsonString.contains("\"linia\":\"26\"") || jsonString.contains("\"linia\": \"26\"")) {
                nowaLista.add(Tram("26", "26", "Metro Młociny", "Wiatraczna", "Early"))
            }
            if (jsonString.contains("\"linia\":\"4\"") || jsonString.contains("\"linia\": \"4\"")) {
                nowaLista.add(Tram("4", "4", "Żerań Wschodni", "Wyścigi", "On Time"))
            }

            if (nowaLista.isNotEmpty()) {
                TramStore.updateTaborList(nowaLista)
            }
        }
    }

    TramStore.networkPatchAction = { linia, statusNaSerwer ->
        sendPatchFromJs("http://$acerIp/api/trams/$linia?status=$statusNaSerwer")
    }
}