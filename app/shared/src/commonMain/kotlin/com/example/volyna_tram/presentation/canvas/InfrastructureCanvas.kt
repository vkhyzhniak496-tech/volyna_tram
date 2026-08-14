package com.example.volyna_tram.presentation.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.volyna_tram.domain.model.TramElement

@Composable
fun InfrastructureCanvas(
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

