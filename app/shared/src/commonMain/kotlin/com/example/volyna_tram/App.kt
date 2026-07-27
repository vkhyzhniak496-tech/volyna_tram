package com.example.volyna_tram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.volyna_tram.config.NetworkConfig
import com.example.volyna_tram.data.tram.TramRepository
import com.example.volyna_tram.data.network.parseNetworkGeoJson
import com.example.volyna_tram.data.tram.parseTramGeoJson
import com.example.volyna_tram.domain.model.TramElement
import com.example.volyna_tram.presentation.TramMap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay

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

    // 🚀 Obserwujemy stan taboru z nowego TramRepository w warstwie data
    val taborMap by TramRepository.taborMap.collectAsState()
    val client = remember { HttpClient() }
    val baseUrl = NetworkConfig.baseUrl

    var infrastructureError by remember { mutableStateOf<String?>(null) }
    var liveDataError by remember { mutableStateOf<String?>(null) }

    // 1. Pobieranie bazy infrastruktury (tory + przefiltrowane przystanki)
    LaunchedEffect(Unit) {
        try {
            val response = client.get("$baseUrl/api/network/map").bodyAsText()
            tramElements = fetchAndCleanInfrastructure(response)
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
                platformElements = parseNetworkGeoJson(response)
                    .filterIsInstance<TramElement.Platform>()
                    .distinctBy { it.polygonPoints }
                liveDataError = null
            } catch (e: Exception) {
                e.printStackTrace()
                liveDataError = "Błąd pobierania peronów."
            }
        }
    }

    // 3. Pętla pobierania sygnału LIVE co 10s (z wykorzystaniem parseTramGeoJson)
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val response = client.get("$baseUrl/api/trams/live").bodyAsText()
                val domainTrams = parseTramGeoJson(response)
                TramRepository.updateTabor(domainTrams)
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

    // 4. RENDEROWANIE WIDOKU (Wydzielone czyste stany)
    if (tramElements.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            TramMap(
                baseElements = tramElements,
                platformElements = if (showPlatforms) platformElements else emptyList(),
                liveTrams = taborMap.values.toList(),
                showPlatforms = showPlatforms,
                isFirstLoad = isFirstLoad,
                onTogglePlatforms = { showPlatforms = !showPlatforms },
                modifier = Modifier.fillMaxSize()
            )

            if (liveDataError != null) {
                ErrorBanner(message = liveDataError!!)
            }
        }
    } else if (infrastructureError != null) {
        InfrastructureErrorView(message = infrastructureError!!)
    } else {
        LoadingView()
    }
}

// --- POMOCNICZA LOGIKA GEOMETRII I WIDOKI DEDYKOWANE ---

private fun fetchAndCleanInfrastructure(jsonString: String): List<TramElement> {
    val parsedElements = parseNetworkGeoJson(jsonString)
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

    return tracks + cleanStops
}

@Composable
private fun ErrorBanner(message: String) {
    Text(
        text = message,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Red.copy(alpha = 0.8f))
            .padding(16.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun InfrastructureErrorView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(text = "Błąd połączenia z bazą", color = Color.Red)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Ładowanie sieci tramwajowej z wozowni...",
            textAlign = TextAlign.Center
        )
    }
}