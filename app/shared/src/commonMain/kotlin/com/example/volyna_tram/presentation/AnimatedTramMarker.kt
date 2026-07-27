package com.example.volyna_tram.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.example.volyna_tram.domain.model.Tram
import kotlin.math.roundToInt
import kotlin.time.Clock

@Composable
fun TramMarkersLayer(
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