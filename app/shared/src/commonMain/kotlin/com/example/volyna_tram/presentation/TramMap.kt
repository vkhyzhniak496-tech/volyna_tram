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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.example.volyna_tram.domain.model.Tram
import com.example.volyna_tram.domain.model.TramElement
import kotlin.math.roundToInt
import kotlin.time.Clock

@Composable
fun TramMap(
    baseElements: List<TramElement>,
    platformElements: List<TramElement>,
    liveTrams: List<Tram>,
    showPlatforms: Boolean,
    isFirstLoad: Boolean,
    modifier: Modifier = Modifier
) {
    val boundingBox = remember(baseElements) { calculateBoundingBox(baseElements) } ?: return

    var scale by remember { mutableStateOf(0.01f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val projection = remember(boundingBox) { MapProjection(boundingBox) }
    val lodThreshold = 0.15f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .handleGestures(
                scale = scale,
                offset = offset,
                onTransform = { newScale, newOffset ->
                    scale = newScale
                    offset = newOffset
                }
            )
    ) {
        // WARSTWA 1: INFRASTRUKTURA
        InfrastructureCanvas(
            baseElements = baseElements,
            platformElements = platformElements,
            showPlatforms = showPlatforms,
            scale = scale,
            offset = offset,
            lodThreshold = lodThreshold,
            project = projection::project
        )

        // WARSTWA 2: TRAMWAJE LIVE
        TramMarkersLayer(
            liveTrams = liveTrams,
            scale = scale,
            offset = offset,
            isFirstLoad = isFirstLoad,
            project = projection::project
        )
    }
}

// --- KLASY POMOCNICZE DLA PROJEKCJI ---
data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

class MapProjection(private val box: BoundingBox) {
    private val latRange = box.maxLat - box.minLat
    private val lonRange = box.maxLon - box.minLon
    private val virtualSize = 100000f
    private val aspect = latRange / lonRange
    private val virtualWidth = virtualSize
    private val virtualHeight = virtualSize * aspect.toFloat()

    fun project(lat: Double, lon: Double): Offset {
        val x = ((lon - box.minLon) / lonRange) * virtualWidth
        val y = ((box.maxLat - lat) / latRange) * virtualHeight
        return Offset(x.toFloat(), y.toFloat())
    }
}

private fun calculateBoundingBox(baseElements: List<TramElement>): BoundingBox? {
    val allPoints = baseElements.flatMap { element ->
        when (element) {
            is TramElement.Track -> element.points
            is TramElement.Stop -> listOf(Pair(element.lat, element.lon))
            is TramElement.Platform -> emptyList()
        }
    }
    if (allPoints.isEmpty()) return null

    return BoundingBox(
        minLat = allPoints.minOf { it.first },
        maxLat = allPoints.maxOf { it.first },
        minLon = allPoints.minOf { it.second },
        maxLon = allPoints.maxOf { it.second }
    )
}

// --- OBSŁUGA GESTÓW ---
private fun Modifier.handleGestures(
    scale: Float,
    offset: Offset,
    onTransform: (Float, Offset) -> Unit
): Modifier = this
    .pointerInput(Unit) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            val oldScale = scale
            val newScale = (scale * zoom).coerceIn(0.002f, 0.5f)
            var newOffset = offset + pan

            if (oldScale != newScale) {
                val scaleRatio = newScale / oldScale
                newOffset = centroid - (centroid - newOffset) * scaleRatio
            }
            onTransform(newScale, newOffset)
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
                    val newScale = (scale * zoomFactor).coerceIn(0.002f, 0.5f)
                    var newOffset = offset

                    if (oldScale != newScale) {
                        val scaleRatio = newScale / oldScale
                        newOffset = mousePosition - (mousePosition - offset) * scaleRatio
                    }
                    onTransform(newScale, newOffset)
                    change.consume()
                }
            }
        }
    }

// --- RYSOWANIE INFRASTRUKTURY ---
@Composable
private fun InfrastructureCanvas(
    baseElements: List<TramElement>,
    platformElements: List<TramElement>,
    showPlatforms: Boolean,
    scale: Float,
    offset: Offset,
    lodThreshold: Float,
    project: (Double, Double) -> Offset
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        withTransform({
            translate(left = offset.x, top = offset.y)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            drawTracks(baseElements, scale, project)

            if (showPlatforms && scale > lodThreshold) {
                drawPlatforms(platformElements, scale, project)
            }

            if (scale <= lodThreshold) {
                drawStops(baseElements, scale, project)
            }
        }
    }
}

