package com.ryda.app.core.sharing

import androidx.compose.ui.geometry.Offset
import com.ryda.app.core.models.GpsPoint
import kotlin.math.*

data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

object RouteSimplifier {

    // Iterative Ramer-Douglas-Peucker (RDP) route simplification
    fun simplifyRoute(points: List<GpsPoint>, epsilon: Double = 0.00005): List<GpsPoint> {
        if (points.size <= 2) return points

        // Pre-decimate very large point datasets (> 4000 points) to speed up RDP
        val inputPoints = if (points.size > 4000) {
            val step = points.size / 2000
            val decimated = ArrayList<GpsPoint>(2005)
            decimated.add(points.first())
            for (i in step until points.size - 1 step step) {
                decimated.add(points[i])
            }
            decimated.add(points.last())
            decimated
        } else {
            points
        }

        val n = inputPoints.size
        if (n <= 2) return inputPoints

        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true

        // Use index stack for iterative RDP (prevents StackOverflowError on 50,000+ points)
        var stack = IntArray((n * 4).coerceAtLeast(64))
        var stackTop = 0
        stack[stackTop++] = 0
        stack[stackTop++] = n - 1

        while (stackTop > 0) {
            val end = stack[--stackTop]
            val start = stack[--stackTop]

            if (end <= start + 1) continue

            var maxDist = 0.0
            var maxIdx = start
            val first = inputPoints[start]
            val last = inputPoints[end]

            for (i in start + 1 until end) {
                val d = perpendicularDistance(inputPoints[i], first, last)
                if (d > maxDist) {
                    maxDist = d
                    maxIdx = i
                }
            }

            if (maxDist > epsilon) {
                keep[maxIdx] = true
                if (stackTop + 4 >= stack.size) {
                    stack = stack.copyOf(stack.size * 2)
                }
                stack[stackTop++] = start
                stack[stackTop++] = maxIdx
                stack[stackTop++] = maxIdx
                stack[stackTop++] = end
            }
        }

        val result = ArrayList<GpsPoint>(n / 4)
        for (i in 0 until n) {
            if (keep[i]) {
                result.add(inputPoints[i])
            }
        }
        return result
    }

    // Extrema-preserving chart downsampling helper (Speed curves, Elevation curves)
    fun <T> downsampleForChart(
        items: List<T>,
        targetCount: Int = 500,
        getValue: (T) -> Double
    ): List<T> {
        if (items.size <= targetCount || items.isEmpty()) return items

        val result = ArrayList<T>(targetCount + 2)
        result.add(items.first())

        val bucketSize = (items.size - 2).toDouble() / (targetCount / 2)
        var currentIdx = 1

        while (currentIdx < items.size - 1) {
            val endIdx = (currentIdx + bucketSize).toInt().coerceAtMost(items.size - 1)
            if (endIdx <= currentIdx) {
                result.add(items[currentIdx])
                currentIdx++
                continue
            }

            var minItem = items[currentIdx]
            var minVal = getValue(minItem)
            var minPos = currentIdx

            var maxItem = items[currentIdx]
            var maxVal = minVal
            var maxPos = currentIdx

            for (i in currentIdx until endIdx) {
                val item = items[i]
                val valI = getValue(item)
                if (valI < minVal) {
                    minVal = valI
                    minItem = item
                    minPos = i
                }
                if (valI > maxVal) {
                    maxVal = valI
                    maxItem = item
                    maxPos = i
                }
            }

            if (minPos < maxPos) {
                result.add(minItem)
                result.add(maxItem)
            } else if (maxPos < minPos) {
                result.add(maxItem)
                result.add(minItem)
            } else {
                result.add(minItem)
            }

            currentIdx = endIdx
        }

        result.add(items.last())
        return result
    }

    private fun perpendicularDistance(p: GpsPoint, a: GpsPoint, b: GpsPoint): Double {
        val dx = b.longitude - a.longitude
        val dy = b.latitude - a.latitude
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return haversineMeters(p.latitude, p.longitude, a.latitude, a.longitude)
        val t = ((p.longitude - a.longitude) * dx + (p.latitude - a.latitude) * dy) / len2
        val projLon = a.longitude + t * dx
        val projLat = a.latitude + t * dy
        return haversineMeters(p.latitude, p.longitude, projLat, projLon)
    }

    // Trim first and last trimMeters of route for privacy
    fun trimRouteEnds(points: List<GpsPoint>, trimMeters: Int): List<GpsPoint> {
        if (points.size < 3) return points
        var startIdx = 0
        var endIdx = points.size - 1
        var cumulativeStart = 0.0
        for (i in 1 until points.size) {
            cumulativeStart += haversineMeters(points[i - 1].latitude, points[i - 1].longitude, points[i].latitude, points[i].longitude)
            if (cumulativeStart >= trimMeters) {
                startIdx = i
                break
            }
        }
        var cumulativeEnd = 0.0
        for (i in points.size - 2 downTo 0) {
            cumulativeEnd += haversineMeters(points[i + 1].latitude, points[i + 1].longitude, points[i].latitude, points[i].longitude)
            if (cumulativeEnd >= trimMeters) {
                endIdx = i
                break
            }
        }
        return if (startIdx < endIdx) points.subList(startIdx, endIdx + 1) else points
    }

    fun computeBoundingBox(points: List<GpsPoint>): BoundingBox {
        val validPoints = points.filter {
            it.latitude != 0.0 && it.longitude != 0.0 &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
        }
        val targetPoints = if (validPoints.isNotEmpty()) validPoints else points
        if (targetPoints.isEmpty()) return BoundingBox(0.0, 1.0, 0.0, 1.0)
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        for (p in targetPoints) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }

        return BoundingBox(minLat, maxLat, minLon, maxLon)
    }

    fun projectToCanvas(points: List<GpsPoint>, box: BoundingBox, canvasW: Float, canvasH: Float, padding: Float): List<Offset> {
        val latRange = (box.maxLat - box.minLat).coerceAtLeast(0.0001)
        val lonRange = (box.maxLon - box.minLon).coerceAtLeast(0.0001)
        val usableW = (canvasW - 2 * padding).coerceAtLeast(10f)
        val usableH = (canvasH - 2 * padding).coerceAtLeast(10f)

        // Maintain true geographic aspect ratio using mid-latitude cosine
        val midLatRad = Math.toRadians((box.minLat + box.maxLat) / 2.0)
        val cosLat = cos(midLatRad).coerceIn(0.1, 1.0)
        val adjustedLonRange = lonRange * cosLat

        val latScale = usableH / latRange.toFloat()
        val lonScale = usableW / adjustedLonRange.toFloat()
        val scale = minOf(latScale, lonScale)

        val routeW = (adjustedLonRange * scale).toFloat()
        val routeH = (latRange * scale).toFloat()

        val offsetX = padding + (usableW - routeW) / 2f
        val offsetY = padding + (usableH - routeH) / 2f

        val result = ArrayList<Offset>(points.size)
        for (p in points) {
            val x = offsetX + (((p.longitude - box.minLon) * cosLat) * scale).toFloat()
            val y = offsetY + ((box.maxLat - p.latitude) * scale).toFloat() // flip Y
            result.add(Offset(x, y))
        }
        return result
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
