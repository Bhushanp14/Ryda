package com.ryda.app.core.sharing

import android.graphics.*
import android.graphics.Paint.Align
import androidx.compose.ui.geometry.Offset
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.Motorcycle
import com.ryda.app.core.models.Ride
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

object RideShareRenderer {

    fun renderTransparentSticker(ride: Ride, points: List<GpsPoint>, motorcycle: Motorcycle?, config: ShareConfig): Bitmap {
        val w = config.exportSize.width; val h = config.exportSize.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // DO NOT call canvas.drawColor() — bitmap starts fully transparent
        val canvas = Canvas(bitmap)

        val processedPoints = preparePoints(points, config)
        if (processedPoints.size >= 2) {
            val box = RouteSimplifier.computeBoundingBox(processedPoints)
            val isDark = config.textStyle == TextStyle.LIGHT_ON_DARK
            val routeColor = if (isDark) Color.WHITE else Color.BLACK

            // Route path: draw in upper 60% of canvas for COMPACT_CORNER/HORIZONTAL_STRIP
            val routeRegionH = when (config.layout) {
                StickerLayout.COMPACT_CORNER, StickerLayout.HORIZONTAL_STRIP -> h * 0.6f
                StickerLayout.FULL_VERTICAL -> h * 0.9f
            }
            val offsets = RouteSimplifier.projectToCanvas(processedPoints, box, w.toFloat(), routeRegionH, w * 0.08f)
            drawRoute(canvas, offsets, routeColor, 6f * (w / 1080f))

            // Stats block based on layout
            when (config.layout) {
                StickerLayout.COMPACT_CORNER -> drawStatsBlock(canvas, ride, motorcycle, config, 40f, h * 0.65f, w * 0.5f, h.toFloat() - 40f, isDark)
                StickerLayout.HORIZONTAL_STRIP -> drawHorizontalStatsStrip(canvas, ride, motorcycle, config, h * 0.65f, h.toFloat(), isDark)
                StickerLayout.FULL_VERTICAL -> drawStatsBlock(canvas, ride, motorcycle, config, 40f, h * 0.3f, w * 0.6f, h * 0.75f, isDark)
            }

            // RYDA watermark top-right
            drawRydaWordmark(canvas, w.toFloat(), isDark)
        }
        return bitmap
    }

    fun renderMapCard(ride: Ride, points: List<GpsPoint>, motorcycle: Motorcycle?, config: ShareConfig): Bitmap {
        val w = config.exportSize.width; val h = config.exportSize.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (config.mapCardStyle) {
            MapCardStyle.DARK -> {
                canvas.drawColor(Color.parseColor("#101114"))
                drawGridLines(canvas, w, h, Color.parseColor("#1E1E24"), 60)
            }
            MapCardStyle.LIGHT -> {
                canvas.drawColor(Color.parseColor("#F0F0F5"))
                drawGridLines(canvas, w, h, Color.parseColor("#D0D0DA"), 60)
            }
            MapCardStyle.MINIMAL_ROUTE -> canvas.drawColor(Color.parseColor("#0A0A0A"))
            MapCardStyle.RYDA_BRANDED -> {
                // Gradient background
                val gradient = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.parseColor("#101114"), Color.parseColor("#1A1A2E"), Shader.TileMode.CLAMP)
                val bgPaint = Paint(); bgPaint.shader = gradient
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
                drawGridLines(canvas, w, h, Color.parseColor("#1A1A2E"), 80)
            }
        }

        val processedPoints = preparePoints(points, config)
        if (processedPoints.size >= 2) {
            val box = RouteSimplifier.computeBoundingBox(processedPoints)
            val statsH = h * 0.28f
            val offsets = RouteSimplifier.projectToCanvas(processedPoints, box, w.toFloat(), h - statsH, w * 0.1f)
            val routeColor = when (config.mapCardStyle) {
                MapCardStyle.DARK, MapCardStyle.RYDA_BRANDED -> Color.parseColor("#FF6B35")
                MapCardStyle.LIGHT -> Color.parseColor("#101114")
                MapCardStyle.MINIMAL_ROUTE -> Color.WHITE
            }
            val strokeW = if (config.mapCardStyle == MapCardStyle.MINIMAL_ROUTE) 3f * (w/1080f) else 5f * (w/1080f)

            if (config.mapCardStyle == MapCardStyle.RYDA_BRANDED) {
                // Glow effect: draw twice
                drawRoute(canvas, offsets, Color.parseColor("#80FF6B35"), 12f * (w/1080f))
            }
            drawRoute(canvas, offsets, routeColor, strokeW)

            // Start/end markers
            if (offsets.isNotEmpty()) {
                drawMarker(canvas, offsets.first(), Color.parseColor("#4CAF50"), 16f * (w/1080f))
                drawMarker(canvas, offsets.last(), Color.parseColor("#FF6B35"), 16f * (w/1080f))
            }
        }

