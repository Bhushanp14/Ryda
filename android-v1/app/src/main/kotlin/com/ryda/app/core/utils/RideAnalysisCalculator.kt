package com.ryda.app.core.utils

import com.ryda.app.core.location.Haversine
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.Ride
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class RideTimeAnalysis(
    val startTimeMs: Long,
    val finishTimeMs: Long,
    val movingTimeSeconds: Long,
    val elapsedTimeSeconds: Long,
    val stoppedTimeSeconds: Long,
    val formattedStartTime: String,
    val formattedFinishTime: String
)

data class RideElevationAnalysis(
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val highestElevationMeters: Double,
    val lowestElevationMeters: Double,
    val isElevationAvailable: Boolean
)

data class SpeedDataPoint(
    val elapsedSeconds: Long,
    val speedKmh: Double
)

data class SpeedOverTimeAnalysis(
    val dataPoints: List<SpeedDataPoint>,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val maxSpeedTimeSeconds: Long
)

data class DistanceSplit(
    val segmentIndex: Int,
    val label: String,
    val distanceKm: Double,
    val elapsedSeconds: Long,
    val avgSpeedKmh: Double
) {
    val formattedTime: String
        get() {
            val mins = elapsedSeconds / 60
            val secs = elapsedSeconds % 60
            return String.format(Locale.US, "%02d:%02d", mins, secs)
        }
}

data class ElevationDataPoint(
    val distanceKm: Double,
    val altitudeMeters: Double
)

data class FullRideAnalysis(
    val timeAnalysis: RideTimeAnalysis,
    val elevationAnalysis: RideElevationAnalysis,
    val speedAnalysis: SpeedOverTimeAnalysis,
    val splits: List<DistanceSplit>,
    val elevationProfile: List<ElevationDataPoint>
)

object RideAnalysisCalculator {

    private const val MAX_VALID_SPEED_KMH = 180.0
    private const val MAX_VALID_ACCURACY_METERS = 25.0f
    private const val MIN_ELEVATION_DELTA_METERS = 3.0

    fun calculateAnalysis(ride: Ride, points: List<GpsPoint>): FullRideAnalysis {
        // Points from SQLite are already ordered by sequence_number ASC; avoid unnecessary sorting copy
        val inputPoints = if (isSortedBySequence(points)) points else points.sortedBy { it.sequenceNumber }

        // 1. Time Analysis
        val startTime = ride.startTime
        val finishTime = ride.endTime ?: inputPoints.lastOrNull()?.timestamp ?: startTime
        val rawElapsed = if (inputPoints.isNotEmpty()) {
            max(0L, (inputPoints.last().timestamp - inputPoints.first().timestamp) / 1000L)
        } else {
            max(0L, (finishTime - startTime) / 1000L)
        }
        val elapsedTime = max(ride.elapsedTimeSeconds, rawElapsed)
        val rawMoving = if (ride.movingTimeSeconds > 0) ride.movingTimeSeconds else elapsedTime
        val movingTime = if (elapsedTime > 0) rawMoving.coerceAtMost(elapsedTime) else rawMoving
        val stoppedTime = max(0L, elapsedTime - movingTime)

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val formattedStart = timeFormat.format(Date(startTime))
        val formattedFinish = timeFormat.format(Date(finishTime))

        val timeAnalysis = RideTimeAnalysis(
            startTimeMs = startTime,
            finishTimeMs = finishTime,
            movingTimeSeconds = movingTime,
            elapsedTimeSeconds = elapsedTime,
            stoppedTimeSeconds = stoppedTime,
            formattedStartTime = formattedStart,
            formattedFinishTime = formattedFinish
        )

        // 2. Speed Analysis & Single-pass Speed/Elevation Data Collection
        val pointCount = inputPoints.size
        val speedPoints = ArrayList<SpeedDataPoint>(pointCount)
        val elevationPoints = ArrayList<ElevationDataPoint>(pointCount)

        var maxSpeed = 0.0
        var maxSpeedTimeSec = 0L

        var totalGain = 0.0
        var totalLoss = 0.0
        var highestAlt = -Double.MAX_VALUE
        var lowestAlt = Double.MAX_VALUE
        var validAltCount = 0
        var prevValidAlt: Double? = null

        var cumDistMeters = 0.0
        var lastValidPt: GpsPoint? = null

        if (inputPoints.isNotEmpty()) {
            val baseTime = inputPoints.first().timestamp

            for (pt in inputPoints) {
                // Distance tracking
                if (lastValidPt != null) {
                    val step = Haversine.distanceInMeters(
                        lastValidPt.latitude, lastValidPt.longitude,
                        pt.latitude, pt.longitude
                    )
                    cumDistMeters += step
                }
                lastValidPt = pt

                // Speed filtering & tracking
                if (pt.accuracy <= MAX_VALID_ACCURACY_METERS) {
                    val rawSpeed = pt.speedKmh
                    if (rawSpeed <= MAX_VALID_SPEED_KMH) {
                        val pointElapsed = max(0L, (pt.timestamp - baseTime) / 1000L)
                        speedPoints.add(SpeedDataPoint(pointElapsed, rawSpeed))

                        if (rawSpeed > maxSpeed) {
                            maxSpeed = rawSpeed
                            maxSpeedTimeSec = pointElapsed
                        }
                    }
                }

                // Elevation filtering & tracking (Single-Pass without intermediate list allocation)
                if (pt.accuracy <= MAX_VALID_ACCURACY_METERS && pt.altitude != 0.0) {
                    val alt = pt.altitude
                    validAltCount++

                    if (alt > highestAlt) highestAlt = alt
                    if (alt < lowestAlt) lowestAlt = alt

                    elevationPoints.add(ElevationDataPoint(cumDistMeters / 1000.0, alt))

                    val prevAlt = prevValidAlt
                    if (prevAlt != null) {
                        val delta = alt - prevAlt
                        if (abs(delta) >= MIN_ELEVATION_DELTA_METERS) {
                            if (delta > 0) {
                                totalGain += delta
                            } else {
                                totalLoss += abs(delta)
                            }
                            prevValidAlt = alt
                        }
                    } else {
                        prevValidAlt = alt
                    }
                }
            }
        }

        val finalMaxSpeed = max(ride.maxSpeedKmh, maxSpeed)
        val finalAvgSpeed = if (ride.avgSpeedKmh > 0) {
            ride.avgSpeedKmh
        } else if (movingTime > 0 && ride.totalDistanceMeters > 0) {
            (ride.totalDistanceMeters / movingTime) * 3.6
        } else {
            0.0
        }

        val speedAnalysis = SpeedOverTimeAnalysis(
            dataPoints = speedPoints,
            averageSpeedKmh = finalAvgSpeed,
            maxSpeedKmh = finalMaxSpeed,
            maxSpeedTimeSeconds = maxSpeedTimeSec
        )

        val isElevationValid = validAltCount >= 2 && highestAlt > lowestAlt
        val finalGain = if (isElevationValid) totalGain else max(ride.elevationGainMeters, totalGain)

        val elevationAnalysis = RideElevationAnalysis(
            elevationGainMeters = finalGain,
            elevationLossMeters = totalLoss,
            highestElevationMeters = if (isElevationValid) highestAlt else 0.0,
            lowestElevationMeters = if (isElevationValid) lowestAlt else 0.0,
            isElevationAvailable = isElevationValid
        )

        // 3. Kilometre Distance Splits
        val splits = calculateDistanceSplits(inputPoints, ride.totalDistanceMeters)

        return FullRideAnalysis(
            timeAnalysis = timeAnalysis,
            elevationAnalysis = elevationAnalysis,
            speedAnalysis = speedAnalysis,
            splits = splits,
            elevationProfile = elevationPoints
        )
    }

