package com.ryda.app.core.location

import com.ryda.app.core.models.GpsPoint

class GpsFilter(
    private val maxAcceptableAccuracyMeters: Float = 25.0f,
    private val maxPlausibleSpeedKmh: Double = 180.0
) {
    private var lastValidPoint: GpsPoint? = null

    fun reset() {
        lastValidPoint = null
    }

    /**
     * Checks if a GPS fix is plausible and acceptable.
     */
    fun isPointValid(point: GpsPoint): Boolean {
        // Filter 1: Check accuracy
        if (point.accuracy <= 0f || point.accuracy > maxAcceptableAccuracyMeters) {
            return false
        }

        // Filter 2: Check reported speed
        if (point.speedKmh > maxPlausibleSpeedKmh) {
            return false
        }

        val previous = lastValidPoint
        if (previous != null) {
            val timeDeltaSeconds = (point.timestamp - previous.timestamp) / 1000.0
            if (timeDeltaSeconds > 0) {
                val distance = Haversine.distanceInMeters(
                    previous.latitude, previous.longitude,
                    point.latitude, point.longitude
                )
                val calculatedSpeedKmh = (distance / timeDeltaSeconds) * 3.6
                // Filter 3: Check calculated jump speed
                if (calculatedSpeedKmh > maxPlausibleSpeedKmh) {
                    return false
                }
            }
        }

        lastValidPoint = point
        return true
    }
}
