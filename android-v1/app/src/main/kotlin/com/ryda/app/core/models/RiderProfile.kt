package com.ryda.app.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RiderProfile(
    val id: String = "rider_local_1",
    val username: String = "rider",
    @SerialName("display_name") val displayName: String = "Ryda Rider",
    val bio: String = "Motorcycle enthusiast exploring the highways and mountain passes.",
    val city: String = "Bengaluru, India",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("date_joined") val dateJoined: String = "Jan 2024",
    @SerialName("is_profile_public") val isProfilePublic: Boolean = false,
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("total_rides") val totalRides: Int = 0,
    @SerialName("total_distance_km") val totalDistanceKm: Double = 0.0,
    @SerialName("total_time_hours") val totalTimeHours: Double = 0.0,
    @SerialName("longest_ride_km") val longestRideKm: Double = 0.0,
    @SerialName("highest_monthly_distance_km") val highestMonthlyDistanceKm: Double = 0.0,
    @SerialName("most_rides_monthly") val mostRidesMonthly: Int = 0,
    @SerialName("longest_duration_seconds") val longestDurationSeconds: Long = 0L,
    @SerialName("active_tag_id") val activeTagId: String? = null,
    @SerialName("active_tag_name") val activeTagName: String? = null,
    @SerialName("active_tag_ids") val activeTagIds: List<String> = listOfNotNull(activeTagId ?: "weekend_rider"),
    @SerialName("showcased_achievement_ids") val showcasedAchievementIds: List<String> = emptyList()
)
