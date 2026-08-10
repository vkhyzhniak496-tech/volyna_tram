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
import com.example.volyna_tram.presentation.tile.TileCanvasLayer
import io.ktor.client.HttpClient

@Composable
fun TramMap(
    baseElements: List<TramElement>,
    platformElements: List<TramElement>,
    liveTrams: List<Tram>,
    showPlatforms: Boolean,
    isFirstLoad: Boolean,
    onTogglePlatforms: () -> Unit,
    modifier: Modifier = Modifier,
    httpClient: HttpClient = remember { HttpClient() }
) {
    val boundingBox = remember(baseElements) { calculateBoundingBox(baseElements) } ?: return

    var scale by remember { mutableStateOf(0.01f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showSpeedLayer by remember { mutableStateOf(false) }

    // 🎯 STAN ZAZNACZONYCH LINII (Multi-select)
    var selectedLines by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 1. Wyciągamy i sortujemy numerycznie unikalne linie z taboru
    val availableLines = remember(liveTrams) {
        liveTrams.map { it.line.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
    }

    // 2. Przefiltrowane tramwaje pod przekazanie na płótno
    val filteredTrams = remember(liveTrams, selectedLines) {
        if (selectedLines.isEmpty()) {
            liveTrams
        } else {
            liveTrams.filter { it.line.trim() in selectedLines }
        }
    }

    val projection = remember(boundingBox) { MapProjection(boundingBox) }
    val lodThreshold = 0.15f

    Box(modifier = modifier.fillMaxSize()) {
        // PŁÓTNO MAPY
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
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
            TileCanvasLayer(
                scale = scale,
                offset = offset,
                project = projection::project,
                client = httpClient // Przekaż HttpClient
            )
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

            // WARSTWA 2: TRAMWAJE LIVE (Przekazujemy PRZEFILTROWANE tramwaje!)
            TramMarkersLayer(
                liveTrams = filteredTrams,
                scale = scale,
                offset = offset,
                isFirstLoad = isFirstLoad,
                showSpeedLayer = showSpeedLayer,
                project = projection::project
            )
        }

        // 🔝 3. INTELIGENTNY FILTR LINII NA GÓRZE EKRANU
        LineFilterBar(
            availableLines = availableLines,
            selectedLines = selectedLines,
            visibleTramsCount = filteredTrams.size,
            onLineToggled = { line ->
                selectedLines = if (line in selectedLines) {
                    selectedLines - line
                } else {
                    selectedLines + line
                }
            },
            onClearAll = { selectedLines = emptySet() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 🔘 4. PIONOWY PASEK PRZYCISKÓW WARSTW W PRAWYM DOLNYM ROGU
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