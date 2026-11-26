package com.ryda.app.core.models

import java.util.UUID

enum class RideStatus {
    RECORDING,
    PAUSED,
    COMPLETED,
    DISCARDED
}

enum class SyncStatus {
    LOCAL_ONLY,
    SYNC_PENDING,
    SYNCED,
    SYNC_FAILED
}

enum class MediaSyncStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED
}

data class Ride(
    val id: String = UUID.randomUUID().toString(),
    val motorcycleId: String? = null,
    val title: String = "Morning Motorcycle Ride",
    val status: RideStatus = RideStatus.RECORDING,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val totalDistanceMeters: Double = 0.0,
    val movingTimeSeconds: Long = 0L,
    val elapsedTimeSeconds: Long = 0L,
    val maxSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val mediaSyncStatus: MediaSyncStatus = MediaSyncStatus.PENDING,
    val isPrivate: Boolean = true,
    val photoUri: String? = null,
    val clubRideSessionId: String? = null
) {
    val totalDistanceKm: Double
        get() = totalDistanceMeters / 1000.0

    fun formattedDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
}
