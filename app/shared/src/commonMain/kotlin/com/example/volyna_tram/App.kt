package com.example.volyna_tram

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.example.volyna_tram.domain.model.TramElement
import com.example.volyna_tram.domain.model.parseNetworkGeoJson
import com.example.volyna_tram.presentation.TramMap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

@Preview
@Composable
fun App() {
    TramScreen()
}

@Composable
fun TramScreen() {
    MaterialTheme {
        // 1. Definiujemy stan dla naszych wektorów
        var tramElements by remember { mutableStateOf<List<TramElement>>(emptyList()) }

        // 2. Multiplatformowy klient sieciowy Ktor
        val client = remember { HttpClient() }

        // 3. Asynchroniczny strzał do wozowni na Acerze
        LaunchedEffect(Unit) {
            try {
                val response = client.get("http://192.168.0.132:8080/api/network/map").bodyAsText()
                tramElements = parseNetworkGeoJson(response)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 4. Sterowanie widokiem w zależności od stanu ładowania danych
        if (tramElements.isNotEmpty()) {
            TramMap(elements = tramElements)
        } else {
            Text("Ładowanie krwiobiegu stolicy z wozowni...")
        }
    }
}