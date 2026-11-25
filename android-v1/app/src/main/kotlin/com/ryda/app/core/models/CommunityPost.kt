package com.ryda.app.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RideTelemetrySnippet(
    val id: String,
    val title: String,
    @SerialName("total_distance_km") val totalDistanceKm: Double = 0.0,
    @SerialName("moving_time_seconds") val movingTimeSeconds: Long = 0L,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double = 0.0,
    @SerialName("motorcycle_name") val motorcycleName: String = "Motorcycle"
)

@Serializable
data class PostComment(
    val id: String,
    @SerialName("user_id") val userId: Int = 0,
    val username: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val text: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class CommunityPost(
    val id: String,
    @SerialName("author_id") val authorId: Int,
    @SerialName("author_username") val authorUsername: String,
    @SerialName("author_display_name") val authorDisplayName: String = "",
    @SerialName("author_avatar_url") val authorAvatarUrl: String = "",
    @SerialName("author_city") val authorCity: String = "",
    @SerialName("post_type") val postType: String = "RIDE",
    val caption: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("club_id") val clubId: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("ride_telemetry") val rideTelemetry: RideTelemetrySnippet? = null,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("comments_count") val commentsCount: Int = 0,
    @SerialName("is_liked") val isLiked: Boolean = false,
    @SerialName("is_following_author") val isFollowingAuthor: Boolean = false
)

@Serializable
data class PostCreateRequest(
    val ride: String? = null,
    @SerialName("post_type") val postType: String = "THOUGHT",
    val caption: String,
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("club_id") val clubId: String? = null
)

@Serializable
data class LikeResponse(
    @SerialName("post_id") val postId: String,
    @SerialName("is_liked") val isLiked: Boolean,
    @SerialName("likes_count") val likesCount: Int
)

@Serializable
data class FollowResponse(
    @SerialName("target_user_id") val targetUserId: Int,
    @SerialName("is_following") val isFollowing: Boolean,
    @SerialName("followers_count") val followersCount: Int
)
