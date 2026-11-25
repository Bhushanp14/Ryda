package com.ryda.app.core.models

data class RiderProfile(
    val id: String = "rider_local_1",
    val displayName: String = "Ryda Rider",
    val bio: String = "Motorcycle enthusiast exploring the highways and mountain passes.",
    val city: String = "Bengaluru, India",
    val avatarUrl: String = "",
    val isProfilePublic: Boolean = false,
    val totalRides: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalTimeHours: Double = 0.0,
    val activeTagId: String? = null,
    val activeTagName: String? = null
)
