package com.example.volyna_tram.presentation.tile

import kotlin.math.*
import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import org.jetbrains.compose.resources.decodeToImageBitmap

data class TileKey(val z: Int, val x: Int, val y: Int) {
    val northLat: Double get() = tile2lat(y, z)
    val westLon: Double get() = tile2lon(x, z)
    val southLat: Double get() = tile2lat(y + 1, z)
    val eastLon: Double get() = tile2lon(x + 1, z)

    private fun tile2lon(x: Int, z: Int): Double = x.toDouble() / (1 shl z) * 360.0 - 180.0

    private fun tile2lat(y: Int, z: Int): Double {
        val n = PI - 2.0 * PI * y.toDouble() / (1 shl z)
        return atan(sinh(n)) * 180.0 / PI
    }

    companion object {
        fun getTileX(lon: Double, zoom: Int): Int =
            floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt().coerceIn(0, (1 shl zoom) - 1)

        fun getTileY(lat: Double, zoom: Int): Int {
            val latRad = lat * PI / 180.0
            val y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)).toInt()
            return y.coerceIn(0, (1 shl zoom) - 1)
        }
    }
}

class TileRepository(private val client: HttpClient) {
    suspend fun fetchTile(key: TileKey): ImageBitmap? {
        val url = "https://a.basemaps.cartocdn.com/dark_all/${key.z}/${key.x}/${key.y}.png"
        return try {
            val bytes: ByteArray = client.get(url).body()
            val bitmap = bytes.decodeToImageBitmap()
            println("[TILE SUCCESS] Załadowano kafelek: $key")
            bitmap
        } catch (t: Throwable) {
            println("[TILE ERROR] Błąd kafelka $key: ${t.message}")
            null
        }
    }
}