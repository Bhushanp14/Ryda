package com.ryda.app.core.sharing

import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.Ride
import java.util.Locale

/**
 * Dedicated vector SVG generator for RYDA Ride Sharing Studio.
 *
 * Produces genuine, transparent SVG XML without background rectangles,
 * card backgrounds, or embedded raster images.
 */
object SvgExporter {

    internal data class PointF(val x: Float, val y: Float)

    /**
     * Generate valid SVG XML string representing the ride and chosen layout template.
     */
    fun exportToSvg(
        ride: Ride,
        points: List<GpsPoint>,
        config: ShareConfig
    ): String {
        val template = config.template
        val w = template.viewBoxWidth
        val h = template.viewBoxHeight

        val primaryColor = config.colorTheme.primaryHex
        val secondaryColor = config.colorTheme.secondaryHex

        val processedPoints = if (points.size > 2000) RouteSimplifier.simplifyRoute(points, 5.0) else points
        val routeHeader = getRouteHeader(ride, config)

        val distStr = String.format(Locale.US, "%.1f", ride.totalDistanceKm)
        val timeStr = ride.formattedDuration(ride.movingTimeSeconds)
        val avgSpdVal = if (ride.avgSpeedKmh > 0) {
            ride.avgSpeedKmh
        } else if (ride.movingTimeSeconds > 0) {
            (ride.totalDistanceMeters / ride.movingTimeSeconds) * 3.6
        } else 0.0
        val avgStr = String.format(Locale.US, "%.1f", avgSpdVal)
        val maxSpdVal = if (ride.maxSpeedKmh > 0) {
            ride.maxSpeedKmh
        } else {
            points.maxOfOrNull { it.speedKmh } ?: 0.0
        }
        val topStr = String.format(Locale.US, "%.1f", maxSpdVal)

        val bodyContent = when (template) {
            SvgLayoutTemplate.HERO_DISTANCE -> renderHeroDistance(
                processedPoints, routeHeader, distStr, timeStr, avgStr, topStr, primaryColor, secondaryColor
            )
            SvgLayoutTemplate.HORIZONTAL_BANNER -> renderHorizontalBanner(
                processedPoints, routeHeader, distStr, timeStr, avgStr, topStr, primaryColor, secondaryColor
            )
            SvgLayoutTemplate.MINIMALIST_STORY -> renderMinimalistStory(
                processedPoints, routeHeader, distStr, timeStr, avgStr, topStr, primaryColor, secondaryColor
            )
            SvgLayoutTemplate.SPLIT_PANEL -> renderSplitPanel(
                processedPoints, routeHeader, distStr, timeStr, avgStr, topStr, primaryColor, secondaryColor
            )
            SvgLayoutTemplate.CLASSIC_CARD -> renderClassicCard(
                processedPoints, routeHeader, distStr, timeStr, avgStr, topStr, primaryColor, secondaryColor
            )
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $w $h" width="$w" height="$h">
$bodyContent
</svg>""".trim()
    }

    fun getRouteHeader(ride: Ride, config: ShareConfig): String {
        val start = config.startLocationName.trim()
        val dest = config.destinationName.trim()
        return when {
            start.isNotBlank() && dest.isNotBlank() -> "$start \u2192 $dest"
            start.isNotBlank() -> start
            dest.isNotBlank() -> dest
            ride.title.isNotBlank() && !ride.title.contains("Ride", ignoreCase = true) -> ride.title
            else -> "Recorded Ride"
        }
    }

    fun escapeXml(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    internal fun projectPointsToContainer(
        points: List<GpsPoint>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        padding: Float
    ): List<PointF> {
        val valid = points.filter {
            it.latitude != 0.0 && it.longitude != 0.0 &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
        }
        val targetPoints = if (valid.isNotEmpty()) valid else points
        if (targetPoints.isEmpty()) return emptyList()
        val box = RouteSimplifier.computeBoundingBox(targetPoints)
        val latRange = (box.maxLat - box.minLat).coerceAtLeast(0.0001)
        val lonRange = (box.maxLon - box.minLon).coerceAtLeast(0.0001)

        val usableW = (width - 2 * padding).coerceAtLeast(10f)
        val usableH = (height - 2 * padding).coerceAtLeast(10f)

        // Maintain true geographic aspect ratio using mid-latitude cosine
        val midLatRad = Math.toRadians((box.minLat + box.maxLat) / 2.0)
        val cosLat = kotlin.math.cos(midLatRad).coerceIn(0.1, 1.0)
        val adjustedLonRange = lonRange * cosLat

        val latScale = usableH / latRange.toFloat()
        val lonScale = usableW / adjustedLonRange.toFloat()
        val scale = minOf(latScale, lonScale)

        val routeW = (adjustedLonRange * scale).toFloat()
        val routeH = (latRange * scale).toFloat()

        val offsetX = left + padding + (usableW - routeW) / 2f
        val offsetY = top + padding + (usableH - routeH) / 2f

        val offsets = ArrayList<PointF>(targetPoints.size)
        for (p in targetPoints) {
            val x = offsetX + (((p.longitude - box.minLon) * cosLat) * scale).toFloat()
            val y = offsetY + ((box.maxLat - p.latitude) * scale).toFloat() // Flip Y
            offsets.add(PointF(x, y))
        }
        return offsets
    }

    private fun renderRoute(
        points: List<GpsPoint>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        strokeW: Float,
        primaryColor: String,
        secondaryColor: String
    ): String {
        val offsets = projectPointsToContainer(points, left, top, width, height, padding = 15f)
        if (offsets.size < 2) {
            if (offsets.size == 1) {
                val p = offsets[0]
                val cx = String.format(Locale.US, "%.1f", p.x)
                val cy = String.format(Locale.US, "%.1f", p.y)
                return """  <circle cx="$cx" cy="$cy" r="7" fill="$primaryColor"/>"""
            }
            return ""
        }

        val pathBuilder = StringBuilder(offsets.size * 18)
        pathBuilder.append("M ").append(String.format(Locale.US, "%.1f", offsets[0].x))
            .append(" ").append(String.format(Locale.US, "%.1f", offsets[0].y))
        for (i in 1 until offsets.size) {
            pathBuilder.append(" L ").append(String.format(Locale.US, "%.1f", offsets[i].x))
                .append(" ").append(String.format(Locale.US, "%.1f", offsets[i].y))
        }

        val startX = String.format(Locale.US, "%.1f", offsets.first().x)
        val startY = String.format(Locale.US, "%.1f", offsets.first().y)
        val finishX = String.format(Locale.US, "%.1f", offsets.last().x)
        val finishY = String.format(Locale.US, "%.1f", offsets.last().y)

        return """  <path d="$pathBuilder" fill="none" stroke="$primaryColor" stroke-width="$strokeW" stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="$startX" cy="$startY" r="7" fill="$secondaryColor"/>
  <circle cx="$finishX" cy="$finishY" r="7" fill="$primaryColor"/>"""
    }

    private fun buildTextTag(
        x: Float,
        y: Float,
        text: String,
        fontSize: Float,
        fontWeight: Int,
        fill: String,
        letterSpacingEm: Float = 0f
    ): String {
        val escaped = escapeXml(text)
        val weightStr = when {
            fontWeight >= 900 -> "900"
            fontWeight >= 800 -> "800"
            fontWeight >= 700 -> "700"
            fontWeight >= 600 -> "600"
            else -> "normal"
        }
        val letterSpacingAttr = if (letterSpacingEm > 0f) """ letter-spacing="${letterSpacingEm}em"""" else ""
        return """  <text x="$x" y="$y" font-family="system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-size="$fontSize" font-weight="$weightStr" fill="$fill"$letterSpacingAttr>$escaped</text>"""
    }

    private fun buildTextWithUnit(
        x: Float,
        y: Float,
        value: String,
        unit: String,
        valueSize: Float,
        unitSize: Float,
        fill: String
    ): String {
        val escapedVal = escapeXml(value)
        val escapedUnit = escapeXml(unit)
        return """  <text x="$x" y="$y" font-family="system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" fill="$fill"><tspan font-size="$valueSize" font-weight="800">$escapedVal</tspan><tspan font-size="$unitSize" font-weight="normal"> $escapedUnit</tspan></text>"""
    }

    private fun renderHeroDistance(
        points: List<GpsPoint>,
        header: String,
        dist: String,
        time: String,
        avg: String,
        top: String,
        primaryColor: String,
        secondaryColor: String
    ): String {
        val routeSvg = renderRoute(points, 30f, 140f, 360f, 200f, strokeW = 6f, primaryColor = primaryColor, secondaryColor = secondaryColor)
        val elements = mutableListOf<String>()
        elements.add(buildTextTag(16f, 30f, header, 15f, 800, secondaryColor))
        elements.add(buildTextTag(16f, 68f, "KM", 12f, 700, primaryColor))
        elements.add(buildTextTag(16f, 118f, dist, 52f, 900, primaryColor))
        if (routeSvg.isNotBlank()) elements.add(routeSvg)
        elements.add(buildTextTag(16f, 390f, "TIME", 11f, 700, primaryColor))
        elements.add(buildTextTag(16f, 422f, time, 24f, 800, primaryColor))
        elements.add(buildTextTag(155f, 390f, "AVG", 11f, 700, primaryColor))
        elements.add(buildTextTag(155f, 422f, avg, 24f, 800, primaryColor))
        elements.add(buildTextTag(285f, 390f, "TOP", 11f, 700, primaryColor))
        elements.add(buildTextTag(285f, 422f, top, 24f, 800, primaryColor))
        return elements.joinToString("\n")
    }

    private fun renderHorizontalBanner(
        points: List<GpsPoint>,
        header: String,
        dist: String,
        time: String,
        avg: String,
        top: String,
        primaryColor: String,
        secondaryColor: String
    ): String {
        val routeSvg = renderRoute(points, 20f, 50f, 195f, 115f, strokeW = 5.5f, primaryColor = primaryColor, secondaryColor = secondaryColor)
        val elements = mutableListOf<String>()
        elements.add(buildTextTag(16f, 28f, header, 15f, 800, secondaryColor))
        if (routeSvg.isNotBlank()) elements.add(routeSvg)
        elements.add(buildTextTag(245f, 88f, "KM", 12f, 700, primaryColor))
        elements.add(buildTextTag(245f, 124f, dist, 30f, 800, primaryColor))
        elements.add(buildTextTag(370f, 88f, "TIME", 12f, 700, primaryColor))
        elements.add(buildTextTag(370f, 124f, time, 30f, 800, primaryColor))
        elements.add(buildTextTag(490f, 88f, "AVG", 12f, 700, primaryColor))
        elements.add(buildTextTag(490f, 124f, avg, 30f, 800, primaryColor))
        elements.add(buildTextTag(610f, 88f, "TOP", 12f, 700, primaryColor))
        elements.add(buildTextTag(610f, 124f, top, 30f, 800, primaryColor))
        return elements.joinToString("\n")
    }

    private fun renderMinimalistStory(
        points: List<GpsPoint>,
        header: String,
        dist: String,
        time: String,
        avg: String,
        top: String,
        primaryColor: String,
        secondaryColor: String
    ): String {
        val routeSvg = renderRoute(points, 35f, 70f, 390f, 240f, strokeW = 6.5f, primaryColor = primaryColor, secondaryColor = secondaryColor)
        val elements = mutableListOf<String>()
        elements.add(buildTextTag(20f, 34f, header, 15f, 800, secondaryColor))
        if (routeSvg.isNotBlank()) elements.add(routeSvg)
        elements.add(buildTextTag(20f, 345f, "KM", 11f, 700, primaryColor))
        elements.add(buildTextTag(20f, 379f, dist, 28f, 800, primaryColor))
        elements.add(buildTextTag(130f, 345f, "TIME", 11f, 700, primaryColor))
        elements.add(buildTextTag(130f, 379f, time, 28f, 800, primaryColor))
        elements.add(buildTextTag(243f, 345f, "AVG", 11f, 700, primaryColor))
        elements.add(buildTextTag(243f, 379f, avg, 28f, 800, primaryColor))
        elements.add(buildTextTag(353f, 345f, "TOP", 11f, 700, primaryColor))
        elements.add(buildTextTag(353f, 379f, top, 28f, 800, primaryColor))
        return elements.joinToString("\n")
    }

    private fun renderSplitPanel(
        points: List<GpsPoint>,
        header: String,
        dist: String,
        time: String,
        avg: String,
        top: String,
        primaryColor: String,
        secondaryColor: String
    ): String {
        val routeSvg = renderRoute(points, 30f, 60f, 330f, 290f, strokeW = 6f, primaryColor = primaryColor, secondaryColor = secondaryColor)
        val elements = mutableListOf<String>()
        elements.add(buildTextTag(16f, 30f, header, 16f, 800, secondaryColor))
        if (routeSvg.isNotBlank()) elements.add(routeSvg)
        elements.add(buildTextTag(395f, 92f, "KM", 12f, 700, primaryColor))
        elements.add(buildTextTag(395f, 122f, dist, 28f, 800, primaryColor))
        elements.add(buildTextTag(395f, 166f, "TIME", 12f, 700, primaryColor))
        elements.add(buildTextTag(395f, 196f, time, 28f, 800, primaryColor))
        elements.add(buildTextTag(395f, 240f, "AVG", 12f, 700, primaryColor))
        elements.add(buildTextTag(395f, 270f, "$avg km/h", 28f, 800, primaryColor))
        elements.add(buildTextTag(395f, 314f, "TOP", 12f, 700, primaryColor))
        elements.add(buildTextTag(395f, 344f, "$top km/h", 28f, 800, primaryColor))
        return elements.joinToString("\n")
    }

    private fun renderClassicCard(
        points: List<GpsPoint>,
        header: String,
        dist: String,
        time: String,
        avg: String,
        top: String,
        primaryColor: String,
        secondaryColor: String
    ): String {
        val routeSvg = renderRoute(points, 30f, 85f, 360f, 210f, strokeW = 6f, primaryColor = primaryColor, secondaryColor = secondaryColor)
        val elements = mutableListOf<String>()
        elements.add(buildTextTag(16f, 34f, "ROUTE", 11f, 700, secondaryColor, letterSpacingEm = 0.08f))
        elements.add(buildTextTag(16f, 58f, header, 16f, 800, secondaryColor))
        if (routeSvg.isNotBlank()) elements.add(routeSvg)
        elements.add(buildTextTag(16f, 325f, "KM", 12f, 700, primaryColor))
        elements.add(buildTextTag(16f, 358f, dist, 30f, 800, primaryColor))
        elements.add(buildTextTag(210f, 325f, "TIME", 12f, 700, primaryColor))
        elements.add(buildTextTag(210f, 358f, time, 30f, 800, primaryColor))
        elements.add(buildTextTag(16f, 415f, "AVG", 12f, 700, primaryColor))
        elements.add(buildTextWithUnit(16f, 448f, avg, "km/h", 30f, 15f, primaryColor))
        elements.add(buildTextTag(210f, 415f, "TOP", 12f, 700, primaryColor))
        elements.add(buildTextWithUnit(210f, 448f, top, "km/h", 30f, 15f, primaryColor))
        return elements.joinToString("\n")
    }
}
