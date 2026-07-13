package com.example.volyna_tram.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.example.volyna_tram.domain.model.TramElement
import kotlin.math.roundToInt

@Composable
fun TramMap(
    baseElements: List<TramElement>,
    platformElements: List<TramElement>,
    liveTrams: List<com.example.volyna_tram.domain.model.Tram>,
    showPlatforms: Boolean,
    isFirstLoad: Boolean, // 🚀 Nowy parametr z App.kt!
    modifier: Modifier = Modifier
) {
    val allPoints = baseElements.flatMap { element ->
        when (element) {
            is TramElement.Track -> element.points
            is TramElement.Stop -> listOf(Pair(element.lat, element.lon))
            is TramElement.Platform -> emptyList()
        }
    }

    if (allPoints.isEmpty()) return

    val minLat = allPoints.minOf { it.first }
    val maxLat = allPoints.maxOf { it.first }
    val minLon = allPoints.minOf { it.second }
    val maxLon = allPoints.maxOf { it.second }

    // Wyjściowy stabilny zoom i przesunięcie
    var scale by remember { mutableStateOf(0.01f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val latRange = maxLat - minLat
    val lonRange = maxLon - minLon

    val virtualSize = 100000f
    val aspect = (latRange / lonRange)
    val virtualWidth = virtualSize
    val virtualHeight = virtualSize * aspect.toFloat()

    fun project(lat: Double, lon: Double): Offset {
        val x = ((lon - minLon) / lonRange) * virtualWidth
        val y = ((maxLat - lat) / latRange) * virtualHeight
        return Offset(x.toFloat(), y.toFloat())
    }

    val lodThreshold = 0.15f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            // 🚀 POTĘŻNY, JEDYNY BLOK DOTYKU (Pinch-to-zoom + Pan pod kciuk)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    // Obcinamy zoom do bezpiecznych wartości dla skali 100k
                    val newScale = (scale * zoom).coerceIn(0.002f, 0.5f)

                    if (oldScale != newScale) {
                        val scaleRatio = newScale / oldScale
                        // Magia matematyczna: zoom celuje dokładnie w środek między Twoimi palcami (centroid)
                        offset = centroid - (centroid - offset) * scaleRatio
                    }
                    // Przesunięcie działa płynnie niezależnie od stopnia przybliżenia
                    offset += pan
                    scale = newScale
                }
            }
            // 🚀 ODDZIELNY BLOK DLA MYSZKI (Desktop)
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
                            val newScale = (scale * zoomFactor).coerceIn(0.002f, 0.5f)

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
        // WARSTWA 1: INFRASTRUKTURA
        Canvas(modifier = Modifier.fillMaxSize()) {
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
                        drawPath(
                            path = path,
                            color = Color(0xFF1A365D),
                            style = Stroke(width = 3f / scale)
                        )
                    }
                }

                // 2. PERONY
                if (showPlatforms && scale > lodThreshold) {
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
                            drawPath(path = polyPath, color = Color(0xFFE2E8F0))
                            drawPath(path = polyPath, color = Color(0xFF475569), style = Stroke(width = 1.2f / scale))
                        }
                    }
                }

                // 3. PRZYSTANKI
                if (scale <= lodThreshold) {
                    baseElements.filterIsInstance<TramElement.Stop>().forEach { stop ->
                        val stopOffset = project(stop.lat, stop.lon)
                        drawCircle(color = Color(0xFFE53E3E), radius = 5f / scale, center = stopOffset)
                        drawCircle(color = Color.White, radius = 5f / scale, center = stopOffset, style = Stroke(width = 1.2f / scale))
                    }
                }
            }
        }

        // WARSTWA 2: TRAMWAJE
        liveTrams.forEach { tram ->
            key(tram.id) {
                AnimatedTramMarker(
                    tram = tram,
                    project = ::project,
                    globalScale = scale,
                    globalOffset = offset,
                    isFirstLoad = isFirstLoad // 🚀 Przekazujemy stan pierwszego ładowania
                )
            }
        }
    }
}

@Composable
fun AnimatedTramMarker(
    tram: com.example.volyna_tram.domain.model.Tram,
    project: (Double, Double) -> Offset,
    globalScale: Float,
    globalOffset: Offset,
    isFirstLoad: Boolean // 🚀 Odbieramy flagę
) {
    // 🧠 Jeśli to pierwsze wejście, snap() odcina animację z punktu (0,0) i natychmiast osadza wóz
    val animLat by animateFloatAsState(
        targetValue = tram.lat.toFloat(),
        animationSpec = if (isFirstLoad) snap() else tween(durationMillis = 10000, easing = LinearEasing)
    )
    val animLon by animateFloatAsState(
        targetValue = tram.lon.toFloat(),
        animationSpec = if (isFirstLoad) snap() else tween(durationMillis = 10000, easing = LinearEasing)
    )

    val virtualOffset = project(animLat.toDouble(), animLon.toDouble())
    val screenX = (virtualOffset.x * globalScale) + globalOffset.x
    val screenY = (virtualOffset.y * globalScale) + globalOffset.y

    val screenTramRadius = 7f
    val screenStroke = 1.5f

    // 🚀 Optymalizacja: rysujemy kropkę jako mały Box przesunięty o piksele.
    // Dzięki temu ponowne przerysowanie (Recomposition) dotyczy tylko tego mikro-Canvasu, a nie całej mapy!
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
    ) {
        drawCircle(
            color = Color(0xFFFFB300),
            radius = screenTramRadius,
            center = Offset.Zero
        )
        drawCircle(
            color = Color(0xFF1A365D),
            radius = screenTramRadius,
            center = Offset.Zero,
            style = Stroke(width = screenStroke)
        )
    }
}