    private fun isSortedBySequence(points: List<GpsPoint>): Boolean {
        for (i in 0 until points.size - 1) {
            if (points[i].sequenceNumber > points[i + 1].sequenceNumber) {
                return false
            }
        }
        return true
    }

    private fun calculateDistanceSplits(points: List<GpsPoint>, totalDistanceMeters: Double): List<DistanceSplit> {
        if (points.isEmpty()) return emptyList()

        val expectedSplitsCount = (totalDistanceMeters / 1000.0).toInt() + 2
        val splits = ArrayList<DistanceSplit>(expectedSplitsCount)

        var currentSegmentIndex = 1
        var segmentStartDist = 0.0
        var segmentStartTime = points.first().timestamp
        var segmentMovingTimeSec = 0L

        var cumDist = 0.0
        var prevPt = points.first()

        for (i in 1 until points.size) {
            val currPt = points[i]
            if (currPt.accuracy > MAX_VALID_ACCURACY_METERS) continue

            val stepDist = Haversine.distanceInMeters(
                prevPt.latitude, prevPt.longitude,
                currPt.latitude, currPt.longitude
            )
            val dtSec = max(0L, (currPt.timestamp - prevPt.timestamp) / 1000L)

            // Check impossible teleport
            if (dtSec > 0 && (stepDist / dtSec) * 3.6 > MAX_VALID_SPEED_KMH) {
                prevPt = currPt
                continue
            }

            cumDist += stepDist
            if (currPt.speedKmh >= 5.4 && dtSec in 1..30) {
                segmentMovingTimeSec += dtSec
            }

            val targetNextKm = currentSegmentIndex * 1000.0

            if (cumDist >= targetNextKm) {
                val segElapsedSec = max(1L, (currPt.timestamp - segmentStartTime) / 1000L)
                val segDistKm = (targetNextKm - segmentStartDist) / 1000.0
                val segAvgSpeed = (segDistKm / (segElapsedSec / 3600.0)).coerceIn(0.0, MAX_VALID_SPEED_KMH)

                val startKm = currentSegmentIndex - 1
                val endKm = currentSegmentIndex

                splits.add(
                    DistanceSplit(
                        segmentIndex = currentSegmentIndex,
                        label = "$startKm–$endKm km",
                        distanceKm = 1.0,
                        elapsedSeconds = segElapsedSec,
                        avgSpeedKmh = segAvgSpeed
                    )
                )

                currentSegmentIndex++
                segmentStartDist = targetNextKm
                segmentStartTime = currPt.timestamp
                segmentMovingTimeSec = 0L
            }

            prevPt = currPt
        }

        // Remaining partial segment
        val finalDistKm = totalDistanceMeters / 1000.0
        val remainingMeters = cumDist - segmentStartDist
        if (remainingMeters > 50.0) { // Only if at least 50 meters in partial final segment
            val segElapsedSec = max(1L, (points.last().timestamp - segmentStartTime) / 1000L)
            val segDistKm = remainingMeters / 1000.0
            val segAvgSpeed = (segDistKm / (segElapsedSec / 3600.0)).coerceIn(0.0, MAX_VALID_SPEED_KMH)
            val startKm = currentSegmentIndex - 1

            splits.add(
                DistanceSplit(
                    segmentIndex = currentSegmentIndex,
                    label = String.format(Locale.US, "$startKm–%.1f km", finalDistKm),
                    distanceKm = segDistKm,
                    elapsedSeconds = segElapsedSec,
                    avgSpeedKmh = segAvgSpeed
                )
            )
        }

        return splits
    }
}
