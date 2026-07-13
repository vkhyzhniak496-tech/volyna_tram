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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        var isFirstLoad by remember { mutableStateOf(true) }
        // 🚀 Podpinamy się pod nasze Jedyne Źródło Prawdy w architekturze (O(1) Map)
        val taborMap by TramStore.taborMap.collectAsState()

        val client = remember { HttpClient() }

        // IP Twojego serwera na Acerze
        val baseUrl = "http://192.168.0.132:8080"
// 1. Definicje stanów błędów
        var infrastructureError by remember { mutableStateOf<String?>(null) }
        var liveDataError by remember { mutableStateOf<String?>(null) }

        // 2. Ładowanie bazy sieci (tory i przystanki)
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
                // Resetujemy ewentualny błąd infrastruktury
                infrastructureError = null
            } catch (e: Exception) {
                e.printStackTrace()
                // Pokazujemy błąd ładowania infrastruktury na ekranie startowym
                infrastructureError = "Nie można pobrać infrastruktury. Serwer jest obecnie niedostępny."
            }
        }

        // 3. Leniwe ładowanie peronów po kliknięciu przycisku
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
                    // Pomyślne pobranie peronów może zresetować błąd danych na mapie
                    liveDataError = null
                } catch (e: Exception) {
                    println("Błąd ładowania peronów: ${e.message}")
                    e.printStackTrace()
                    // Jeśli perony padną, informujemy o tym użytkownika na mapie
                    liveDataError = "Błąd pobierania peronów. Sprawdź połączenie z serwerem."
                }
            }
        }

        // 4. Pobieranie taboru LIVE w pętli (Co 10 sekund)
        LaunchedEffect(Unit) {
            val jsonDecoder = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            while (true) {
                try {
                    val response = client.get("$baseUrl/api/trams/live").bodyAsText()
                    val data = jsonDecoder.decodeFromString<GeoJsonTramResponse>(response)

                    val domainTrams = data.features.mapNotNull { it.toDomain() }
                    TramStore.updateTaborList(domainTrams)

                    // Połączenie z taborem wróciło do normy -> gasimy błąd live na mapie
                    liveDataError = null

                } catch (e: Exception) {
                    println("Błąd pobierania taboru live: ${e.message}")
                    // Gdy tabor leży, pokazujemy to na czerwonym pasku nad mapą
                    liveDataError = "Brak połączenia z serwerem. Oczekuję na dane o tramwajach..."
                }
                kotlinx.coroutines.delay(10000)
            }
        }

        LaunchedEffect(taborMap) {
            if (taborMap.isNotEmpty() && isFirstLoad) {
                kotlinx.coroutines.delay(200)
                isFirstLoad = false
            }
        }

        // 5. STRUKTURA UI
        if (tramElements.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 🚀 Renderujemy mapę
                TramMap(
                    baseElements = tramElements,
                    platformElements = if (showPlatforms) platformElements else emptyList(),
                    liveTrams = taborMap.values.toList(),
                    showPlatforms = showPlatforms,
                    isFirstLoad = isFirstLoad,
                    modifier = Modifier.fillMaxSize()
                )

                // 🚀 Jeśli tabor live / perony rzucą błędem, pokazujemy dyskretny, czerwony pasek na górze mapy
                if (liveDataError != null) {
                    Text(
                        text = liveDataError!!,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Color.Red.copy(alpha = 0.8f))
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // Przycisk peronów
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
            // 🚀 EKRAN STARTOWY / ŁADOWANIA (Gdy nie ma jeszcze danych sieci bazowej)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (infrastructureError != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "Błąd połączenia z bazą",
                            color = Color.Red,
                            textAlign = TextAlign.Center
                        )

                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = infrastructureError!!,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Czysty, wyśrodkowany stan ładowania na starcie
                    Text(
                        text = "Ładowanie sieci tramwajowej z wozowni...",
                        textAlign = TextAlign.Center
                    )
                }

            }

        }
    }
        }