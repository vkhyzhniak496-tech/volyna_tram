package com.example.volyna_tram.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.example.volyna_tram.domain.model.TramElement
import com.example.volyna_tram.domain.model.TramFeature

@Composable
fun TramMap(
    baseElements: List<TramElement>,
    platformElements: List<TramElement>,
    liveTrams: List<TramFeature>,
    showPlatforms: Boolean,
    modifier: Modifier = Modifier
) {
    // Układ odniesienia bazuje wyłącznie na stałych torach i przystankach
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

        withTransform({
            translate(left = offset.x, top = offset.y)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {

            // 1. RYSUJEMY TORY
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

            // 2. RYSUJEMY PERONY (Jeśli warstwa włączona i jesteśmy blisko)
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

            // 3. RYSUJEMY PRZYSTANKI (Tylko na oddaleniu)
            if (scale <= lodThreshold) {
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

            // 4. RYSUJEMY TRAMWAJE NA ŻYWO (Zawsze widoczne na wierzchu mapy!)
            liveTrams.forEach { tram ->
                val tramOffset = project(tram.geometry.lat, tram.geometry.lon)

                val screenTramRadius = 7f
                val virtualTramRadius = screenTramRadius / scale

                val screenStroke = 1.5f
                val virtualStroke = screenStroke / scale

                // Warszawskie, żółto-pomarańczowe wnętrze
                drawCircle(
                    color = Color(0xFFFFB300),
                    radius = virtualTramRadius,
                    center = tramOffset
                )

                // Ciemna obwódka dla odcięcia od podkładu
                drawCircle(
                    color = Color(0xFF1A365D),
                    radius = virtualTramRadius,
                    center = tramOffset,
                    style = Stroke(width = virtualStroke)
                )
            }
        }
    }
}