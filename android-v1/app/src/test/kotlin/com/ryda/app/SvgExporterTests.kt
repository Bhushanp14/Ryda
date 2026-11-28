package com.ryda.app

import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.Ride
import com.ryda.app.core.sharing.ColorTheme
import com.ryda.app.core.sharing.GraphicStyle
import com.ryda.app.core.sharing.ShareConfig
import com.ryda.app.core.sharing.SvgExporter
import com.ryda.app.core.sharing.SvgLayoutTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class SvgExporterTests {

    private fun parseSvg(svgXml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(svgXml.toByteArray(Charsets.UTF_8)))
    }

    private fun sampleRide(): Ride {
        return Ride(
            id = "test-ride-uuid-1234",
            title = "Mumbai to Lonavala Express",
            totalDistanceMeters = 84500.0,
            movingTimeSeconds = 5400L,
            maxSpeedKmh = 112.5,
            avgSpeedKmh = 56.3
        )
    }

    private fun makePoint(lat: Double, lon: Double, speed: Double = 0.0, seq: Long = 1L): GpsPoint {
        return GpsPoint(
            rideId = "test-ride-uuid-1234",
            sequenceNumber = seq,
            latitude = lat,
            longitude = lon,
            speedKmh = speed
        )
    }

    private fun sampleGpsPoints(): List<GpsPoint> {
        return listOf(
            makePoint(18.9894, 73.1175, 40.0, 1L),
            makePoint(18.9054, 73.2355, 80.0, 2L),
            makePoint(18.8352, 73.2981, 112.5, 3L),
            makePoint(18.7502, 73.4065, 45.0, 4L)
        )
    }

    @Test
    fun svgExporter_producesValidXml_forAllFiveTemplates() {
        val ride = sampleRide()
        val points = sampleGpsPoints()

        for (template in SvgLayoutTemplate.entries) {
            val config = ShareConfig(
                template = template,
                style = GraphicStyle.TRANSPARENT,
                colorTheme = ColorTheme.RYDA_ORANGE,
                startLocationName = "Panvel",
                destinationName = "Lonavala"
            )

            val svgXml = SvgExporter.exportToSvg(ride, points, config)

            assertTrue("SVG should start with XML declaration", svgXml.startsWith("<?xml"))
            assertTrue("SVG should contain closing svg tag", svgXml.endsWith("</svg>"))

            // Verify XML parser parses without any syntax or character error
            val doc = parseSvg(svgXml)
            val root = doc.documentElement
            assertEquals("svg", root.localName ?: root.tagName)
            assertEquals("0 0 ${template.viewBoxWidth} ${template.viewBoxHeight}", root.getAttribute("viewBox"))
            assertEquals(template.viewBoxWidth.toString(), root.getAttribute("width"))
            assertEquals(template.viewBoxHeight.toString(), root.getAttribute("height"))
            assertEquals("http://www.w3.org/2000/svg", root.namespaceURI)
        }
    }

    @Test
    fun svgExporter_producesTransparentSvg_withoutBackgroundRectOrRasterImage() {
        val ride = sampleRide()
        val points = sampleGpsPoints()
        val config = ShareConfig(
            template = SvgLayoutTemplate.HERO_DISTANCE,
            style = GraphicStyle.TRANSPARENT,
            colorTheme = ColorTheme.RYDA_ORANGE
        )

        val svgXml = SvgExporter.exportToSvg(ride, points, config)
        val doc = parseSvg(svgXml)

        // Genuine transparency check: NO <rect> elements at all
        val rects = doc.getElementsByTagName("rect")
        assertEquals("Must not contain any background <rect> elements", 0, rects.length)

        // NO embedded raster images (<image> elements)
        val images = doc.getElementsByTagName("image")
        assertEquals("Must not contain any <image> elements", 0, images.length)

        // Raw XML checks
        assertFalse("Must not contain base64 png data", svgXml.contains("data:image/png"))
        assertFalse("Must not contain base64 jpeg data", svgXml.contains("data:image/jpeg"))
    }

    @Test
    fun svgExporter_containsVectorRoutePath_andMarkers() {
        val ride = sampleRide()
        val points = sampleGpsPoints()
        val config = ShareConfig(
            template = SvgLayoutTemplate.HERO_DISTANCE,
            style = GraphicStyle.TRANSPARENT,
            colorTheme = ColorTheme.RYDA_ORANGE
        )

        val svgXml = SvgExporter.exportToSvg(ride, points, config)
        val doc = parseSvg(svgXml)

        // Vector path for route
        val paths = doc.getElementsByTagName("path")
        assertEquals(1, paths.length)
        val pathElem = paths.item(0) as Element
        val d = pathElem.getAttribute("d")
        assertTrue("Path d attribute must start with M", d.startsWith("M "))
        assertTrue("Path d attribute must contain line segments L", d.contains(" L "))
        assertEquals("none", pathElem.getAttribute("fill"))
        assertEquals(ColorTheme.RYDA_ORANGE.primaryHex, pathElem.getAttribute("stroke"))
        assertEquals("round", pathElem.getAttribute("stroke-linecap"))
        assertEquals("round", pathElem.getAttribute("stroke-linejoin"))

        // Start and finish circle markers
        val circles = doc.getElementsByTagName("circle")
        assertEquals(2, circles.length)
        val startCircle = circles.item(0) as Element
        val finishCircle = circles.item(1) as Element
        assertEquals(ColorTheme.RYDA_ORANGE.secondaryHex, startCircle.getAttribute("fill"))
        assertEquals(ColorTheme.RYDA_ORANGE.primaryHex, finishCircle.getAttribute("fill"))
        assertEquals("7", startCircle.getAttribute("r"))
        assertEquals("7", finishCircle.getAttribute("r"))
    }

    @Test
    fun svgExporter_containsStatistics_asSvgTextElements() {
        val ride = sampleRide()
        val points = sampleGpsPoints()
        val config = ShareConfig(
            template = SvgLayoutTemplate.HERO_DISTANCE,
            style = GraphicStyle.TRANSPARENT,
            colorTheme = ColorTheme.RYDA_ORANGE
        )

        val svgXml = SvgExporter.exportToSvg(ride, points, config)
        val doc = parseSvg(svgXml)

        val textElements = doc.getElementsByTagName("text")
        assertTrue("Should contain multiple text elements", textElements.length >= 6)

        var foundDistance = false
        var foundTime = false
        var foundAvg = false
        var foundTop = false

        for (i in 0 until textElements.length) {
            val content = textElements.item(i).textContent
            if (content.contains("84.5")) foundDistance = true
            if (content.contains("01:30:00")) foundTime = true
            if (content.contains("56.3")) foundAvg = true
            if (content.contains("112.5")) foundTop = true
        }

        assertTrue("Distance value 84.5 must be present in SVG text", foundDistance)
        assertTrue("Time value 01:30:00 must be present in SVG text", foundTime)
        assertTrue("Avg speed value 56.3 must be present in SVG text", foundAvg)
        assertTrue("Top speed value 112.5 must be present in SVG text", foundTop)
    }

    @Test
    fun svgExporter_handlesEmptyGpsPoints_safely() {
        val ride = sampleRide()
        val points = emptyList<GpsPoint>()
        val config = ShareConfig(
            template = SvgLayoutTemplate.HERO_DISTANCE,
            style = GraphicStyle.TRANSPARENT
        )

        val svgXml = SvgExporter.exportToSvg(ride, points, config)
        val doc = parseSvg(svgXml)

        // No path or circle markers, but still valid SVG with statistics
        assertEquals(0, doc.getElementsByTagName("path").length)
        assertEquals(0, doc.getElementsByTagName("circle").length)
        assertTrue(doc.getElementsByTagName("text").length > 0)
    }

    @Test
    fun svgExporter_handlesSingleGpsPoint_safely() {
        val ride = sampleRide()
        val points = listOf(makePoint(18.9894, 73.1175))
        val config = ShareConfig(
            template = SvgLayoutTemplate.HERO_DISTANCE,
            style = GraphicStyle.TRANSPARENT
        )

        val svgXml = SvgExporter.exportToSvg(ride, points, config)
        val doc = parseSvg(svgXml)

        assertEquals(0, doc.getElementsByTagName("path").length)
        assertEquals(1, doc.getElementsByTagName("circle").length)
    }

    @Test
    fun svgExporter_escapesSpecialXmlCharacters_inTitlesAndLocations() {
        val ride = sampleRide().copy(title = "Biker & <Fast> 'Racer' \"Tour\"")
        val points = sampleGpsPoints()
        val config = ShareConfig(
            template = SvgLayoutTemplate.HERO_DISTANCE,
            style = GraphicStyle.TRANSPARENT,
            startLocationName = "Cafe <Start> & Snack",
            destinationName = "Viewpoint \"High\" 'Overlook'"
        )

        val svgXml = SvgExporter.exportToSvg(ride, points, config)

        // Raw XML must contain escaped entities, never raw unescaped < or &
        assertTrue(svgXml.contains("&amp;"))
        assertTrue(svgXml.contains("&lt;"))
        assertTrue(svgXml.contains("&gt;"))
        assertTrue(svgXml.contains("&quot;"))
        assertTrue(svgXml.contains("&apos;"))

        // Document parser must parse successfully without fatal SAXParseException
        val doc = parseSvg(svgXml)
        assertNotNull(doc)

        // DOM node content should decode back to original text
        val headerText = doc.getElementsByTagName("text").item(0).textContent
        assertTrue(headerText.contains("<Start>"))
        assertTrue(headerText.contains("&"))
        assertTrue(headerText.contains("\"High\""))
    }

    @Test
    fun svgExporter_handlesLongRouteNames_safely() {
        val longName = "A".repeat(400)
        val ride = sampleRide().copy(title = longName)
        val points = sampleGpsPoints()
        val config = ShareConfig(
            template = SvgLayoutTemplate.CLASSIC_CARD,
            style = GraphicStyle.TRANSPARENT,
            startLocationName = longName,
            destinationName = longName
        )

        val svgXml = SvgExporter.exportToSvg(ride, points, config)
        val doc = parseSvg(svgXml)
        assertNotNull(doc)
    }

    @Test
    fun svgExporter_handlesExtremeAspectRatios_wideAndTall() {
        val ride = sampleRide()

        // Very wide route (east-west highway across longitude)
        val widePoints = listOf(
            makePoint(18.0001, 72.0, seq = 1L),
            makePoint(18.0002, 75.0, seq = 2L)
        )
        val wideSvg = SvgExporter.exportToSvg(ride, widePoints, ShareConfig(template = SvgLayoutTemplate.SPLIT_PANEL))
        val wideDoc = parseSvg(wideSvg)
        assertEquals(1, wideDoc.getElementsByTagName("path").length)

        // Very tall route (north-south coastal corridor across latitude)
        val tallPoints = listOf(
            makePoint(15.0, 73.0001, seq = 1L),
            makePoint(21.0, 73.0002, seq = 2L)
        )
        val tallSvg = SvgExporter.exportToSvg(ride, tallPoints, ShareConfig(template = SvgLayoutTemplate.MINIMALIST_STORY))
        val tallDoc = parseSvg(tallSvg)
        assertEquals(1, tallDoc.getElementsByTagName("path").length)
    }

    @Test
    fun svgExporter_writesSampleFile_forInspection() {
        val ride = sampleRide()
        val points = sampleGpsPoints()
        val config = ShareConfig(
            template = SvgLayoutTemplate.HERO_DISTANCE,
            style = GraphicStyle.TRANSPARENT,
            colorTheme = ColorTheme.RYDA_ORANGE,
            startLocationName = "Panvel",
            destinationName = "Lonavala"
        )
        val svgXml = SvgExporter.exportToSvg(ride, points, config)
        java.io.File("build/sample_hero_distance.svg").writeText(svgXml)
        assertTrue(svgXml.contains("viewBox=\"0 0 420 470\""))
    }

    @Test
    fun svgExporter_containsVectorRoutePath_acrossAllFiveTemplates() {
        val ride = sampleRide()
        val points = sampleGpsPoints()

        for (template in SvgLayoutTemplate.entries) {
            val config = ShareConfig(
                template = template,
                style = GraphicStyle.TRANSPARENT,
                colorTheme = ColorTheme.RYDA_ORANGE
            )

            val svgXml = SvgExporter.exportToSvg(ride, points, config)
            val doc = parseSvg(svgXml)

            val paths = doc.getElementsByTagName("path")
            assertEquals("Template ${template.title} must contain 1 vector path", 1, paths.length)

            val pathElem = paths.item(0) as Element
            val d = pathElem.getAttribute("d")
            assertTrue("Path d must start with M in ${template.title}", d.startsWith("M "))

            val circles = doc.getElementsByTagName("circle")
            assertEquals("Template ${template.title} must contain 2 markers", 2, circles.length)
        }
    }
}
