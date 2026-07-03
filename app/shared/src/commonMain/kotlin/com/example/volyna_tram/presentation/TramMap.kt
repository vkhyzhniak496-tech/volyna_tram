package com.example.volyna_tram.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.example.volyna_tram.domain.model.TramElement

@Composable
fun TramMap(elements: List<TramElement>, modifier: Modifier = Modifier) {
    val allPoints = elements.flatMap { element ->
        when (element) {
            is TramElement.Track -> element.points
            is TramElement.Stop -> listOf(Pair(element.lat, element.lon))
        }
    }

    if (allPoints.isEmpty()) return

    val minLat = allPoints.minOf { it.first }
    val maxLat = allPoints.maxOf { it.first }
    val minLon = allPoints.minOf { it.second }
    val maxLon = allPoints.maxOf { it.second }

    val latRange = maxLat - minLat
    val lonRange = maxLon - minLon

    // 1. Zaczynamy od mniejszej skali bazowej, bo nasz świat wirtualny będzie gigantyczny
    var scale by remember { mutableStateOf(0.05f) } // mniejszy start, żeby zmieścić makietę
    var offset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Rozszerzamy zakres zoomu pod gigantyczną makietę
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

        // 2. TWORZYMY GIGANTYCZNĄ WIRTUALNĄ MAKIETĘ (Szerokość 100 000 pikseli w pamięci)
        val virtualSize = 100000f

        // Obliczamy proporcję Warszawy, żeby nie rozciągać siatki w pionie/poziomie
        val aspect = (latRange / lonRange)
        val virtualWidth = virtualSize
        val virtualHeight = virtualSize * aspect.toFloat()

        // Funkcja rzutująca GPS -> do gigantycznej przestrzeni wirtualnej
        fun project(lat: Double, lon: Double): Offset {
            val x = ((lon - minLon) / lonRange) * virtualWidth
            val y = ((maxLat - lat) / latRange) * virtualHeight
            return Offset(x.toFloat(), y.toFloat())
        }

        withTransform({
            // Przesunięcie i skalowanie naszej gigantycznej makiety
            translate(left = offset.x, top = offset.y)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {

            // 3. RYSUJEMY TORY (ZAŁATANE DZIURY!)
            elements.filterIsInstance<TramElement.Track>().forEach { track ->
                // ZMIANA: Zmieniono z > 2 na > 1, żeby rysować również krótkie, 2-punktowe łączniki rozjazdów
                if (track.points.size > 1) {
                    val path = Path().apply {
                        val first = project(track.points[0].first, track.points[0].second)
                        moveTo(first.x, first.y)
                        for (i in 1 until track.points.size) {
                            val next = project(track.points[i].first, track.points[i].second)
                            lineTo(next.x, next.y)
                        }
                    }

                    // Wyliczamy grubość w świecie wirtualnym tak, aby na ekranie miała zawsze sensowny rozmiar w pikselach
                    val screenStrokeWidth = 3f // Żądana stała grubość szyny na ekranie w pikselach
                    val virtualStrokeWidth = screenStrokeWidth / scale

                    drawPath(
                        path = path,
                        color = Color(0xFF1A365D),
                        style = Stroke(width = virtualStrokeWidth)
                    )
                }
            }

            // 4. RYSUJEMY PRZYSTANKI (DYNAMICZNE I WYRAZISTE)
            elements.filterIsInstance<TramElement.Stop>().forEach { stop ->
                val stopOffset = project(stop.lat, stop.lon)

                // Wyliczamy promień w przestrzeni wirtualnej na bazie pożądanego promienia na ekranie
                val screenRadius = 5f // Bazowy promień kropki w pikselach ekranu
                val virtualRadius = screenRadius / scale

                // Wyliczamy grubość obwódki w przestrzeni wirtualnej
                val screenStroke = 1.2f
                val virtualStroke = screenStroke / scale

                // Rysujemy czerwoną kropkę
                drawCircle(
                    color = Color(0xFFE53E3E),
                    radius = virtualRadius,
                    center = stopOffset
                )

                // Nakładamy białą obwódkę dla idealnego kontrastu na skrzyżowaniach
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