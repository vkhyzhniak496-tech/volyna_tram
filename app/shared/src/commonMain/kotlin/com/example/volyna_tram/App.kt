package com.example.volyna_tram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.volyna_tram.domain.model.TramElement
import com.example.volyna_tram.domain.model.parseNetworkGeoJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

// =====================================================================
// 1. GŁÓWNE KOMPONENTY WEJŚCIOWE aplikacji
// =====================================================================
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

        val client = remember { HttpClient() }

        // Ładowanie bazy sieci (tory i przystanki) z Acera
        LaunchedEffect(Unit) {
            try {
                val response = client.get("http://192.168.0.132:8080/api/network/map").bodyAsText()
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

        // Leniwe ładowanie peronów po kliknięciu przycisku
        LaunchedEffect(showPlatforms) {
            if (showPlatforms && platformElements.isEmpty()) {
                try {
                    val response = client.get("http://192.168.0.132:8080/api/network/map/platforms").bodyAsText()
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

        if (tramElements.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 🛠️ ZMIANA: Przekazujemy stałą bazę osobo, a perony osobno!
                TramMap(
                    baseElements = tramElements,
                    platformElements = if (showPlatforms) platformElements else emptyList(),
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

// =====================================================================
// 2. WARSTWA RENDEROWANIA GRAFIKI (CANVAS)
// =====================================================================
@Composable
fun TramMap(
    baseElements: List<TramElement>, // Stały fundament mapy
    platformElements: List<TramElement>, // Dynamiczna warstwa peronów
    showPlatforms: Boolean,
    modifier: Modifier = Modifier
) {
    // 🛠️ UKŁAD ODNIESIENIA: Granice liczymy WYŁĄCZNIE ze stałej bazy torów i krotek!
    val allPoints = baseElements.flatMap { element ->
        when (element) {
            is TramElement.Track -> element.points
            is TramElement.Stop -> listOf(Pair(element.lat, element.lon))
            is TramElement.Platform -> emptyList() // Na wszelki wypadek ignorujemy perony przy skali
        }
    }

    if (allPoints.isEmpty()) return

    val minLat = allPoints.minOf { it.first }
    val maxLat = allPoints.maxOf { it.first }
    val minLon = allPoints.minOf { it.second }
    val maxLon = allPoints.maxOf { it.second }

    val latRange = maxLat - minLat
    val lonRange = maxLon - minLon

    var scale by remember { mutableStateOf(0.05f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.001f, 10f)
                    offset += pan
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.first()
                            val scrollDelta = change.scrollDelta.y
                            val mousePosition = change.position

                            val zoomFactor = if (scrollDelta < 0) 1.1f else 0.9f
                            val oldScale = scale
                            val newScale = (scale * zoomFactor).coerceIn(0.001f, 10f)

                            if (oldScale != newScale) {
                                val scaleRatio = newScale / oldScale
                                offset = mousePosition - (mousePosition - offset) * scaleRatio
                                scale = newScale
                            }
                            change.consume()
                        }
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height

        val virtualSize = 100000f
        val aspect = (latRange / lonRange)
        val virtualWidth = virtualSize
        val virtualHeight = virtualSize * aspect.toFloat()

        fun project(lat: Double, lon: Double): Offset {
            val x = ((lon - minLon) / lonRange) * virtualWidth
            val y = ((maxLat - lat) / latRange) * virtualHeight
            return Offset(x.toFloat(), y.toFloat())
        }

        withTransform({
            translate(left = offset.x, top = offset.y)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {

            // 1. TORY
            baseElements.filterIsInstance<TramElement.Track>().forEach { track ->
                if (track.points.size > 1) {
                    val path = Path().apply {
                        val first = project(track.points[0].first, track.points[0].second)
                        moveTo(first.x, first.y)
                        for (i in 1 until track.points.size) {
                            val next = project(track.points[i].first, track.points[i].second)
                            lineTo(next.x, next.y)
                        }
                    }

                    val screenStrokeWidth = 3f
                    val virtualStrokeWidth = screenStrokeWidth / scale

                    drawPath(
                        path = path,
                        color = Color(0xFF1A365D),
                        style = Stroke(width = virtualStrokeWidth)
                    )
                }
            }

            // 2. PERONY (Rysowane z dedykowanej listy bez wpływu na układ współrzędnych)
            if (showPlatforms) {
                platformElements.filterIsInstance<TramElement.Platform>().forEach { platform ->
                    if (platform.polygonPoints.size > 2) {
                        val polyPath = Path().apply {
                            val first = project(platform.polygonPoints[0].first, platform.polygonPoints[0].second)
                            moveTo(first.x, first.y)
                            for (i in 1 until platform.polygonPoints.size) {
                                val next = project(platform.polygonPoints[i].first, platform.polygonPoints[i].second)
                                lineTo(next.x, next.y)
                            }
                            close()
                        }

                        drawPath(
                            path = polyPath,
                            color = Color(0xFFE2E8F0)
                        )

                        drawPath(
                            path = polyPath,
                            color = Color(0xFF475569),
                            style = Stroke(width = 1.2f / scale)
                        )
                    }
                }
            }

            // 3. KROPKI PRZYSTANKOWE
            baseElements.filterIsInstance<TramElement.Stop>().forEach { stop ->
                val stopOffset = project(stop.lat, stop.lon)

                val screenRadius = 5f
                val virtualRadius = screenRadius / scale

                val screenStroke = 1.2f
                val virtualStroke = screenStroke / scale

                drawCircle(
                    color = Color(0xFFE53E3E),
                    radius = virtualRadius,
                    center = stopOffset
                )

                drawCircle(
                    color = Color.White,
                    radius = virtualRadius,
                    center = stopOffset,
                    style = Stroke(width = virtualStroke)
                )
            }
        }
    }
}