package com.example.volyna_tram.presentation.canvas

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.example.volyna_tram.domain.model.Tram
import com.example.volyna_tram.domain.model.TramElement
import com.example.volyna_tram.presentation.tile.TileCanvasLayer
import io.ktor.client.HttpClient
import kotlin.math.roundToInt

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

    // 🎯 Stan klikniętego tramwaju
    var selectedTramId by remember { mutableStateOf<String?>(null) }

    // 🎯 STAN ZAZNACZONYCH LINII (Multi-select filtr)
    var selectedLines by remember { mutableStateOf<Set<String>>(emptySet()) }

    val availableLines = remember(liveTrams) {
        liveTrams.map { it.line.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
    }

    val filteredTrams = remember(liveTrams, selectedLines) {
        if (selectedLines.isEmpty()) {
            liveTrams
        } else {
            liveTrams.filter { it.line.trim() in selectedLines }
        }
    }

    val projection = remember(boundingBox) { MapProjection(boundingBox) }
    val lodThreshold = 0.15f

    // Dane aktualnie zaznaczonego tramwaju
    val selectedTram = remember(filteredTrams, selectedTramId) {
        filteredTrams.find { it.id == selectedTramId }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // PŁÓTNO MAPY + GESTY KAMERY I KLIKANIA
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                // 1. Detekcja kliknięcia w tramwaj (Hit-Testing)
                .pointerInput(filteredTrams, scale, offset) {
                    detectTapGestures { tapOffset ->
                        val clicked = findClickedTram(
                            screenTap = tapOffset,
                            trams = filteredTrams,
                            scale = scale,
                            offset = offset,
                            project = projection::project,
                            maxRadiusPx = 35f
                        )
                        selectedTramId = clicked?.id
                    }
                }
                // 2. Pan & Zoom gesty
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
                // 3. Scroll myszy (Desktop/Web)
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
            // WARSTWA 0: KAFELKI TŁA
            TileCanvasLayer(
                scale = scale,
                offset = offset,
                project = projection::project,
                client = httpClient
            )

            // WARSTWA 1: INFRASTRUKTURA (TORY I PERONY NA CANVASIE)
            InfrastructureCanvas(
                baseElements = baseElements,
                platformElements = platformElements,
                showPlatforms = showPlatforms,
                scale = scale,
                offset = offset,
                lodThreshold = lodThreshold,
                project = projection::project
            )

            // WARSTWA 2: TRAMWAJE LIVE (PŁYNNA ANIMACJA 10S + CANVAS GPU)
            TramMarkersCanvas(
                liveTrams = filteredTrams,
                selectedTramId = selectedTramId,
                scale = scale,
                offset = offset,
                isFirstLoad = isFirstLoad,
                showSpeedLayer = showSpeedLayer,
                project = projection::project
            )
        }

        // 🔝 3. FILTR LINII NA GÓRZE
        LineFilterBar(
            availableLines = availableLines,
            selectedLines = selectedLines,
            visibleTramsCount = filteredTrams.size,
            onLineToggled = { line ->
                selectedLines = if (line in selectedLines) selectedLines - line else selectedLines + line
            },
            onClearAll = { selectedLines = emptySet() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 🔘 4. PRZYCISKI WARSTW W PRAWYM DOLNYM ROGU
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

        // 🚋 5. OKNO INFORMACYJNE KLIKNIĘTEGO TRAMWAJU (LEWY DOLNY RÓG)
        AnimatedVisibility(
            visible = selectedTram != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            selectedTram?.let { tram ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), // Slate 800
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Żółty badge z numerem linii
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFB300),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = tram.line,
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Linia ${tram.line}  •  Brygada ${tram.brigade}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isMoving = tram.speed >= 3.5
                                val statusColor = if (isMoving) Color(0xFF10B981) else Color(0xFFF59E0B)
                                val statusText = if (isMoving) "${tram.speed.roundToInt()} km/h" else "Postój / Przystanek"

                                Surface(
                                    shape = CircleShape,
                                    color = statusColor,
                                    modifier = Modifier.size(7.dp)
                                ) {}

                                Spacer(modifier = Modifier.width(6.dp))

                                Text(
                                    text = statusText,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Przycisk zamknięcia okna [✕]
                        IconButton(
                            onClick = { selectedTramId = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text(
                                text = "✕",
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Funkcja pomocnicza do sprawdzania kliknięcia w tramwaj
private fun findClickedTram(
    screenTap: Offset,
    trams: List<Tram>,
    scale: Float,
    offset: Offset,
    project: (Double, Double) -> Offset,
    maxRadiusPx: Float = 35f
): Tram? {
    return trams.minByOrNull { tram ->
        val mapPos = project(tram.lat, tram.lon)
        val screenX = (mapPos.x * scale) + offset.x
        val screenY = (mapPos.y * scale) + offset.y
        val dx = screenX - screenTap.x
        val dy = screenY - screenTap.y
        dx * dx + dy * dy
    }?.takeIf { tram ->
        val mapPos = project(tram.lat, tram.lon)
        val screenX = (mapPos.x * scale) + offset.x
        val screenY = (mapPos.y * scale) + offset.y
        val dx = screenX - screenTap.x
        val dy = screenY - screenTap.y
        (dx * dx + dy * dy) <= (maxRadiusPx * maxRadiusPx)
    }
}