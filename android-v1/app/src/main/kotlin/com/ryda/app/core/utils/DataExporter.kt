package com.ryda.app.core.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ryda.app.core.database.RydaDatabaseHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DataExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun exportAllDataJson(context: Context): Uri {
        val db = RydaDatabaseHelper.getInstance(context)
        val profile = db.getRiderProfile()
        val bikes = db.getAllMotorcycles()
        val rides = db.getAllCompletedRides()

        val root = JSONObject().apply {
            put("export_format", "RYDA_USER_DATA_V1")
            put("exported_at", isoFormat.format(Date()))

            // Profile
            put("profile", JSONObject().apply {
                put("display_name", profile.displayName)
                put("bio", profile.bio)
                put("city", profile.city)
                put("avatar_url", profile.avatarUrl)
                put("is_profile_public", profile.isProfilePublic)
                put("total_rides", profile.totalRides)
                put("total_distance_km", profile.totalDistanceKm)
                put("total_time_hours", profile.totalTimeHours)
            })

            // Garage
            val bikesArray = JSONArray()
            bikes.forEach { bike ->
                bikesArray.put(JSONObject().apply {
                    put("id", bike.id)
                    put("brand", bike.brand)
                    put("model", bike.model)
                    put("variant", bike.variant)
                    put("year", bike.year)
                    put("engine_cc", bike.engineCc)
                    put("nickname", bike.nickname)
                    put("is_primary", bike.isPrimary)
                })
            }
            put("motorcycles", bikesArray)

            // Rides
            val ridesArray = JSONArray()
            rides.forEach { ride ->
                val rideObj = JSONObject().apply {
                    put("id", ride.id)
                    put("title", ride.title)
                    put("motorcycle_id", ride.motorcycleId)
                    put("start_time", isoFormat.format(Date(ride.startTime)))
                    put("end_time", ride.endTime?.let { isoFormat.format(Date(it)) } ?: "")
                    put("total_distance_km", ride.totalDistanceKm)
                    put("total_distance_meters", ride.totalDistanceMeters)
                    put("moving_time_seconds", ride.movingTimeSeconds)
                    put("elapsed_time_seconds", ride.elapsedTimeSeconds)
                    put("max_speed_kmh", ride.maxSpeedKmh)
                    put("avg_speed_kmh", ride.avgSpeedKmh)
                    put("elevation_gain_meters", ride.elevationGainMeters)
                    put("is_private", ride.isPrivate)
                    put("sync_status", ride.syncStatus.name)

                    val points = db.getPointsForRide(ride.id)
                    val pointsArray = JSONArray()
                    points.forEach { pt ->
                        pointsArray.put(JSONObject().apply {
                            put("seq", pt.sequenceNumber)
                            put("lat", pt.latitude)
                            put("lon", pt.longitude)
                            put("alt", pt.altitude)
                            put("speed_kmh", pt.speedKmh)
                            put("accuracy", pt.accuracy)
                            put("time", isoFormat.format(Date(pt.timestamp)))
                        })
                    }
                    put("points_count", points.size)
                    put("points", pointsArray)
                }
                ridesArray.put(rideObj)
            }
            put("rides", ridesArray)
            put("total_rides_count", rides.size)
        }

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val file = File(exportDir, "RYDA_Data_Export_${System.currentTimeMillis()}.json")
        file.writeText(root.toString(2), Charsets.UTF_8)

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun shareDataExport(context: Context) {
        val uri = exportAllDataJson(context)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RYDA Account & Ride Data Export")
            putExtra(Intent.EXTRA_TEXT, "Here is your exported RYDA motorcycle activity and profile dataset.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Export All My Data via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
