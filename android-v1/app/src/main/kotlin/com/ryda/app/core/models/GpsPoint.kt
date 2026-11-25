package com.ryda.app.core.models

data class GpsPoint(
    val id: Long = 0,
    val rideId: String,
    val sequenceNumber: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speedKmh: Double = 0.0
)
