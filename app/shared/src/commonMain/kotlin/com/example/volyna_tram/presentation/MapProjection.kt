package com.example.volyna_tram.presentation

import androidx.compose.ui.geometry.Offset
import com.example.volyna_tram.domain.model.TramElement

data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

class MapProjection(private val box: BoundingBox) {
    private val latRange = box.maxLat - box.minLat
    private val lonRange = box.maxLon - box.minLon
    private val virtualSize = 100000f
    private val aspect = latRange / lonRange
    private val virtualWidth = virtualSize
    private val virtualHeight = virtualSize * aspect.toFloat()

    fun project(lat: Double, lon: Double): Offset {
        val x = ((lon - box.minLon) / lonRange) * virtualWidth
        val y = ((box.maxLat - lat) / latRange) * virtualHeight
        return Offset(x.toFloat(), y.toFloat())
    }
}

fun calculateBoundingBox(baseElements: List<TramElement>): BoundingBox? {
    val allPoints = baseElements.flatMap { element ->
        when (element) {
            is TramElement.Track -> element.points
            is TramElement.Stop -> listOf(Pair(element.lat, element.lon))
            is TramElement.Platform -> emptyList()
        }
    }
    if (allPoints.isEmpty()) return null

    return BoundingBox(
        minLat = allPoints.minOf { it.first },
        maxLat = allPoints.maxOf { it.first },
        minLon = allPoints.minOf { it.second },
        maxLon = allPoints.maxOf { it.second }
    )
}