        // Stats panel bottom
        val isDark = config.mapCardStyle != MapCardStyle.LIGHT
        drawCardStatsPanel(canvas, ride, motorcycle, config, h * 0.72f, h.toFloat(), w.toFloat(), isDark, config.mapCardStyle == MapCardStyle.RYDA_BRANDED)
        return bitmap
    }

    fun renderPhotoCard(ride: Ride, points: List<GpsPoint>, motorcycle: Motorcycle?, photoBitmap: Bitmap, config: ShareConfig): Bitmap {
        val w = config.exportSize.width; val h = config.exportSize.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Center-crop photo to canvas size
        val srcAspect = photoBitmap.width.toFloat() / photoBitmap.height
        val dstAspect = w.toFloat() / h
        val srcRect = if (srcAspect > dstAspect) {
            val newW = (photoBitmap.height * dstAspect).toInt()
            val x = (photoBitmap.width - newW) / 2
            Rect(x, 0, x + newW, photoBitmap.height)
        } else {
            val newH = (photoBitmap.width / dstAspect).toInt()
            val y = (photoBitmap.height - newH) / 2
            Rect(0, y, photoBitmap.width, y + newH)
        }
        canvas.drawBitmap(photoBitmap, srcRect, Rect(0, 0, w, h), null)

        // Bottom scrim gradient (transparent to dark)
        val scrimGradient = LinearGradient(0f, h * 0.55f, 0f, h.toFloat(), Color.TRANSPARENT, Color.parseColor("#B3000000"), Shader.TileMode.CLAMP)
        val scrimPaint = Paint(); scrimPaint.shader = scrimGradient
        canvas.drawRect(0f, h * 0.55f, w.toFloat(), h.toFloat(), scrimPaint)

        val processedPoints = preparePoints(points, config)
        if (processedPoints.size >= 2) {
            val box = RouteSimplifier.computeBoundingBox(processedPoints)
            val offsets = RouteSimplifier.projectToCanvas(processedPoints, box, w * 0.4f, h * 0.3f, w * 0.05f)
            val adjustedOffsets = offsets.map { Offset(it.x + w * 0.55f, it.y + h * 0.08f) }
            drawRoute(canvas, adjustedOffsets, Color.WHITE, 4f * (w/1080f))
        }

        drawCardStatsPanel(canvas, ride, motorcycle, config, h * 0.72f, h.toFloat(), w.toFloat(), true, false)
        drawRydaWordmark(canvas, w.toFloat(), true)
        return bitmap
    }

    // --- Private helpers ---

    private fun preparePoints(points: List<GpsPoint>, config: ShareConfig): List<GpsPoint> {
        var pts = if (config.trimRouteEnds && points.size > 10) RouteSimplifier.trimRouteEnds(points, config.trimMeters) else points
        pts = RouteSimplifier.simplifyRoute(pts, 0.0001)
        return pts
    }

    private fun drawRoute(canvas: Canvas, offsets: List<Offset>, color: Int, strokeWidth: Float) {
        if (offsets.size < 2) return
        val paint = Paint().apply {
            this.color = color; this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE; isAntiAlias = true
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        path.moveTo(offsets[0].x, offsets[0].y)
        offsets.drop(1).forEach { path.lineTo(it.x, it.y) }
        canvas.drawPath(path, paint)
    }

    private fun drawMarker(canvas: Canvas, offset: Offset, color: Int, radius: Float) {
        val paint = Paint().apply { this.color = color; isAntiAlias = true; style = Paint.Style.FILL }
        canvas.drawCircle(offset.x, offset.y, radius, paint)
        val border = Paint().apply { this.color = Color.WHITE; isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = radius * 0.4f }
        canvas.drawCircle(offset.x, offset.y, radius, border)
    }

    private fun drawGridLines(canvas: Canvas, w: Int, h: Int, color: Int, spacing: Int) {
        val paint = Paint().apply { this.color = color; strokeWidth = 1f; alpha = 80 }
        var x = 0f; while (x < w) { canvas.drawLine(x, 0f, x, h.toFloat(), paint); x += spacing }
        var y = 0f; while (y < h) { canvas.drawLine(0f, y, w.toFloat(), y, paint); y += spacing }
    }

    private fun drawStatsBlock(canvas: Canvas, ride: Ride, motorcycle: Motorcycle?, config: ShareConfig, left: Float, top: Float, right: Float, bottom: Float, isDark: Boolean) {
        val bgColor = if (isDark) Color.parseColor("#A0101114") else Color.parseColor("#A0FFFFFF")
        val textColor = if (isDark) Color.WHITE else Color.parseColor("#101114")
        val accentColor = Color.parseColor("#FF6B35")
        val bgPaint = Paint().apply { color = bgColor; isAntiAlias = true }
        canvas.drawRoundRect(left, top, right, bottom, 20f, 20f, bgPaint)

        var yPos = top + 48f
        val scale = (right - left) / 480f
        val statPaint = Paint().apply { color = textColor; isAntiAlias = true; textSize = 36f * scale; typeface = Typeface.DEFAULT_BOLD }
        val labelPaint = Paint().apply { color = textColor; isAntiAlias = true; textSize = 22f * scale; alpha = 180 }
        val accentPaint = Paint().apply { color = accentColor; isAntiAlias = true; textSize = 36f * scale; typeface = Typeface.DEFAULT_BOLD }

        if (config.showDistance) {
            canvas.drawText(formatDistance(ride), left + 20f, yPos, accentPaint); yPos += 26f * scale
            canvas.drawText("DISTANCE", left + 20f, yPos, labelPaint); yPos += 44f * scale
        }
        if (config.showMovingTime) {
            canvas.drawText(formatDuration(ride), left + 20f, yPos, statPaint); yPos += 26f * scale
            canvas.drawText("MOVING TIME", left + 20f, yPos, labelPaint); yPos += 44f * scale
        }
        if (config.showAvgSpeed) {
            canvas.drawText(formatAvgSpeed(ride), left + 20f, yPos, statPaint); yPos += 26f * scale
            canvas.drawText("AVG SPEED", left + 20f, yPos, labelPaint); yPos += 44f * scale
        }
        if (config.showMaxSpeed) {
            canvas.drawText(formatMaxSpeed(ride), left + 20f, yPos, statPaint); yPos += 26f * scale
            canvas.drawText("TOP SPEED", left + 20f, yPos, labelPaint); yPos += 44f * scale
        }
        if (config.showElevation && ride.elevationGainMeters > 0) {
            canvas.drawText(formatElevation(ride), left + 20f, yPos, statPaint); yPos += 26f * scale
            canvas.drawText("ELEVATION", left + 20f, yPos, labelPaint)
        }
    }

    private fun drawHorizontalStatsStrip(canvas: Canvas, ride: Ride, motorcycle: Motorcycle?, config: ShareConfig, top: Float, bottom: Float, isDark: Boolean) {
        val w = canvas.width.toFloat()
        val bgColor = if (isDark) Color.parseColor("#B0101114") else Color.parseColor("#B0FFFFFF")
        val textColor = if (isDark) Color.WHITE else Color.parseColor("#101114")
        val accentColor = Color.parseColor("#FF6B35")
        val bgPaint = Paint().apply { color = bgColor; isAntiAlias = true }
        canvas.drawRect(0f, top, w, bottom, bgPaint)

        val stats = buildList {
            if (config.showDistance) add(Pair(formatDistance(ride), "DISTANCE"))
            if (config.showMovingTime) add(Pair(formatDuration(ride), "TIME"))
            if (config.showAvgSpeed) add(Pair(formatAvgSpeed(ride), "AVG"))
            if (config.showMaxSpeed) add(Pair(formatMaxSpeed(ride), "MAX"))
        }
        val colW = w / stats.size.coerceAtLeast(1)
        val midY = (top + bottom) / 2f
        val scale = w / 1080f
        val statPaint = Paint().apply { color = accentColor; isAntiAlias = true; textSize = 52f * scale; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val labelPaint = Paint().apply { color = textColor; isAntiAlias = true; textSize = 26f * scale; textAlign = Paint.Align.CENTER; alpha = 200 }
        stats.forEachIndexed { i, (value, label) ->
            val cx = colW * i + colW / 2f
            canvas.drawText(value, cx, midY - 8f, statPaint)
            canvas.drawText(label, cx, midY + 36f * scale, labelPaint)
        }
    }

    private fun drawCardStatsPanel(canvas: Canvas, ride: Ride, motorcycle: Motorcycle?, config: ShareConfig, top: Float, bottom: Float, w: Float, isDark: Boolean, branded: Boolean) {
        val bgColor = if (isDark) Color.parseColor("#E6101114") else Color.parseColor("#E6FFFFFF")
        val textColor = if (isDark) Color.WHITE else Color.parseColor("#101114")
        val accentColor = Color.parseColor("#FF6B35")
        val bgPaint = Paint().apply { color = bgColor }
        canvas.drawRect(0f, top, w, bottom, bgPaint)

        // Separator line
        val lineP = Paint().apply { color = accentColor; strokeWidth = 3f * (w/1080f) }
        canvas.drawLine(40f, top, w - 40f, top, lineP)

        val scale = w / 1080f
        val titlePaint = Paint().apply { color = textColor; isAntiAlias = true; textSize = 36f * scale; typeface = Typeface.DEFAULT_BOLD }
        val subtitlePaint = Paint().apply { color = textColor; isAntiAlias = true; textSize = 26f * scale; alpha = 180 }
        val accentPaint = Paint().apply { color = accentColor; isAntiAlias = true; textSize = 52f * scale; typeface = Typeface.DEFAULT_BOLD }

        // Title (ride name)
        canvas.drawText(ride.title.take(30), 60f, top + 60f * scale, titlePaint)

        // Stats row
        var xPos = 60f
        val statsY = top + 130f * scale
        val statsRow = buildList {
            if (config.showDistance) add(Pair(formatDistance(ride), "km"))
            if (config.showMovingTime) add(Pair(formatDuration(ride), "time"))
            if (config.showAvgSpeed) add(Pair(formatAvgSpeed(ride), "avg"))
            if (config.showMaxSpeed) add(Pair(formatMaxSpeed(ride), "top"))
        }
        statsRow.forEach { (value, label) ->
            canvas.drawText(value, xPos, statsY, accentPaint)
            canvas.drawText(label, xPos, statsY + 32f * scale, subtitlePaint)
            xPos += w / (statsRow.size + 0.5f)
        }

        // Date and motorcycle
        var metaY = top + 190f * scale
        if (config.showDate) canvas.drawText(formatDate(ride), 60f, metaY, subtitlePaint).also { metaY += 32f * scale }
        if (config.showMotorcycle && motorcycle != null) canvas.drawText(motorcycle.displayName, 60f, metaY, subtitlePaint)

        if (branded) {
            val brandP = Paint().apply { color = accentColor; isAntiAlias = true; textSize = 32f * scale; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
            canvas.drawText("RYDA", w - 60f, top + 60f * scale, brandP)
        }
    }

    private fun drawRydaWordmark(canvas: Canvas, w: Float, isDark: Boolean) {
        val paint = Paint().apply {
            color = if (isDark) Color.parseColor("#CCFFFFFF") else Color.parseColor("#CC101114")
            isAntiAlias = true; textSize = 32f * (w / 1080f); typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT; alpha = 200
        }
        canvas.drawText("RYDA", w - 40f * (w/1080f), 60f * (w/1080f), paint)
    }

    private fun formatDistance(ride: Ride) = String.format("%.1f km", ride.totalDistanceKm)
    private fun formatDuration(ride: Ride) = ride.formattedDuration(ride.movingTimeSeconds)
    private fun formatAvgSpeed(ride: Ride) = String.format("%.0f km/h", ride.avgSpeedKmh)
    private fun formatMaxSpeed(ride: Ride) = String.format("%.0f km/h", ride.maxSpeedKmh)
    private fun formatElevation(ride: Ride) = String.format("+%.0f m", ride.elevationGainMeters)
    private fun formatDate(ride: Ride): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(java.util.Date(ride.startTime))
}
