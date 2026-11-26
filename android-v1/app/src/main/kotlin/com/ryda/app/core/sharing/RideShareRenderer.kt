package com.ryda.app.core.sharing

import android.graphics.*
import androidx.compose.ui.geometry.Offset
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.Ride
import java.util.Locale

object RideShareRenderer {

    fun renderShareGraphic(
        ride: Ride,
        points: List<GpsPoint>,
        config: ShareConfig,
        mapBitmap: Bitmap? = null
    ): Bitmap {
        val template = if (config.style == GraphicStyle.MAP_BACKGROUND) SvgLayoutTemplate.HERO_DISTANCE else config.template
        val scaleFactor = 2.5f
        val w = (template.viewBoxWidth * scaleFactor).toInt()
        val h = (template.viewBoxHeight * scaleFactor).toInt()

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scaleFactor, scaleFactor)

        val primaryColor = Color.parseColor(config.colorTheme.primaryHex)
        val secondaryColor = Color.parseColor(config.colorTheme.secondaryHex)

        val processedPoints = if (points.size > 2) RouteSimplifier.simplifyRoute(points, 0.0001) else points
        val routeHeader = getRouteHeader(ride, config)

        val distStr = String.format(Locale.US, "%.1f", ride.totalDistanceKm)
        val timeStr = ride.formattedDuration(ride.movingTimeSeconds)
        val avgSpdVal = if (ride.avgSpeedKmh > 0) ride.avgSpeedKmh else if (ride.movingTimeSeconds > 0) (ride.totalDistanceMeters / ride.movingTimeSeconds) * 3.6 else 0.0
        val avgStr = String.format(Locale.US, "%.1f", avgSpdVal)
        val maxSpdVal = if (ride.maxSpeedKmh > 0) ride.maxSpeedKmh else points.maxOfOrNull { it.speedKmh } ?: 0.0
        val topStr = String.format(Locale.US, "%.1f", maxSpdVal)

        // MAP BACKGROUND STYLE PIPELINE ONLY: Show map background + Hero Distance stats (no dark rectangular overlay)
        if (config.style == GraphicStyle.MAP_BACKGROUND) {
            if (mapBitmap != null) {
                val srcRect = Rect(0, 0, mapBitmap.width, mapBitmap.height)
                val dstRect = Rect(0, 0, template.viewBoxWidth, template.viewBoxHeight)
                canvas.drawBitmap(mapBitmap, srcRect, dstRect, null)
            } else {
                canvas.drawColor(Color.parseColor("#12141A"))
                drawMapGridLines(canvas, template.viewBoxWidth, template.viewBoxHeight)
            }

            // Draw RYDA Watermark Top Right
            drawRydaWordmark(canvas, template.viewBoxWidth.toFloat(), primaryColor)

            // Draw Hero Distance Stats layout directly over the map cleanly without dark rectangular block
            drawHeroDistanceStatsOnly(canvas, routeHeader, distStr, timeStr, avgStr, topStr, primaryColor, secondaryColor)
            return bitmap
        }

        // TRANSPARENT SVG STYLE PIPELINE: Render transparent canvas for SVG vector sharing
        if (config.colorTheme == ColorTheme.PLAIN_BLACK) {
            canvas.drawColor(Color.parseColor(config.colorTheme.bgHex))
        } else {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        when (template) {
            SvgLayoutTemplate.HERO_DISTANCE -> {
                drawHeroDistance(canvas, processedPoints, routeHeader, distStr, timeStr, avgStr, topStr, primaryColor, secondaryColor)
            }
            SvgLayoutTemplate.HORIZONTAL_BANNER -> {
                drawHorizontalBanner(canvas, processedPoints, routeHeader, distStr, timeStr, avgStr, topStr, primaryColor, secondaryColor)
            }
            SvgLayoutTemplate.MINIMALIST_STORY -> {
                drawMinimalistStory(canvas, processedPoints, routeHeader, distStr, timeStr, avgStr, topStr, primaryColor, secondaryColor)
            }
            SvgLayoutTemplate.SPLIT_PANEL -> {
                drawSplitPanel(canvas, processedPoints, routeHeader, distStr, timeStr, avgStr, topStr, primaryColor, secondaryColor)
            }
            SvgLayoutTemplate.CLASSIC_CARD -> {
                drawClassicCard(canvas, processedPoints, routeHeader, distStr, timeStr, avgStr, topStr, primaryColor, secondaryColor)
            }
        }

        return bitmap
    }

