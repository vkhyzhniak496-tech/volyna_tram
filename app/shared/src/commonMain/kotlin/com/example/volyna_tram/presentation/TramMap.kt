package com.example.volyna_tram.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.volyna_tram.domain.model.TramElement

@Composable
fun TramMap(elements: List<TramElement>, modifier: Modifier = Modifier) {
    // 1. Wyciągamy absolutnie wszystkie punkty, żeby znaleźć ekstrema geograficzne sieci
    val allPoints = elements.flatMap { element ->
        when (element) {
            is TramElement.Track -> element.points
            is TramElement.Stop -> listOf(Pair(element.lat, element.lon))
        }
    }

    if (allPoints.isEmpty()) return

    // Znajdujemy bounding box (skrajne współrzędne dla Warszawy w Twoim pliku)
    val minLat = allPoints.minOf { it.first }
    val maxLat = allPoints.maxOf { it.first }
    val minLon = allPoints.minOf { it.second }
    val maxLon = allPoints.maxOf { it.second }

    val latRange = maxLat - minLat
    val lonRange = maxLon - minLon

    // 2. Czysty, biały Canvas zajmujący całą dostępną przestrzeń
    Canvas(modifier = modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        val width = size.width
        val height = size.height
        val padding = 60f // bezpieczny margines od krawędzi okna

        // Funkcja rzutująca: GPS (Lat, Lon) -> Układ ekranowy (X, Y)
        // Oś Y na ekranie rośnie w dół, dlatego odwracamy Lat: (maxLat - lat)
        fun project(lat: Double, lon: Double): Offset {
            val x = padding + ((lon - minLon) / lonRange) * (width - 2 * padding)
            val y = padding + ((maxLat - lat) / latRange) * (height - 2 * padding)
            return Offset(x.toFloat(), y.toFloat())
        }

        // 3. NAJPIERW RYSUJEMY TORY (żeby kropki przystanków były na wierzchu)
        elements.filterIsInstance<TramElement.Track>().forEach { track ->
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
                    color = Color(0xFF1A365D), // Klasyczny, głęboki granat transportowy
                    style = Stroke(width = 4f)  // Grubość szyn na ekranie
                )
            }
        }

        // 4. RYSUJEMY PRZYSTANKI
        elements.filterIsInstance<TramElement.Stop>().forEach { stop ->
            val stopOffset = project(stop.lat, stop.lon)
            drawCircle(
                color = Color(0xFFE53E3E), // Wyrazista czerwień TW
                radius = 6f,
                center = stopOffset
            )
        }
    }
}