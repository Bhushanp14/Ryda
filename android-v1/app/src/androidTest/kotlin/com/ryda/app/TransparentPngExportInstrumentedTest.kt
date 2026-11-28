package com.ryda.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.Ride
import com.ryda.app.core.sharing.ColorTheme
import com.ryda.app.core.sharing.GraphicStyle
import com.ryda.app.core.sharing.RideShareRenderer
import com.ryda.app.core.sharing.ShareConfig
import com.ryda.app.core.sharing.ShareHelper
import com.ryda.app.core.sharing.SvgLayoutTemplate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class TransparentPngExportInstrumentedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun sampleRide(): Ride {
        return Ride(
            id = "verify-ride-9999",
            title = "Mumbai to Lonavala Express",
            totalDistanceMeters = 84500.0,
            movingTimeSeconds = 5400L,
            maxSpeedKmh = 112.5,
            avgSpeedKmh = 56.3
        )
    }

    private fun sampleGpsPoints(): List<GpsPoint> {
        return listOf(
            GpsPoint(rideId = "verify-ride-9999", sequenceNumber = 1L, latitude = 18.9894, longitude = 73.1175, speedKmh = 40.0),
            GpsPoint(rideId = "verify-ride-9999", sequenceNumber = 2L, latitude = 18.9054, longitude = 73.2355, speedKmh = 80.0),
            GpsPoint(rideId = "verify-ride-9999", sequenceNumber = 3L, latitude = 18.8352, longitude = 73.2981, speedKmh = 112.5),
            GpsPoint(rideId = "verify-ride-9999", sequenceNumber = 4L, latitude = 18.7502, longitude = 73.4065, speedKmh = 45.0)
        )
    }

    @Test
    fun verifyTransparentPng_containsTrueAlphaChannel_andSurvivesExport() = runBlocking {
        val ride = sampleRide()
        val points = sampleGpsPoints()
        val config = ShareConfig(
            template = SvgLayoutTemplate.HERO_DISTANCE,
            style = GraphicStyle.TRANSPARENT,
            colorTheme = ColorTheme.RYDA_ORANGE,
            startLocationName = "Panvel",
            destinationName = "Lonavala"
        )

        // 1. Render transparent graphic in memory
        val renderedBitmap = RideShareRenderer.renderShareGraphic(
            ride = ride,
            points = points,
            config = config,
            mapBitmap = null
        )

        assertNotNull("Rendered bitmap must not be null", renderedBitmap)
        assertEquals("Bitmap must use ARGB_8888 for alpha support", Bitmap.Config.ARGB_8888, renderedBitmap.config)
        assertTrue("Bitmap must have alpha enabled", renderedBitmap.hasAlpha())

        val width = renderedBitmap.width
        val height = renderedBitmap.height
        assertEquals(1050, width) // 420 * 2.5
        assertEquals(1175, height) // 470 * 2.5

        // 2. Export through ShareHelper saving pipeline
        val filename = "test_verified_transparent_${System.currentTimeMillis()}.png"
        val cacheUri = ShareHelper.saveBitmapToCache(context, renderedBitmap, filename, isTransparent = true)
        assertNotNull("Cache URI must not be null", cacheUri)

        val file = File(context.cacheDir, "sharing/$filename")
        assertTrue("Exported file must exist on disk", file.exists())
        assertTrue("File extension must be .png", file.name.endsWith(".png"))

        // 3. Verify raw file format is genuine PNG (Magic bytes: 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A)
        val fileBytes = file.readBytes()
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte())
        assertTrue("File must start with PNG magic header", fileBytes.take(8).toByteArray().contentEquals(pngMagic))

        // 4. Reopen and inspect the exported file from disk
        val decodedBitmap = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull("Decoded bitmap from file must not be null", decodedBitmap)
        assertEquals("Decoded bitmap must match dimensions", width, decodedBitmap.width)
        assertEquals("Decoded bitmap must match dimensions", height, decodedBitmap.height)
        assertTrue("Decoded bitmap must preserve alpha channel", decodedBitmap.hasAlpha())

        // 5. Pixel-by-pixel inspection of the alpha channel
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        decodedBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var minAlpha = 255
        var maxAlpha = 0
        var fullyTransparentCount = 0
        var semiTransparentCount = 0
        var fullyOpaqueCount = 0

        for (p in pixels) {
            val alpha = Color.alpha(p)
            if (alpha < minAlpha) minAlpha = alpha
            if (alpha > maxAlpha) maxAlpha = alpha

            when (alpha) {
                0 -> fullyTransparentCount++
                255 -> fullyOpaqueCount++
                else -> semiTransparentCount++
            }
        }

        val visibleCount = fullyOpaqueCount + semiTransparentCount
        val transparentPercent = (fullyTransparentCount.toDouble() / totalPixels.toDouble()) * 100.0

        println("=== TRANSPARENT PNG EXPORT VERIFICATION REPORT ===")
        println("Dimensions: ${width}x${height} (${totalPixels} total pixels)")
        println("Minimum Alpha: $minAlpha")
        println("Maximum Alpha: $maxAlpha")
        println("Fully Transparent Pixels (alpha = 0): $fullyTransparentCount (${String.format(java.util.Locale.US, "%.2f", transparentPercent)}%)")
        println("Visible Foreground Pixels (alpha > 0): $visibleCount (${String.format(java.util.Locale.US, "%.2f", 100.0 - transparentPercent)}%)")
        println("Fully Opaque Pixels (alpha = 255): $fullyOpaqueCount")
        println("Semi-transparent Anti-aliased Pixels (1..254): $semiTransparentCount")
        println("==================================================")

        // Mandatory Assertions
        assertEquals("Minimum alpha MUST be 0 (genuine transparency)", 0, minAlpha)
        assertEquals("Maximum alpha MUST be 255 (visible foreground)", 255, maxAlpha)
        assertTrue("Over 60% of pixels must be fully transparent in sticker layout", transparentPercent > 60.0)
        assertTrue("Visible foreground pixels must exist for route and text", visibleCount > 5000)

        // Write verification metrics JSON to filesDir for external validation
        val resultJson = """
        {
            "filename": "$filename",
            "width": $width,
            "height": $height,
            "totalPixels": $totalPixels,
            "minAlpha": $minAlpha,
            "maxAlpha": $maxAlpha,
            "fullyTransparentPixels": $fullyTransparentCount,
            "transparentPercentage": ${String.format(java.util.Locale.US, "%.2f", transparentPercent)},
            "visiblePixels": $visibleCount,
            "semiTransparentPixels": $semiTransparentCount,
            "fullyOpaquePixels": $fullyOpaqueCount
        }
        """.trimIndent()
        File(context.filesDir, "png_verification_report.json").writeText(resultJson)

        // 6. Verification: Layer PNG over a simulated scenic photograph
        val photoBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val photoCanvas = Canvas(photoBitmap)
        // Draw photo gradient/scenery: Sky blue top, asphalt/road bottom
        val skyPaint = Paint().apply { color = Color.parseColor("#4A90E2") }
        photoCanvas.drawRect(0f, 0f, width.toFloat(), height * 0.5f, skyPaint)
        val roadPaint = Paint().apply { color = Color.parseColor("#2C3E50") }
        photoCanvas.drawRect(0f, height * 0.5f, width.toFloat(), height.toFloat(), roadPaint)

        // Composite the transparent PNG overlay on top of the photo
        photoCanvas.drawBitmap(decodedBitmap, 0f, 0f, null)

        // Save the composited result
        val compositeFile = File(context.filesDir, "test_composited_over_photo.png")
        FileOutputStream(compositeFile).use { out ->
            photoBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Composited photo must be saved", compositeFile.exists())

        // Check that where overlay was transparent (e.g. at pixel 0,0), the photo's sky blue (#4A90E2) is visible!
        val compositePixelAtOrigin = photoBitmap.getPixel(0, 0)
        assertEquals("Where overlay is transparent, underlying photo color must show through", Color.parseColor("#4A90E2"), compositePixelAtOrigin)
    }

    @Test
    fun verifyMapBackground_remainsOpaque() = runBlocking {
        val ride = sampleRide()
        val points = sampleGpsPoints()
        val config = ShareConfig(
            template = SvgLayoutTemplate.HERO_DISTANCE,
            style = GraphicStyle.MAP_BACKGROUND,
            colorTheme = ColorTheme.RYDA_ORANGE
        )

        val renderedBitmap = RideShareRenderer.renderShareGraphic(
            ride = ride,
            points = points,
            config = config,
            mapBitmap = null
        )

        val filename = "test_map_opaque_${System.currentTimeMillis()}.jpg"
        val cacheUri = ShareHelper.saveBitmapToCache(context, renderedBitmap, filename, isTransparent = false)
        assertNotNull(cacheUri)

        val file = File(context.cacheDir, "sharing/$filename")
        assertTrue(file.exists())

        // Decoded map background must have NO transparent pixels (min alpha = 255)
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
        val pixels = IntArray(decoded.width * decoded.height)
        decoded.getPixels(pixels, 0, decoded.width, 0, 0, decoded.width, decoded.height)

        val minAlpha = pixels.minOf { Color.alpha(it) }
        assertEquals("Map background must be completely opaque (alpha = 255)", 255, minAlpha)
    }

    @Test
    fun verifyRouteIsVisible_acrossAllFiveTemplates() = runBlocking {
        val ride = sampleRide()
        val points = sampleGpsPoints()

        for (template in SvgLayoutTemplate.entries) {
            val config = ShareConfig(
                template = template,
                style = GraphicStyle.TRANSPARENT,
                colorTheme = ColorTheme.RYDA_ORANGE
            )

            val bitmap = RideShareRenderer.renderShareGraphic(
                ride = ride,
                points = points,
                config = config,
                mapBitmap = null
            )

            assertNotNull("Rendered bitmap must not be null for ${template.title}", bitmap)
            assertTrue("Bitmap has alpha", bitmap.hasAlpha())

            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            val primaryColor = Color.parseColor(ColorTheme.RYDA_ORANGE.primaryHex)
            val secondaryColor = Color.parseColor(ColorTheme.RYDA_ORANGE.secondaryHex)

            // Verify route primary color pixels and secondary color start pin pixels exist
            var primaryColorCount = 0
            var secondaryColorCount = 0
            for (p in pixels) {
                if (p == primaryColor) primaryColorCount++
                if (p == secondaryColor) secondaryColorCount++
            }

            println("Template ${template.title}: primaryColorCount=$primaryColorCount, secondaryColorCount=$secondaryColorCount")
            assertTrue("Template ${template.title} must have visible route primary color pixels", primaryColorCount > 100)
            assertTrue("Template ${template.title} must have start marker secondary color pixels", secondaryColorCount > 10)
        }
    }

    @Test
    fun verifyMapBackground_rendersRouteOnFallback() = runBlocking {
        val ride = sampleRide()
        val points = sampleGpsPoints()
        val config = ShareConfig(
            template = SvgLayoutTemplate.HERO_DISTANCE,
            style = GraphicStyle.MAP_BACKGROUND,
            colorTheme = ColorTheme.RYDA_ORANGE
        )

        // When mapBitmap is null (e.g. before snapshot completes), fallback draws grid AND route
        val bitmap = RideShareRenderer.renderShareGraphic(
            ride = ride,
            points = points,
            config = config,
            mapBitmap = null
        )

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val primaryColor = Color.parseColor(ColorTheme.RYDA_ORANGE.primaryHex)
        val secondaryColor = Color.parseColor(ColorTheme.RYDA_ORANGE.secondaryHex)

        var primaryColorCount = 0
        var secondaryColorCount = 0
        for (p in pixels) {
            if (p == primaryColor) primaryColorCount++
            if (p == secondaryColor) secondaryColorCount++
        }

        assertTrue("Fallback map background must render route line", primaryColorCount > 100)
        assertTrue("Fallback map background must render start marker", secondaryColorCount > 10)
    }
}