    private fun getRouteHeader(ride: Ride, config: ShareConfig): String {
        val start = config.startLocationName.trim()
        val dest = config.destinationName.trim()
        return when {
            start.isNotBlank() && dest.isNotBlank() -> "$start → $dest"
            start.isNotBlank() -> start
            dest.isNotBlank() -> dest
            ride.title.isNotBlank() && !ride.title.contains("Ride", ignoreCase = true) -> ride.title
            else -> "Recorded Ride"
        }
    }

    private fun drawRydaWordmark(canvas: Canvas, w: Float, accentColor: Int) {
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
            alpha = 220
        }
        canvas.drawText("RYDA", w - 20f, 30f, paint)

        val accentDotP = Paint().apply {
            color = accentColor
            isAntiAlias = true
        }
        canvas.drawCircle(w - 14f, 26f, 3f, accentDotP)
    }

    private fun drawHeroDistanceStatsOnly(
        canvas: Canvas,
        header: String,
        dist: String,
        time: String,
        avg: String,
        top: String,
        primaryColor: Int,
        secondaryColor: Int
    ) {
        // Header Text
        drawText(canvas, header, 16f, 30f, 15f, 800, secondaryColor)

        // KM Label & Distance Value
        drawText(canvas, "KM", 16f, 68f, 12f, 700, primaryColor)
        drawText(canvas, dist, 16f, 118f, 52f, 900, primaryColor)

        // Bottom Stats Row
        drawText(canvas, "TIME", 16f, 390f, 11f, 700, primaryColor)
        drawText(canvas, time, 16f, 422f, 24f, 800, primaryColor)

        drawText(canvas, "AVG", 155f, 390f, 11f, 700, primaryColor)
        drawText(canvas, avg, 155f, 422f, 24f, 800, primaryColor)

        drawText(canvas, "TOP", 285f, 390f, 11f, 700, primaryColor)
        drawText(canvas, top, 285f, 422f, 24f, 800, primaryColor)
    }

    private fun drawHeroDistance(
        canvas: Canvas,
        points: List<GpsPoint>,
        header: String,
        dist: String,
        time: String,
        avg: String,
        top: String,
        primaryColor: Int,
        secondaryColor: Int
    ) {
        drawHeroDistanceStatsOnly(canvas, header, dist, time, avg, top, primaryColor, secondaryColor)
        // Route Container (left=30, top=140, width=360, height=200)
        drawRoute(canvas, points, 30f, 140f, 360f, 200f, strokeW = 6f, primaryColor = primaryColor, secondaryColor = secondaryColor)
    }

    private fun drawHorizontalBanner(
        canvas: Canvas,
        points: List<GpsPoint>,
        header: String,
        dist: String,
        time: String,
        avg: String,
        top: String,
        primaryColor: Int,
        secondaryColor: Int
    ) {
        // Header Text
        drawText(canvas, header, 16f, 28f, 15f, 800, secondaryColor)

        // Route Container (left=20, top=50, width=195, height=115)
        drawRoute(canvas, points, 20f, 50f, 195f, 115f, strokeW = 5.5f, primaryColor = primaryColor, secondaryColor = secondaryColor)

        // Right Stats Columns
        drawText(canvas, "KM", 245f, 88f, 12f, 700, primaryColor)
        drawText(canvas, dist, 245f, 124f, 30f, 800, primaryColor)

        drawText(canvas, "TIME", 370f, 88f, 12f, 700, primaryColor)
        drawText(canvas, time, 370f, 124f, 30f, 800, primaryColor)

        drawText(canvas, "AVG", 490f, 88f, 12f, 700, primaryColor)
        drawText(canvas, avg, 490f, 124f, 30f, 800, primaryColor)

        drawText(canvas, "TOP", 610f, 88f, 12f, 700, primaryColor)
        drawText(canvas, top, 610f, 124f, 30f, 800, primaryColor)
    }

    private fun drawMinimalistStory(
        canvas: Canvas,
        points: List<GpsPoint>,
        header: String,
        dist: String,
        time: String,
        avg: String,
        top: String,
        primaryColor: Int,
        secondaryColor: Int
    ) {
        // Header Text
        drawText(canvas, header, 20f, 34f, 15f, 800, secondaryColor)

        // Route Container (left=35, top=70, width=390, height=240)
        drawRoute(canvas, points, 35f, 70f, 390f, 240f, strokeW = 6.5f, primaryColor = primaryColor, secondaryColor = secondaryColor)

        // Bottom Stats Row
        drawText(canvas, "KM", 20f, 345f, 11f, 700, primaryColor)
        drawText(canvas, dist, 20f, 379f, 28f, 800, primaryColor)

        drawText(canvas, "TIME", 130f, 345f, 11f, 700, primaryColor)
        drawText(canvas, time, 130f, 379f, 28f, 800, primaryColor)

        drawText(canvas, "AVG", 243f, 345f, 11f, 700, primaryColor)
        drawText(canvas, avg, 243f, 379f, 28f, 800, primaryColor)

        drawText(canvas, "TOP", 353f, 345f, 11f, 700, primaryColor)
        drawText(canvas, top, 353f, 379f, 28f, 800, primaryColor)
    }

    private fun drawSplitPanel(
        canvas: Canvas,
        points: List<GpsPoint>,
        header: String,
        dist: String,
        time: String,
        avg: String,
        top: String,
        primaryColor: Int,
        secondaryColor: Int
    ) {
        // Header Text
        drawText(canvas, header, 16f, 30f, 16f, 800, secondaryColor)

        // Left Route Container (left=30, top=60, width=330, height=290)
        drawRoute(canvas, points, 30f, 60f, 330f, 290f, strokeW = 6f, primaryColor = primaryColor, secondaryColor = secondaryColor)

        // Right Vertical Stats Column
        drawText(canvas, "KM", 395f, 92f, 12f, 700, primaryColor)
        drawText(canvas, dist, 395f, 122f, 28f, 800, primaryColor)

        drawText(canvas, "TIME", 395f, 166f, 12f, 700, primaryColor)
        drawText(canvas, time, 395f, 196f, 28f, 800, primaryColor)

        drawText(canvas, "AVG", 395f, 240f, 12f, 700, primaryColor)
        drawText(canvas, "$avg km/h", 395f, 270f, 28f, 800, primaryColor)

        drawText(canvas, "TOP", 395f, 314f, 12f, 700, primaryColor)
        drawText(canvas, "$top km/h", 395f, 344f, 28f, 800, primaryColor)
    }

    private fun drawClassicCard(
        canvas: Canvas,
        points: List<GpsPoint>,
        header: String,
        dist: String,
        time: String,
        avg: String,
        top: String,
        primaryColor: Int,
        secondaryColor: Int
    ) {
        // Header Labels
        drawText(canvas, "ROUTE", 16f, 34f, 11f, 700, secondaryColor, letterSpacing = 0.08f)
        drawText(canvas, header, 16f, 58f, 16f, 800, secondaryColor)

        // Center Route Container (left=30, top=85, width=360, height=210)
        drawRoute(canvas, points, 30f, 85f, 360f, 210f, strokeW = 6f, primaryColor = primaryColor, secondaryColor = secondaryColor)

        // Bottom 2x2 Grid Stats
        drawText(canvas, "KM", 16f, 325f, 12f, 700, primaryColor)
        drawText(canvas, dist, 16f, 358f, 30f, 800, primaryColor)

        drawText(canvas, "TIME", 210f, 325f, 12f, 700, primaryColor)
        drawText(canvas, time, 210f, 358f, 30f, 800, primaryColor)

        drawText(canvas, "AVG", 16f, 415f, 12f, 700, primaryColor)
        drawTextWithUnit(canvas, avg, "km/h", 16f, 448f, 30f, 15f, primaryColor)

        drawText(canvas, "TOP", 210f, 415f, 12f, 700, primaryColor)
        drawTextWithUnit(canvas, top, "km/h", 210f, 448f, 30f, 15f, primaryColor)
    }

    private fun drawRoute(
        canvas: Canvas,
        points: List<GpsPoint>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        strokeW: Float,
        primaryColor: Int,
        secondaryColor: Int
    ) {
        val offsets = projectPointsToContainer(points, left, top, width, height, padding = 15f)
        if (offsets.size >= 2) {
            val paint = Paint().apply {
                color = primaryColor
                strokeWidth = strokeW
                style = Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            path.moveTo(offsets[0].x, offsets[0].y)
            for (i in 1 until offsets.size) {
                path.lineTo(offsets[i].x, offsets[i].y)
            }
            canvas.drawPath(path, paint)

            // Start & Finish Circles matching original SVG
            val startCircle = Paint().apply { color = secondaryColor; isAntiAlias = true; style = Paint.Style.FILL }
            canvas.drawCircle(offsets.first().x, offsets.first().y, 7f, startCircle)

            val finishCircle = Paint().apply { color = primaryColor; isAntiAlias = true; style = Paint.Style.FILL }
            canvas.drawCircle(offsets.last().x, offsets.last().y, 7f, finishCircle)
        }
    }

    private fun drawMapGridLines(canvas: Canvas, w: Int, h: Int) {
        val gridPaint = Paint().apply {
            color = Color.parseColor("#1C202B")
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        var x = 0f
        while (x < w) {
            canvas.drawLine(x, 0f, x, h.toFloat(), gridPaint)
            x += 60f
        }
        var y = 0f
        while (y < h) {
            canvas.drawLine(0f, y, w.toFloat(), y, gridPaint)
            y += 60f
        }
    }

    private fun projectPointsToContainer(
        points: List<GpsPoint>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        padding: Float
    ): List<Offset> {
        if (points.isEmpty()) return emptyList()
        val box = RouteSimplifier.computeBoundingBox(points)
        val latRange = (box.maxLat - box.minLat).coerceAtLeast(0.0001)
        val lonRange = (box.maxLon - box.minLon).coerceAtLeast(0.0001)

        val usableW = width - 2 * padding
        val usableH = height - 2 * padding

        val latScale = usableH / latRange.toFloat()
        val lonScale = usableW / lonRange.toFloat()
        val scale = minOf(latScale, lonScale)

        val offsetX = left + padding + (usableW - lonRange.toFloat() * scale) / 2f
        val offsetY = top + padding + (usableH - latRange.toFloat() * scale) / 2f

        val offsets = ArrayList<Offset>(points.size)
        for (p in points) {
            val x = offsetX + ((p.longitude - box.minLon) * scale).toFloat()
            val y = offsetY + ((box.maxLat - p.latitude) * scale).toFloat() // Y grows down
            offsets.add(Offset(x, y))
        }
        return offsets
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        sizeSp: Float,
        weight: Int,
        color: Int,
        letterSpacing: Float = 0f
    ) {
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
            textSize = sizeSp
            typeface = if (weight >= 800) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (letterSpacing > 0f) this.letterSpacing = letterSpacing
        }
        canvas.drawText(text, x, y, paint)
    }

    private fun drawTextWithUnit(
        canvas: Canvas,
        mainText: String,
        unitText: String,
        x: Float,
        y: Float,
        mainSizeSp: Float,
        unitSizeSp: Float,
        color: Int
    ) {
        val mainPaint = Paint().apply {
            this.color = color
            isAntiAlias = true
            textSize = mainSizeSp
            typeface = Typeface.DEFAULT_BOLD
        }
        val unitPaint = Paint().apply {
            this.color = color
            isAntiAlias = true
            textSize = unitSizeSp
            typeface = Typeface.DEFAULT
        }
        canvas.drawText(mainText, x, y, mainPaint)
        val mainWidth = mainPaint.measureText("$mainText ")
        canvas.drawText(unitText, x + mainWidth, y, unitPaint)
    }
}
