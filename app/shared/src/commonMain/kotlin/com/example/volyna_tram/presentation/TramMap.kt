package com.example.volyna_tram.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.volyna_tram.domain.model.Tram
import com.example.volyna_tram.domain.model.TramElement

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