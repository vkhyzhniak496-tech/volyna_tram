package com.example.volyna_tram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.volyna_tram.data.GeoJsonTramResponse
import com.example.volyna_tram.domain.model.TramElement
import com.example.volyna_tram.data.parseNetworkGeoJson
import com.example.volyna_tram.data.toDomain
import com.example.volyna_tram.presentation.TramMap
import com.example.volyna_tram.presentation.TramStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

@Composable
fun App() {
    MaterialTheme {
        TramScreen()
    }
}

@Composable
fun TramScreen() {
    var tramElements by remember { mutableStateOf<List<TramElement>>(emptyList()) }
    var showPlatforms by remember { mutableStateOf(false) }
    var platformElements by remember { mutableStateOf<List<TramElement>>(emptyList()) }
    var isFirstLoad by remember { mutableStateOf(true) }

    val taborMap by TramStore.taborMap.collectAsState()
    val client = remember { HttpClient() }
    val baseUrl = "http://192.168.0.132:8080"

    var infrastructureError by remember { mutableStateOf<String?>(null) }
    var liveDataError by remember { mutableStateOf<String?>(null) }

    // 1. Pobieranie tła/sieci bazowej
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
                    (kotlin.math.sqrt(dLat * dLat + dLon * dLon) * 111000) < 15.0
                }
                if (!isDuplicate) cleanStops.add(stop)
            }

            tramElements = tracks + cleanStops
            infrastructureError = null
        } catch (e: Exception) {
            e.printStackTrace()
            infrastructureError = "Nie można pobrać infrastruktury. Serwer jest niedostępny."
        }
    }

    // 2. Leniwe ładowanie peronów
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
                liveDataError = null
            } catch (e: Exception) {
                e.printStackTrace()
                liveDataError = "Błąd pobierania peronów."
            }
        }
    }

    // 3. Pętla pobierania sygnału LIVE co 10s
    LaunchedEffect(Unit) {
        val jsonDecoder = Json { ignoreUnknownKeys = true }
        while (true) {
            try {
                val response = client.get("$baseUrl/api/trams/live").bodyAsText()
                val data = jsonDecoder.decodeFromString<GeoJsonTramResponse>(response)

                val domainTrams = data.features.mapNotNull { it.toDomain() }
                TramStore.updateTaborList(domainTrams)
                liveDataError = null
            } catch (e: Exception) {
                liveDataError = "Brak połączenia z serwerem. Oczekuję na dane..."
            }
            delay(10000)
        }
    }

    // Odcięcie pierwszego ładowania
    LaunchedEffect(taborMap) {
        if (taborMap.isNotEmpty() && isFirstLoad) {
            delay(200)
            isFirstLoad = false
        }
    }

    // 4. RENDEROWANIE WIDOKU
    if (tramElements.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            TramMap(
                baseElements = tramElements,
                platformElements = if (showPlatforms) platformElements else emptyList(),
                liveTrams = taborMap.values.toList(),
                showPlatforms = showPlatforms,
                isFirstLoad = isFirstLoad,
                modifier = Modifier.fillMaxSize()
            )

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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (infrastructureError != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(text = "Błąd połączenia z bazą", color = Color.Red)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = infrastructureError!!, textAlign = TextAlign.Center)
                }
            } else {
                Text(
                    text = "Ładowanie sieci tramwajowej z wozowni...",
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}