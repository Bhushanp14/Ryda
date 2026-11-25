package com.ryda.app.core.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.Ride
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {

    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Converts a Ride and its sequenced GPS points into a standard GPX 1.1 XML string.
     */
    fun generateGpxXml(ride: Ride, points: List<GpsPoint>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"RYDA Motorcycle Activity Platform - Android\"\n")
        sb.append("     xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
        sb.append("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
        sb.append("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        sb.append("  <metadata>\n")
        sb.append("    <name>").append(escapeXml(ride.title)).append("</name>\n")
        sb.append("    <time>").append(iso8601Format.format(Date(ride.startTime))).append("</time>\n")
        sb.append("  </metadata>\n")
        sb.append("  <trk>\n")
        sb.append("    <name>").append(escapeXml(ride.title)).append("</name>\n")
        sb.append("    <type>Motorcycle</type>\n")
        sb.append("    <trkseg>\n")

        for (p in points) {
            sb.append("      <trkpt lat=\"").append(p.latitude).append("\" lon=\"").append(p.longitude).append("\">\n")
            if (p.altitude != 0.0) {
                sb.append("        <ele>").append(String.format(Locale.US, "%.1f", p.altitude)).append("</ele>\n")
            }
            sb.append("        <time>").append(iso8601Format.format(Date(p.timestamp))).append("</time>\n")
            if (p.speedKmh > 0) {
                // Speed in m/s as per GPX extensions
                val speedMps = p.speedKmh / 3.6
                sb.append("        <speed>").append(String.format(Locale.US, "%.2f", speedMps)).append("</speed>\n")
            }
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")

        return sb.toString()
    }

    /**
     * Exports ride to GPX, saves in cache, and returns File and FileProvider content URI.
     */
    fun exportGpxFile(context: Context, ride: Ride, points: List<GpsPoint>): Uri {
        val gpxDir = File(context.cacheDir, "gpx")
        if (!gpxDir.exists()) {
            gpxDir.mkdirs()
        }

        val cleanTitle = ride.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(gpxDir, "RYDA_${cleanTitle}_${ride.id.take(8)}.gpx")
        val xml = generateGpxXml(ride, points)
        file.writeText(xml, Charsets.UTF_8)

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Launches Android Share Sheet with GPX file attached.
     */
    fun shareGpx(context: Context, ride: Ride, points: List<GpsPoint>) {
        val uri = exportGpxFile(context, ride, points)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RYDA Ride: ${ride.title}")
            putExtra(Intent.EXTRA_TEXT, "Recorded with RYDA - ${String.format(Locale.US, "%.1f", ride.totalDistanceKm)} km motorcycle ride.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Export GPX Route via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
