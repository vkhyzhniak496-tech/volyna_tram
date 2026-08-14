package com.example.volyna_tram.presentation.canvas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.volyna_tram.domain.model.Tram
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock

@Composable
fun TramMarkersCanvas(
    liveTrams: List<Tram>,
    selectedTramId: String?,
    scale: Float,
    offset: Offset,
    isFirstLoad: Boolean,
    showSpeedLayer: Boolean,
    project: (Double, Double) -> Offset
) {
    val animatedTrams = liveTrams.map { tram ->
        key(tram.id) {
            rememberAnimatedTramPosition(
                tram = tram,
                isFirstLoad = isFirstLoad,
                project = project
            )
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize()) {
        withTransform({
            translate(left = offset.x, top = offset.y)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            animatedTrams.forEach { (tram, mapPos) ->
                val isSelected = (tram.id == selectedTramId)

                drawTramVehicle(
                    tram = tram,
                    center = mapPos,
                    scale = scale,
                    isSelected = isSelected,
                    showSpeedLayer = showSpeedLayer,
                    textMeasurer = textMeasurer
                )
            }
        }
    }
}

/**
 * Płynna animacja z zabezpieczeniem przed przelotami po wybudzeniu karty.
 */
@Composable
private fun rememberAnimatedTramPosition(
    tram: Tram,
    isFirstLoad: Boolean,
    project: (Double, Double) -> Offset
): Pair<Tram, Offset> {
    val targetLat = tram.lat.toFloat()
    val targetLon = tram.lon.toFloat()

    val animLat = remember(tram.id) { Animatable(targetLat) }
    val animLon = remember(tram.id) { Animatable(targetLon) }

    LaunchedEffect(targetLat, targetLon) {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val timeDiffSec = (currentTime - tram.timestamp) / 1000L

        // Odległość w stopniach (~0.003 stopnia to ok. 300 metrów w Warszawie)
        val dLat = abs(animLat.value - targetLat)
        val dLon = abs(animLon.value - targetLon)
        val maxRealisticJump = 0.0035f

        // Warunki natychmiastowego przeskoku (SNAP):
        // 1. Pierwsze załadowanie
        // 2. Opóźnienie pakietu > 14 sekund
        // 3. Pozycja zmieniła się o więcej niż 300m (powrót na kartę po uśpieniu)
        val shouldSnap = isFirstLoad || timeDiffSec > 14 || dLat > maxRealisticJump || dLon > maxRealisticJump

        if (shouldSnap) {
            animLat.snapTo(targetLat)
            animLon.snapTo(targetLon)
        } else {
            launch {
                animLat.animateTo(
                    targetValue = targetLat,
                    animationSpec = tween(durationMillis = 10000, easing = LinearEasing)
                )
            }
            launch {
                animLon.animateTo(
                    targetValue = targetLon,
                    animationSpec = tween(durationMillis = 10000, easing = LinearEasing)
                )
            }
        }
    }

    val mapPos = remember(animLat.value, animLon.value) {
        project(animLat.value.toDouble(), animLon.value.toDouble())
    }

    return tram to mapPos
}

private fun DrawScope.drawTramVehicle(
    tram: Tram,
    center: Offset,
    scale: Float,
    isSelected: Boolean,
    showSpeedLayer: Boolean,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val safeScale = scale.coerceAtLeast(0.002f)
    val baseRadius = 8.5f / safeScale
    val ringStroke = 1.8f / safeScale

    val tramColor = if (showSpeedLayer) {
        when {
            tram.speed < 3.0 -> Color(0xFFE53E3E)
            tram.speed < 20.0 -> Color(0xFFF59E0B)
            else -> Color(0xFF10B981)
        }
    } else {
        Color(0xFFFFB300) // Domyślny żółty
    }

    if (isSelected) {
        drawCircle(
            color = Color(0xFFFFD700),
            radius = baseRadius * 1.55f,
            center = center,
            style = Stroke(width = ringStroke * 2.2f)
        )
    }

    drawCircle(
        color = tramColor,
        radius = baseRadius,
        center = center
    )

    drawCircle(
        color = Color(0xFF0F172A),
        radius = baseRadius,
        center = center,
        style = Stroke(width = ringStroke)
    )

    if (scale > 0.008f) {
        val fontSize = 11.sp
        val label = if (showSpeedLayer) "${tram.line} (${tram.speed.roundToInt()} km/h)" else tram.line

        val textLayout = textMeasurer.measure(
            text = label,
            style = TextStyle(
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            )
        )

        val textWidthWorld = textLayout.size.width / scale
        val textHeightWorld = textLayout.size.height / scale
        val padWorld = 3.5f / scale

        val badgeTopLeft = Offset(
            x = center.x - (textWidthWorld / 2f) - padWorld,
            y = center.y - baseRadius - textHeightWorld - (4f / scale)
        )

        drawRoundRect(
            color = Color(0xEE0F172A),
            topLeft = badgeTopLeft,
            size = Size(textWidthWorld + padWorld * 2f, textHeightWorld + padWorld * 2f),
            cornerRadius = CornerRadius(4f / scale, 4f / scale)
        )

        withTransform({
            translate(badgeTopLeft.x + padWorld, badgeTopLeft.y + padWorld)
            scale(1f / scale, 1f / scale, Offset.Zero)
        }) {
            drawText(textLayout)
        }
    }
}