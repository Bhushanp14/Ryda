package com.ryda.app.core.network

import com.ryda.app.core.models.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

object LenientIdSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        return try {
            val jsonDecoder = decoder as? JsonDecoder
            if (jsonDecoder != null) {
                val element = jsonDecoder.decodeJsonElement()
                if (element is JsonPrimitive) {
                    element.content
                } else {
                    element.toString()
                }
            } else {
                decoder.decodeString()
            }
        } catch (_: Exception) {
            ""
        }
    }
}

// ── Request/Response DTOs ─────────────────────────────────────────────────────

@Serializable
data class RegisterRequest(val username: String, val email: String = "", val password: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class TokenResponse(val token: String)

@Serializable
data class RideRequest(
    val id: String,           // client UUID
    val title: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("motorcycle") val motorcycleId: String? = null,
    @SerialName("motorcycle_id") val motorcycleIdAlias: String? = null,
    @SerialName("is_private") val isPrivate: Boolean = true,
    @SerialName("total_distance_meters") val totalDistanceMeters: Double = 0.0,
    @SerialName("moving_time_seconds") val movingTimeSeconds: Long = 0L,
    @SerialName("elapsed_time_seconds") val elapsedTimeSeconds: Long = 0L,
    @SerialName("max_speed_kmh") val maxSpeedKmh: Double = 0.0,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double = 0.0,
    @SerialName("elevation_gain_meters") val elevationGainMeters: Double = 0.0,
    @SerialName("elevation_loss_meters") val elevationLossMeters: Double = 0.0
)

@Serializable
data class RideResponse(
    val id: String = "",
    @SerialName("motorcycle") val motorcycleId: String? = null,
    @SerialName("motorcycle_name") val motorcycleName: String? = null,
    val title: String = "",
    val status: String = "COMPLETED",
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("total_distance_meters") val totalDistanceMeters: Double = 0.0,
    @SerialName("total_distance_km") val totalDistanceKm: Double = 0.0,
    @SerialName("moving_time_seconds") val movingTimeSeconds: Long = 0L,
    @SerialName("elapsed_time_seconds") val elapsedTimeSeconds: Long = 0L,
    @SerialName("max_speed_kmh") val maxSpeedKmh: Double = 0.0,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double = 0.0,
    @SerialName("elevation_gain_meters") val elevationGainMeters: Double = 0.0,
    @SerialName("elevation_loss_meters") val elevationLossMeters: Double = 0.0,
    @SerialName("is_private") val isPrivate: Boolean = true,
    @SerialName("image_url") val imageUrl: String? = null,
    val points: List<GpsPointDto> = emptyList(),
    val message: String = ""
)

@Serializable
data class GpsPointDto(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    @SerialName("speed_kmh") val speedKmh: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: String,
    @SerialName("sequence_number") val sequenceNumber: Int
)

@Serializable
data class BatchPointsRequest(
    val points: List<GpsPointDto>,
    @SerialName("batch_id") val batchId: String,
    @SerialName("batch_index") val batchIndex: Int,
    @SerialName("sequence_start") val sequenceStart: Int,
    @SerialName("sequence_end") val sequenceEnd: Int
)

@Serializable
data class BatchPointsResponse(
    val count: Int = 0,
    @SerialName("total_saved") val totalSaved: Int = 0,
    val message: String = "",
    @SerialName("batch_id") val batchId: String = ""
)

@Serializable
data class CompleteRideRequest(
    @SerialName("end_time") val endTime: String,
    @SerialName("total_distance_meters") val totalDistanceMeters: Double = 0.0,
    @SerialName("moving_time_seconds") val movingTimeSeconds: Long = 0L,
    @SerialName("elapsed_time_seconds") val elapsedTimeSeconds: Long = 0L,
    @SerialName("max_speed_kmh") val maxSpeedKmh: Double = 0.0,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double = 0.0,
    @SerialName("elevation_gain_meters") val elevationGainMeters: Double = 0.0,
    @SerialName("elevation_loss_meters") val elevationLossMeters: Double = 0.0,
    @SerialName("expected_points_count") val expectedPointsCount: Int = 0,
    @SerialName("expected_batches_count") val expectedBatchesCount: Int = 0
)

@Serializable
data class MotorcycleRequest(
    val brand: String = "",
    val make: String = "",
    val model: String,
    val year: Int,
    val nickname: String? = null,
    @SerialName("engine_cc") val engineCc: Int = 0,
    @SerialName("is_primary") val isPrimary: Boolean = false
)

@Serializable
data class MotorcycleResponse(
    val id: String,
    val brand: String = "",
    val make: String = "",
    val model: String = "",
    val year: Int = 2024,
    val nickname: String? = null,
    @SerialName("engine_cc") val engineCc: Int = 0,
    @SerialName("is_primary") val isPrimary: Boolean = false
) {
    val displayBrand: String get() = brand.ifBlank { make }
}

@Serializable
data class ProfileResponse(
    @Serializable(with = LenientIdSerializer::class) val id: String = "",
    val username: String = "",
    val email: String = "",
    @SerialName("display_name") val displayName: String = "",
    val bio: String = "",
    val city: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("is_profile_public") val isProfilePublic: Boolean = false,
    @SerialName("total_rides") val totalRides: Int = 0,
    @SerialName("total_distance_km") val totalDistanceKm: Double = 0.0,
    @SerialName("total_time_hours") val totalTimeHours: Double = 0.0
)

@Serializable
data class ImageUploadResponse(
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val message: String? = null
)

@Serializable
data class ClubEligibilityResponse(
    val eligible: Boolean = false,
    @SerialName("completed_rides") val completedRides: Int = 0,
    @SerialName("public_posts") val publicPosts: Int = 0,
    @SerialName("needs_rides") val needsRides: Int = 0,
    @SerialName("needs_posts") val needsPosts: Int = 0
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface RydaApiService {

    // Auth
    @POST("auth/register/")
    suspend fun register(@Body body: RegisterRequest): Response<TokenResponse>

    @POST("auth/login/")
    suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @POST("auth/logout/")
    suspend fun logout(): Response<Unit>

    @DELETE("auth/delete-account/")
    suspend fun deleteAccount(): Response<Unit>

    // Profile
    @GET("profile/me/")
    suspend fun getProfile(): Response<ProfileResponse>

    @PATCH("profile/me/")
    suspend fun patchProfile(@Body body: Map<String, String>): Response<ProfileResponse>

    @GET("profile/export/")
    suspend fun exportData(): Response<okhttp3.ResponseBody>

    @Multipart
    @POST("profile/avatar/")
    suspend fun uploadProfileAvatar(
        @Part avatar: MultipartBody.Part
    ): Response<ImageUploadResponse>

    // Garage
    @GET("garage/motorcycles/")
    suspend fun getMotorcycles(): Response<List<MotorcycleResponse>>

    @POST("garage/motorcycles/")
    suspend fun createMotorcycle(@Body body: MotorcycleRequest): Response<MotorcycleResponse>

    @PATCH("garage/motorcycles/{id}/")
    suspend fun patchMotorcycle(@Path("id") id: String, @Body body: MotorcycleRequest): Response<MotorcycleResponse>

    @DELETE("garage/motorcycles/{id}/")
    suspend fun deleteMotorcycle(@Path("id") id: String): Response<Unit>

    // Rides
    @POST("rides/")
    suspend fun createRide(@Body body: RideRequest): Response<RideResponse>

    @GET("rides/")
    suspend fun listRides(): Response<List<RideResponse>>

    @GET("rides/{id}/")
    suspend fun getRide(@Path("id") id: String): Response<RideResponse>

    @PATCH("rides/{id}/")
    suspend fun patchRide(@Path("id") id: String, @Body body: Map<String, String>): Response<RideResponse>

    @POST("rides/{id}/points/batch/")
    suspend fun uploadPoints(@Path("id") id: String, @Body body: BatchPointsRequest): Response<BatchPointsResponse>

    @POST("rides/{id}/complete/")
    suspend fun completeRide(@Path("id") id: String, @Body body: CompleteRideRequest): Response<RideResponse>

    @GET("rides/{id}/summary/")
    suspend fun getRideSummary(@Path("id") id: String): Response<RideResponse>

    @Multipart
    @POST("rides/{id}/photo/")
    suspend fun uploadRidePhoto(
        @Path("id") id: String,
        @Part photo: MultipartBody.Part
    ): Response<ImageUploadResponse>

    @POST("rides/{id}/photo/")
    suspend fun updateRidePhotoUrl(
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): Response<ImageUploadResponse>

    // ── Phase 5: Community Foundation ──────────────────────────────────────────
    @GET("community/feed/")
    suspend fun getCommunityFeed(@Query("tab") tab: String = "following"): Response<List<CommunityPost>>

    @POST("community/posts/")
    suspend fun createCommunityPost(@Body body: PostCreateRequest): Response<CommunityPost>

    @POST("community/posts/{id}/like/")
    suspend fun togglePostLike(@Path("id") id: String): Response<LikeResponse>

    @GET("community/posts/{id}/comments/")
    suspend fun getPostComments(@Path("id") id: String): Response<List<PostComment>>

    @POST("community/posts/{id}/comments/")
    suspend fun addPostComment(@Path("id") id: String, @Body body: Map<String, String>): Response<PostComment>

    @POST("community/riders/{id}/follow/")
    suspend fun toggleRiderFollow(@Path("id") id: Int): Response<FollowResponse>

    // ── Phase 6: Clubs & Organised Events ──────────────────────────────────────
    @GET("clubs/eligibility/")
    suspend fun getClubEligibility(): Response<ClubEligibilityResponse>

    @GET("clubs/")
    suspend fun getClubs(@Query("city") city: String? = null): Response<List<Club>>

    @POST("clubs/")
    suspend fun createClub(@Body body: Map<String, String>): Response<Club>

    @GET("clubs/{id}/")
    suspend fun getClubDetail(@Path("id") id: String): Response<Club>

    @POST("clubs/{id}/join/")
    suspend fun toggleClubMembership(@Path("id") id: String): Response<ClubJoinResponse>

    @GET("clubs/{id}/events/")
    suspend fun getClubEvents(@Path("id") id: String): Response<List<RidingEvent>>

    @GET("clubs/events/")
    suspend fun getExploreEvents(): Response<List<RidingEvent>>

    @POST("clubs/events/{id}/rsvp/")
    suspend fun setEventRsvp(@Path("id") id: String, @Body body: Map<String, String>): Response<EventRsvpResponse>

    // ── Phase 7: Achievements & Notifications ──────────────────────────────────
    @GET("achievements/")
    suspend fun getAchievements(): Response<AchievementsResponse>

    @GET("notifications/")
    suspend fun getNotifications(): Response<NotificationsResponse>

    @POST("notifications/mark-all-read/")
    suspend fun markAllNotificationsRead(): Response<Map<String, String>>

    @POST("notifications/{id}/read/")
    suspend fun markNotificationRead(@Path("id") id: String): Response<Map<String, String>>
}
