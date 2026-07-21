package com.example.volyna_tram.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
    onTogglePlatforms: () -> Unit,
    modifier: Modifier = Modifier
) {
    val boundingBox = remember(baseElements) { calculateBoundingBox(baseElements) } ?: return

    var scale by remember { mutableStateOf(0.01f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var showSpeedLayer by remember { mutableStateOf(false) }

    val projection = remember(boundingBox) { MapProjection(boundingBox) }
    val lodThreshold = 0.15f

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        // 🚀 ULTRA ZOOM: Zwiększony limit z 20.0f do 150.0f dla oglądania rozstawu torów!
                        val newScale = (scale * zoom).coerceIn(0.001f, 15.0f)

                        val targetOffset = if (oldScale != newScale) {
                            centroid - (centroid - offset) * (newScale / oldScale)
                        } else {
                            offset
                        }
                        scale = newScale
                        offset = targetOffset + pan
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val scrollDelta = event.changes.first().scrollDelta.y
                                val zoomFactor = if (scrollDelta < 0) 1.1f else 0.9f

                                val centroid = event.changes.first().position
                                val oldScale = scale
                                val newScale = (scale * zoomFactor).coerceIn(0.001f, 15.0f)

                                offset = centroid - (centroid - offset) * (newScale / oldScale)
                                scale = newScale
                            }
                        }
                    }
                }
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
                showSpeedLayer = showSpeedLayer,
                project = projection::project
            )
        }

        // PIONOWY PASEK PRZYCISKÓW W PRAWYM DOLNYM ROGU
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            ElevatedFilterChip(
                selected = showPlatforms,
                onClick = onTogglePlatforms,
                label = {
                    Text(
                        text = if (showPlatforms) "Perony: ON" else "Perony: OFF",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF475569),
                    selectedLabelColor = Color.White
                )
            )

            ElevatedFilterChip(
                selected = showSpeedLayer,
                onClick = { showSpeedLayer = !showSpeedLayer },
                label = {
                    Text(
                        text = if (showSpeedLayer) "Prędkość: ON" else "Prędkość: OFF",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF2E7D32),
                    selectedLabelColor = Color.White
                )
            )
        }
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
            // 🚀 Tory skalują się proporcjonalnie do powiększenia (zabezpieczenie przed rozmyciem)
            val strokeWidth = (if (track.maxSpeed <= 20.0) 1.5f else 3.0f) / (scale.coerceAtLeast(0.005f))
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
    showSpeedLayer: Boolean,
    project: (Double, Double) -> Offset
) {
    liveTrams.forEach { tram ->
        key(tram.id) {
            AnimatedTramMarker(
                tram = tram,
                project = project,
                globalScale = scale,
                globalOffset = offset,
                isFirstLoad = isFirstLoad,
                showSpeedLayer = showSpeedLayer
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
    isFirstLoad: Boolean,
    showSpeedLayer: Boolean
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

    val tramColor = if (showSpeedLayer) {
        when {
            tram.speed < 3.0 -> Color(0xFFE53E3E)
            tram.speed < 20.0 -> Color(0xFFFFB300)
            else -> Color(0xFF2E7D32)
        }
    } else {
        Color(0xFFFFB300)
    }

    // Dynamiczna czcionka zależna od poziomu przybliżenia
    val fontFloatSize = (10f * (globalScale * 10f)).coerceIn(8f, 16f)
    val dynamicFontSize = fontFloatSize.sp

    val textMeasurer = rememberTextMeasurer()
    val labelText = if (showSpeedLayer) "${tram.line} (${tram.speed.roundToInt()} km/h)" else tram.line

    val textLayoutResult = remember(labelText, dynamicFontSize) {
        textMeasurer.measure(
            text = labelText,
            style = TextStyle(
                color = Color.White,
                fontSize = dynamicFontSize,
                fontWeight = FontWeight.Bold
            )
        )
    }

    // Promień kropki rośnie wraz z przybliżeniem, ale z rozsądnymi granicami
    val screenTramRadius = (7f * (globalScale * 5f)).coerceIn(5f, 22f)
    val screenStroke = 2f

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
    ) {
        drawCircle(
            color = tramColor,
            radius = screenTramRadius,
            center = Offset.Zero
        )
        drawCircle(
            color = Color(0xFF1A365D),
            radius = screenTramRadius,
            center = Offset.Zero,
            style = Stroke(width = screenStroke)
        )

        if (showSpeedLayer || globalScale > 0.01f) {
            val pad = (4f * (globalScale * 5f)).coerceIn(3f, 10f)
            val rectWidth = textLayoutResult.size.width + (pad * 2f)
            val rectHeight = textLayoutResult.size.height + (pad * 2f)

            val badgeOffset = Offset(
                x = -rectWidth / 2f,
                y = -screenTramRadius - rectHeight - 4f
            )

            drawRoundRect(
                color = Color(0xEE1A365D),
                topLeft = badgeOffset,
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )

            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    x = badgeOffset.x + pad,
                    y = badgeOffset.y + pad
                )
            )
        }
    }
}