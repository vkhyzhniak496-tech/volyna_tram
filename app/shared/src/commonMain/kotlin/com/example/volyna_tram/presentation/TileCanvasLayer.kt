package com.example.volyna_tram.presentation.tile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.ktor.client.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun TileCanvasLayer(
    scale: Float,
    offset: Offset,
    project: (Double, Double) -> Offset,
    client: HttpClient
) {
    val repository = remember(client) { TileRepository(client) }
    val tileCache = remember { mutableStateMapOf<TileKey, ImageBitmap>() }

    val zoom = 13

    val minTileX = TileKey.getTileX(20.80, zoom)
    val maxTileX = TileKey.getTileX(21.25, zoom)
    val minTileY = TileKey.getTileY(52.38, zoom)
    val maxTileY = TileKey.getTileY(52.05, zoom)

    val visibleTiles = remember(zoom) {
        val list = mutableListOf<TileKey>()
        val startY = min(minTileY, maxTileY)
        val endY = max(minTileY, maxTileY)

        for (x in minTileX..maxTileX) {
            for (y in startY..endY) {
                list.add(TileKey(zoom, x, y))
            }
        }
        list
    }

    LaunchedEffect(visibleTiles) {
        for (tileKey in visibleTiles) {
            if (!tileCache.containsKey(tileKey)) {
                launch {
                    val bitmap = repository.fetchTile(tileKey)
                    if (bitmap != null) {
                        tileCache[tileKey] = bitmap
                    }
                }
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (tileKey in visibleTiles) {
            val topLeftUnscaled = project(tileKey.northLat, tileKey.westLon)
            val bottomRightUnscaled = project(tileKey.southLat, tileKey.eastLon)

            val p1X = topLeftUnscaled.x * scale + offset.x
            val p1Y = topLeftUnscaled.y * scale + offset.y
            val p2X = bottomRightUnscaled.x * scale + offset.x
            val p2Y = bottomRightUnscaled.y * scale + offset.y

            val drawX = min(p1X, p2X)
            val drawY = min(p1Y, p2Y)
            val tileWidth = abs(p2X - p1X)
            val tileHeight = abs(p2Y - p1Y)

            if (tileWidth > 0f && tileHeight > 0f) {
                val bitmap = tileCache[tileKey]
                if (bitmap != null) {
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset(drawX.toInt(), drawY.toInt()),
                        dstSize = IntSize(max(1, tileWidth.toInt()), max(1, tileHeight.toInt())),
                        filterQuality = FilterQuality.Medium
                    )
                } else {
                    // Podgląd siatki tła przed wczytaniem obrazka
                    drawRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(drawX, drawY),
                        size = Size(tileWidth, tileHeight)
                    )
                }
            }
        }
    }
}