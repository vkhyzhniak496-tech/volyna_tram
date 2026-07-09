package com.example.volyna_tram

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.volyna_tram.domain.model.TramElement
import com.example.volyna_tram.domain.model.GeoJsonTramResponse
import com.example.volyna_tram.domain.model.parseNetworkGeoJson
import com.example.volyna_tram.domain.model.toDomain
import com.example.volyna_tram.presentation.TramMap
import com.example.volyna_tram.presentation.TramStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

@Composable
fun App() {
    TramScreen()
}

@Composable
fun TramScreen() {
    MaterialTheme {
        var tramElements by remember { mutableStateOf<List<TramElement>>(emptyList()) }
        var showPlatforms by remember { mutableStateOf(false) }
        var platformElements by remember { mutableStateOf<List<TramElement>>(emptyList()) }

        // 🚀 Podpinamy się pod nasze Jedyne Źródło Prawdy w architekturze (O(1) Map)
        val taborMap by TramStore.taborMap.collectAsState()

        val client = remember { HttpClient() }

        // IP Twojego serwera na Acerze
        val baseUrl = "http://192.168.0.132:8080"

        // 1. Ładowanie bazy sieci (tory i przystanki)
        LaunchedEffect(Unit) {
            try {
                val response = client.get("$baseUrl/api/network/map").bodyAsText()
                val parsedElements = parseNetworkGeoJson(response)

                val tracks = parsedElements.filterIsInstance<TramElement.Track>()
                val rawStops = parsedElements.filterIsInstance<TramElement.Stop>()

                val cleanStops = mutableListOf<TramElement.Stop>()
                for (stop in rawStops) {
                    val isDuplicate = cleanStops.any { existing ->
                        val dLat = existing.lat - stop.lat
                        val dLon = existing.lon - stop.lon
                        val distanceInMeters = kotlin.math.sqrt(dLat * dLat + dLon * dLon) * 111000
                        distanceInMeters < 15.0
                    }
                    if (!isDuplicate) {
                        cleanStops.add(stop)
                    }
                }

                tramElements = tracks + cleanStops
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Leniwe ładowanie peronów po kliknięciu przycisku
        LaunchedEffect(showPlatforms) {
            if (showPlatforms && platformElements.isEmpty()) {
                try {
                    val response = client.get("$baseUrl/api/network/map/platforms").bodyAsText()
                    val parsedPlatforms = parseNetworkGeoJson(response)

                    platformElements = parsedPlatforms.distinctBy { element ->
                        when (element) {
                            is TramElement.Platform -> element.polygonPoints
                            else -> element
                        }
                    }
                } catch (e: Exception) {
                    println("Błąd ładowania peronów: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        // 3. Cykliczne pobieranie pozycji i ładowanie ich bezpośrednio do TramStore
        LaunchedEffect(Unit) {
            val jsonDecoder = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            while (true) {
                try {
                    val response = client.get("$baseUrl/api/trams/live").bodyAsText()
                    val data = jsonDecoder.decodeFromString<GeoJsonTramResponse>(response)

                    // 🚀 Przepuszczamy GeoJSON przez toDomain() i pakujemy czyste obiekty do sklepu!
                    val domainTrams = data.features.mapNotNull { it.toDomain() }
                    TramStore.updateTaborList(domainTrams)

                } catch (e: Exception) {
                    println("Błąd pobierania taboru live: ${e.message}")
                }
                kotlinx.coroutines.delay(10000)
            }
        }

        if (tramElements.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 🚀 Przekazujemy listę wartości z naszej zoptymalizowanej mapy
                TramMap(
                    baseElements = tramElements,
                    platformElements = if (showPlatforms) platformElements else emptyList(),
                    liveTrams = taborMap.values.toList(),
                    showPlatforms = showPlatforms,
                    modifier = Modifier.fillMaxSize()
                )

                Button(
                    onClick = { showPlatforms = !showPlatforms },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                ) {
                    Text(if (showPlatforms) "Ukryj perony" else "Pokaż perony")
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Ładowanie sieci tramwajowej z wozowni...")
            }
        }
    }
}