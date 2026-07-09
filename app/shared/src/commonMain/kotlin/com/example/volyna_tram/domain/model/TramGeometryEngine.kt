package com.example.volyna_tram.domain.model

object TramGeometryEngine {

    fun findNearestTrack(lat: Double, lon: Double, tracks: List<TramElement.Track>): TramElement.Track? {
        if (tracks.isEmpty()) return null
        var minDistance = Double.MAX_VALUE
        var bestTrack: TramElement.Track? = null

        for (track in tracks) {
            for (point in track.points) {
                val dLat = point.first - lat
                val dLon = point.second - lon
                val dist = dLat * dLat + dLon * dLon
                if (dist < minDistance) {
                    minDistance = dist
                    bestTrack = track
                }
            }
        }
        return bestTrack
    }

    /**
     * 🚀 INTELIGENTNA INTERPOLACJA:
     * Całkowicie oczyszczona z javy i oparta na najprostszych operacjach arytmetycznych.
     */
    fun getPointAlongTrack(
        track: TramElement.Track,
        progress: Float,
        prevLat: Double, prevLon: Double,
        nextLat: Double, nextLon: Double
    ): Pair<Double, Double> {
        if (track.points.isEmpty()) return Pair(0.0, 0.0)
        if (track.points.size == 1) return track.points.first()

        // 1. Wyciągamy pierwszy punkt wektora toru
        val firstTrackPoint = track.points.first()

        // 2. Liczymy kwadraty odległości za pomocą zwykłego, płaskiego mnożenia dx * dx (czysty KMP!)
        val dxPrev = firstTrackPoint.first - prevLat
        val dyPrev = firstTrackPoint.second - prevLon
        val distPrevToStart = (dxPrev * dxPrev) + (dyPrev * dyPrev)

        val dxNext = firstTrackPoint.first - nextLat
        val dyNext = firstTrackPoint.second - nextLon
        val distNextToStart = (dxNext * dxNext) + (dyNext * dyNext)

        // 3. Proste porównanie logiczne liczb Double - kompilator to uwielbia
        val isMovingForward = distNextToStart > distPrevToStart

        val effectiveProgress = if (isMovingForward) {
            progress.coerceIn(0f, 1f).toDouble()
        } else {
            (1f - progress).coerceIn(0f, 1f).toDouble()
        }

        val targetDistance = effectiveProgress * track.totalLength

        // --- Nasz stabilny Binary Search ---
        var low = 0
        var high = track.cumulativeDistances.size - 1
        var insertionPoint = track.cumulativeDistances.size

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midVal = track.cumulativeDistances[mid]

            if (midVal < targetDistance) {
                low = mid + 1
            } else if (midVal > targetDistance) {
                insertionPoint = mid
                high = mid - 1
            } else {
                return track.points[mid]
            }
        }

        if (insertionPoint <= 0) return track.points.first()
        if (insertionPoint >= track.points.size) return track.points.last()

        val p1 = track.points[insertionPoint - 1]
        val p2 = track.points[insertionPoint]

        val d1 = track.cumulativeDistances[insertionPoint - 1]
        val d2 = track.cumulativeDistances[insertionPoint]

        val segmentLength = d2 - d1
        if (segmentLength == 0.0) return p1

        val localProgress = (targetDistance - d1) / segmentLength

        val interpolatedLat = p1.first + localProgress * (p2.first - p1.first)
        val interpolatedLon = p1.second + localProgress * (p2.second - p1.second)

        return Pair(interpolatedLat, interpolatedLon)
    }
}