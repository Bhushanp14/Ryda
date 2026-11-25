package com.ryda.app.core.models

data class TrackingState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val currentRideId: String? = null,
    val currentSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val averageSpeedKmh: Double = 0.0,
    val totalDistanceMeters: Double = 0.0,
    val movingTimeSeconds: Long = 0L,
    val elapsedTimeSeconds: Long = 0L,
    val gpsAccuracyMeters: Float = 0f,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val recordedPointsCount: Int = 0,
    val isSimulationMode: Boolean = false
) {
    val totalDistanceKm: Double
        get() = totalDistanceMeters / 1000.0

    val formattedMovingTime: String
        get() {
            val h = movingTimeSeconds / 3600
            val m = (movingTimeSeconds % 3600) / 60
            val s = movingTimeSeconds % 60
            return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
        }

    val formattedElapsedTime: String
        get() {
            val h = elapsedTimeSeconds / 3600
            val m = (elapsedTimeSeconds % 3600) / 60
            val s = elapsedTimeSeconds % 60
            return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
        }
}
