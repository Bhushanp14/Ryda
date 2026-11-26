package com.ryda.app.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClubRideWaypoint(
    val lat: Double,
    val lng: Double,
    val label: String = ""
)

@Serializable
data class ClubRideParticipant(
    val id: String,
    @SerialName("user_id") val userId: Int,
    val username: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val status: String = "JOINED",
    @SerialName("joined_at") val joinedAt: String = "",
    @SerialName("ready_at") val readyAt: String? = null,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("individual_ride_id") val individualRideId: String? = null
)

@Serializable
data class ClubRideSession(
    val id: String,
    @SerialName("club_id") val clubId: String = "",
    val title: String,
    val description: String = "",
    val status: String = "SCHEDULED",
    @SerialName("meeting_point") val meetingPoint: String = "",
    val destination: String = "",
    @SerialName("start_lat") val startLat: Double? = null,
    @SerialName("start_lng") val startLng: Double? = null,
    @SerialName("dest_lat") val destLat: Double? = null,
    @SerialName("dest_lng") val destLng: Double? = null,
    @SerialName("encoded_polyline") val encodedPolyline: String = "",
    @SerialName("route_distance_meters") val routeDistanceMeters: Int = 0,
    @SerialName("route_duration_seconds") val routeDurationSeconds: Int = 0,
    @SerialName("route_summary") val routeSummary: String = "",
    @SerialName("scheduled_start") val scheduledStart: String = "",
    @SerialName("expires_at") val expiresAt: String = "",
    @SerialName("actual_start") val actualStart: String? = null,
    @SerialName("actual_end") val actualEnd: String? = null,
    @SerialName("max_participants") val maxParticipants: Int = 50,
    @SerialName("participant_count") val participantCount: Int = 0,
    @SerialName("my_status") val myStatus: String? = null,
    @SerialName("my_individual_ride_id") val myIndividualRideId: String? = null,
    @SerialName("is_expired") val isExpired: Boolean = false,
    @SerialName("created_by_username") val createdByUsername: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    val waypoints: List<ClubRideWaypoint> = emptyList(),
    val participants: List<ClubRideParticipant>? = null
)

@Serializable
data class CreateClubRideRequest(
    val title: String,
    val description: String = "",
    @SerialName("meeting_point") val meetingPoint: String,
    val destination: String,
    @SerialName("start_lat") val startLat: Double,
    @SerialName("start_lng") val startLng: Double,
    @SerialName("dest_lat") val destLat: Double,
    @SerialName("dest_lng") val destLng: Double,
    @SerialName("encoded_polyline") val encodedPolyline: String,
    @SerialName("route_distance_meters") val routeDistanceMeters: Int,
    @SerialName("route_duration_seconds") val routeDurationSeconds: Int,
    @SerialName("route_summary") val routeSummary: String = "",
    @SerialName("scheduled_start") val scheduledStart: String,
    @SerialName("max_participants") val maxParticipants: Int = 50,
    val waypoints: List<ClubRideWaypoint> = emptyList()
)

@Serializable
data class RouteLatLng(
    val lat: Double,
    val lng: Double,
    val label: String = ""
)

@Serializable
data class ComputeRouteRequest(
    @SerialName("club_id") val clubId: String,
    val origin: RouteLatLng,
    val destination: RouteLatLng,
    val intermediates: List<RouteLatLng> = emptyList(),
    @SerialName("compute_alternatives") val computeAlternatives: Boolean = true,
    @SerialName("travel_mode") val travelMode: String = "TWO_WHEELER"
)

@Serializable
data class ComputedRouteOption(
    val index: Int = 0,
    val summary: String = "",
    @SerialName("distance_meters") val distanceMeters: Int = 0,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    @SerialName("encoded_polyline") val encodedPolyline: String = "",
    @SerialName("is_default") val isDefault: Boolean = false
)

@Serializable
data class ComputeRouteResponse(
    @SerialName("travel_mode") val travelMode: String = "TWO_WHEELER",
    @SerialName("beta_notice") val betaNotice: String = "",
    val routes: List<ComputedRouteOption> = emptyList()
)

@Serializable
data class ClubRideJoinResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("my_status") val myStatus: String? = null,
    @SerialName("participant_count") val participantCount: Int = 0
)

@Serializable
data class ClubRideReadyResponse(
    @SerialName("my_status") val myStatus: String,
    @SerialName("ready_at") val readyAt: String? = null
)

@Serializable
data class ClubRideVerifyResponse(
    @SerialName("user_id") val userId: String,
    val username: String = "",
    val status: String,
    @SerialName("verified_at") val verifiedAt: String? = null
)

@Serializable
data class ClubRideAbsentResponse(
    @SerialName("user_id") val userId: String,
    val status: String
)

@Serializable
data class ClubRideRosterItem(
    @SerialName("user_id") val userId: String,
    val username: String = "",
    @SerialName("ride_id") val rideId: String
)

@Serializable
data class ClubRideStartResponse(
    @SerialName("session_id") val sessionId: String,
    val status: String,
    @SerialName("actual_start") val actualStart: String? = null,
    val roster: List<ClubRideRosterItem> = emptyList()
)

@Serializable
data class ClubRideStatusResponse(
    @SerialName("session_id") val sessionId: String,
    val status: String,
    @SerialName("actual_end") val actualEnd: String? = null
)
