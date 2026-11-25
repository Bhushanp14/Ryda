package com.ryda.app.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RidingEvent(
    val id: String,
    @SerialName("club_id") val clubId: String = "",
    @SerialName("club_name") val clubName: String = "",
    val title: String,
    val description: String = "",
    @SerialName("meeting_point") val meetingPoint: String = "",
    val destination: String = "",
    @SerialName("start_time") val startTime: String = "",
    @SerialName("rider_limit") val riderLimit: Int = 35,
    val pace: String = "Intermediate",
    @SerialName("distance_km") val distanceKm: Double = 100.0,
    @SerialName("is_official") val isOfficial: Boolean = true,
    @SerialName("confirmed_count") val confirmedCount: Int = 0,
    @SerialName("interested_count") val interestedCount: Int = 0,
    @SerialName("my_rsvp") val myRsvp: String? = null
)

@Serializable
data class Club(
    val id: String,
    val name: String,
    val slug: String = "",
    val description: String = "",
    val city: String = "Mumbai",
    @SerialName("banner_url") val bannerUrl: String = "",
    @SerialName("logo_url") val logoUrl: String = "",
    @SerialName("is_verified") val isVerified: Boolean = true,
    val policy: String = "OPEN",
    @SerialName("total_mileage_km") val totalMileageKm: Double = 0.0,
    @SerialName("members_count") val membersCount: Int = 0,
    @SerialName("is_member") val isMember: Boolean = false,
    @SerialName("upcoming_events") val upcomingEvents: List<RidingEvent> = emptyList()
)

@Serializable
data class ClubJoinResponse(
    @SerialName("club_id") val clubId: String,
    @SerialName("is_member") val isMember: Boolean,
    @SerialName("members_count") val membersCount: Int
)

@Serializable
data class EventRsvpResponse(
    @SerialName("event_id") val eventId: String,
    @SerialName("my_rsvp") val myRsvp: String?,
    @SerialName("confirmed_count") val confirmedCount: Int,
    @SerialName("interested_count") val interestedCount: Int
)