private fun DrawScope.drawTracks(
    baseElements: List<TramElement>,
    scale: Float,
    project: (Double, Double) -> Offset
) {
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
            // 🎨 RÓŻNICOWANIE WYGLĄDU TORÓW W ZALEŻNOŚCI OD MAXSPEED Z OVERPASSA
            val strokeWidth = when {
                track.maxSpeed <= 20.0 -> 1.5f / scale  // Zwrotnice / ciasne łuki
                track.maxSpeed >= 50.0 -> 3.5f / scale  // Główne trasy
                else -> 2.5f / scale
            }
            val trackColor = if (track.maxSpeed <= 20.0) Color(0xFF64748B) else Color(0xFF1A365D)

            drawPath(
                path = path,
                color = trackColor,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

private fun DrawScope.drawPlatforms(
    platformElements: List<TramElement>,
    scale: Float,
    project: (Double, Double) -> Offset
) {
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

private fun DrawScope.drawStops(
    baseElements: List<TramElement>,
    scale: Float,
    project: (Double, Double) -> Offset
) {
    baseElements.filterIsInstance<TramElement.Stop>().forEach { stop ->
        val stopOffset = project(stop.lat, stop.lon)
        drawCircle(color = Color(0xFFE53E3E), radius = 5f / scale, center = stopOffset)
        drawCircle(color = Color.White, radius = 5f / scale, center = stopOffset, style = Stroke(width = 1.2f / scale))
    }
}

// --- RYSOWANIE MARKERÓW TRAMWAJÓW LIVE ---
@Composable
private fun TramMarkersLayer(
    liveTrams: List<Tram>,
    scale: Float,
    offset: Offset,
    isFirstLoad: Boolean,
    project: (Double, Double) -> Offset
) {
    liveTrams.forEach { tram ->
        key(tram.id) {
            AnimatedTramMarker(
                tram = tram,
                project = project,
                globalScale = scale,
                globalOffset = offset,
                isFirstLoad = isFirstLoad
            )
        }
    }
}

@Composable
fun AnimatedTramMarker(
    tram: Tram,
    project: (Double, Double) -> Offset,
    globalScale: Float,
    globalOffset: Offset,
    isFirstLoad: Boolean
) {
    val currentTime = Clock.System.now().toEpochMilliseconds()
    val timeDiffSeconds = (currentTime - tram.timestamp) / 1000L
    val useSnap = isFirstLoad || timeDiffSeconds > 15

    val animLat by animateFloatAsState(
        targetValue = tram.lat.toFloat(),
        animationSpec = if (useSnap) snap() else tween(durationMillis = 10000, easing = LinearEasing)
    )
    val animLon by animateFloatAsState(
        targetValue = tram.lon.toFloat(),
        animationSpec = if (useSnap) snap() else tween(durationMillis = 10000, easing = LinearEasing)
    )

    val virtualOffset = project(animLat.toDouble(), animLon.toDouble())
    val screenX = (virtualOffset.x * globalScale) + globalOffset.x
    val screenY = (virtualOffset.y * globalScale) + globalOffset.y

    // 🚀 DYNAMICZNY KOLOR W ZALEŻNOŚCI OD PRĘDKOŚCI W KM/H DOWIEZIONEJ PRZEZ SERWER
    val speedColor = when {
        tram.speed < 3.0 -> Color(0xFFE53E3E)    // Czerwony: Stoi na przystanku / w korku
        tram.speed < 20.0 -> Color(0xFFFFB300)   // Żółty/Pomarańczowy: Wolna jazda / manewr
        else -> Color(0xFF2E7D32)                // Zielony: Płynna jazda
    }

    val textMeasurer = rememberTextMeasurer()
    val labelText = "${tram.line} (${tram.speed.roundToInt()} km/h)"

    val textLayoutResult = remember(labelText) {
        textMeasurer.measure(
            text = labelText,
            style = TextStyle(
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }

    val screenTramRadius = 8f
    val screenStroke = 2f

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
    ) {
        // 1. Kropka tramwaju
        drawCircle(
            color = speedColor,
            radius = screenTramRadius,
            center = Offset.Zero
        )
        drawCircle(
            color = Color(0xFF1A365D),
            radius = screenTramRadius,
            center = Offset.Zero,
            style = Stroke(width = screenStroke)
        )

        // 2. Plakietka informacyjna nad tramwajem [Linia (km/h)]
        if (globalScale > 0.05f) { // Pokazuj plakietki przy większym przybliżeniu (LOD)
            val padding = 6f
            val rectWidth = textLayoutResult.size.width + (padding * 2)
            val rectHeight = textLayoutResult.size.height + (padding * 2)

            val badgeOffset = Offset(
                x = -rectWidth / 2f,
                y = -screenTramRadius - rectHeight - 4f
            )

            // Tło plakietki
            drawRoundRect(
                color = Color(0xCC1A365D),
                topLeft = badgeOffset,
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Tekst na plakietce
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    x = badgeOffset.x + padding,
                    y = badgeOffset.y + padding
                )
            )
        }
    }
}