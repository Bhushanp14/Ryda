package com.ryda.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ryda.app.core.auth.TokenDataStore
import com.ryda.app.core.database.RydaDatabaseHelper
import com.ryda.app.core.models.*
import com.ryda.app.core.network.ClubEligibilityResponse
import com.ryda.app.core.network.ClubLeaderboardResponse
import com.ryda.app.core.network.MotorcycleStatsResponse
import com.ryda.app.core.network.RetrofitBuilder
import com.ryda.app.core.network.RydaApiService
import com.ryda.app.core.utils.MediaStorageHelper
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

enum class RestorationState {
    NOT_STARTED,
    RESTORING,
    PARTIALLY_RESTORED,
    COMPLETED,
    FAILED
}

data class ModuleRestoreResult(
    val moduleName: String,
    val isSuccess: Boolean,
    val recordsRetrieved: Int = 0,
    val recordsPersisted: Int = 0,
    val failureReason: String? = null,
    val attemptedAt: Long = System.currentTimeMillis()
)

data class AccountRestorationReport(
    val state: RestorationState = RestorationState.NOT_STARTED,
    val moduleResults: Map<String, ModuleRestoreResult> = emptyMap(),
    val startedAt: Long = 0L,
    val completedAt: Long = 0L
)

class RydaRepository private constructor(private val context: Context) {

    private val db = RydaDatabaseHelper.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _completedRides = MutableStateFlow<List<Ride>>(emptyList())
    val completedRides: StateFlow<List<Ride>> = _completedRides.asStateFlow()

    private val _motorcycles = MutableStateFlow<List<Motorcycle>>(emptyList())
    val motorcycles: StateFlow<List<Motorcycle>> = _motorcycles.asStateFlow()

    private val _riderProfile = MutableStateFlow(RiderProfile())
    val riderProfile: StateFlow<RiderProfile> = _riderProfile.asStateFlow()

    private val _restorationStatus = MutableStateFlow(AccountRestorationReport())
    val restorationStatus: StateFlow<AccountRestorationReport> = _restorationStatus.asStateFlow()

    init {
        refreshAll()
    }

    companion object {
        @Volatile
        private var INSTANCE: RydaRepository? = null

        fun getInstance(context: Context): RydaRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RydaRepository(context).also { INSTANCE = it }
            }
        }
    }

    fun refreshAll() {
        scope.launch {
            _completedRides.value = db.getAllCompletedRides()
            _motorcycles.value = db.getAllMotorcycles()
            _riderProfile.value = db.getRiderProfile()
        }
    }

    fun getRideById(rideId: String): Ride? {
        return db.getRideById(rideId)
    }

    fun getPointsForRide(rideId: String): List<GpsPoint> {
        return db.getPointsForRide(rideId)
    }

    fun deleteRide(rideId: String) {
        scope.launch {
            db.deleteRide(rideId)
            recalculateAggregates()
            refreshAll()
        }
    }

    fun addMotorcycle(motorcycle: Motorcycle) {
        scope.launch {
            try {
                val persistentImageUri = if (!motorcycle.imageUri.isNullOrBlank()) {
                    MediaStorageHelper.saveImageToInternalStorage(context, motorcycle.imageUri, "motorcycle_photos", "bike")
                } else {
                    motorcycle.imageUri
                }
                val finalMoto = motorcycle.copy(imageUri = persistentImageUri)

                val tokenDs = TokenDataStore.getInstance(context)
                if (tokenDs.isLoggedIn()) {
                    val api = RetrofitBuilder.getService(tokenDs)
                    try {
                        api.createMotorcycle(
                            com.ryda.app.core.network.MotorcycleRequest(
                                brand = finalMoto.brand,
                                make = finalMoto.brand,
                                model = finalMoto.model,
                                year = finalMoto.year,
                                nickname = finalMoto.nickname ?: "",
                                engineCc = finalMoto.engineCc,
                                isPrimary = finalMoto.isPrimary
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("RYDA_REPO", "Server createMotorcycle non-fatal error: ${e.message}")
                    }
                }
                db.insertMotorcycle(finalMoto)
                _motorcycles.value = db.getAllMotorcycles()
            } catch (e: Exception) {
                android.util.Log.e("RYDA_REPO", "addMotorcycle error: ${e.message}", e)
                try {
                    db.insertMotorcycle(motorcycle)
                    _motorcycles.value = db.getAllMotorcycles()
                } catch (_: Exception) {}
            }
        }
    }

    fun updateMotorcycle(motorcycle: Motorcycle) {
        scope.launch {
            try {
                val persistentImageUri = if (!motorcycle.imageUri.isNullOrBlank()) {
                    MediaStorageHelper.saveImageToInternalStorage(context, motorcycle.imageUri, "motorcycle_photos", "bike")
                } else {
                    motorcycle.imageUri
                }
                val finalMoto = motorcycle.copy(imageUri = persistentImageUri)

                val tokenDs = TokenDataStore.getInstance(context)
                if (tokenDs.isLoggedIn()) {
                    val api = RetrofitBuilder.getService(tokenDs)
                    try {
                        api.patchMotorcycle(
                            finalMoto.id,
                            com.ryda.app.core.network.MotorcycleRequest(
                                brand = finalMoto.brand,
                                make = finalMoto.brand,
                                model = finalMoto.model,
                                year = finalMoto.year,
                                nickname = finalMoto.nickname ?: "",
                                engineCc = finalMoto.engineCc,
                                isPrimary = finalMoto.isPrimary
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("RYDA_REPO", "Server patchMotorcycle non-fatal error: ${e.message}")
                    }
                }
                db.updateMotorcycle(finalMoto)
                _motorcycles.value = db.getAllMotorcycles()
            } catch (e: Exception) {
                android.util.Log.e("RYDA_REPO", "updateMotorcycle error: ${e.message}", e)
                try {
                    db.updateMotorcycle(motorcycle)
                    _motorcycles.value = db.getAllMotorcycles()
                } catch (_: Exception) {}
            }
        }
    }

    fun setPrimaryMotorcycle(bikeId: String) {
        scope.launch {
            db.setPrimaryMotorcycle(bikeId)
            _motorcycles.value = db.getAllMotorcycles()
        }
    }

    fun deleteMotorcycle(bikeId: String) {
        scope.launch {
            db.deleteMotorcycle(bikeId)
            _motorcycles.value = db.getAllMotorcycles()
            try {
                val tokenDs = TokenDataStore.getInstance(context)
                if (tokenDs.isLoggedIn()) {
                    val api = RetrofitBuilder.getService(tokenDs)
                    api.deleteMotorcycle(bikeId)
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun fetchMotorcycleStats(bikeId: String): MotorcycleStatsResponse? = withContext(Dispatchers.IO) {
        try {
            val tokenDs = TokenDataStore.getInstance(context)
            if (!tokenDs.isLoggedIn()) return@withContext db.calculateMotorcycleStats(bikeId)
            val api = RetrofitBuilder.getService(tokenDs)
            val resp = api.getMotorcycleStats(bikeId)
            if (resp.isSuccessful) resp.body() else db.calculateMotorcycleStats(bikeId)
        } catch (e: Exception) {
            db.calculateMotorcycleStats(bikeId)
        }
    }

    fun calculateLocalMotorcycleStats(bikeId: String): MotorcycleStatsResponse {
        return db.calculateMotorcycleStats(bikeId)
    }

    fun getCompletedRidesForMotorcycle(bikeId: String): List<Ride> {
        return db.getRidesForMotorcycle(bikeId)
    }

    fun updateProfile(profile: RiderProfile) {
        scope.launch {
            var updatedProfile = profile
            var serverAvatarUrl: String? = null
            val targetAvatar = profile.avatarUrl

            if (targetAvatar.isNotBlank() && (targetAvatar.startsWith("content://") || targetAvatar.startsWith("file://"))) {
                val savedAvatarLocalUri = com.ryda.app.core.utils.MediaStorageHelper.saveImageToInternalStorage(context, targetAvatar, "profile_avatar", "avatar")
                val localFilePath = savedAvatarLocalUri?.let { Uri.parse(it).path }

                val imageBytes = try {
                    if (localFilePath != null && java.io.File(localFilePath).exists()) {
                        java.io.File(localFilePath).readBytes()
                    } else {
                        context.contentResolver.openInputStream(Uri.parse(targetAvatar))?.use { it.readBytes() }
                    }
                } catch (e: Exception) {
                    Log.e("RYDA_REPO", "Failed reading avatar bytes: ${e.message}")
                    null
                }

                if (imageBytes != null && imageBytes.isNotEmpty()) {
                    try {
                        val tokenDs = TokenDataStore.getInstance(context)
                        if (tokenDs.isLoggedIn()) {
                            val api = RetrofitBuilder.getService(tokenDs)
                            val reqFile = imageBytes.toRequestBody("image/*".toMediaTypeOrNull())
                            val body = MultipartBody.Part.createFormData("avatar", "avatar.jpg", reqFile)
                            val uploadResp = api.uploadProfileAvatar(body)
                            if (uploadResp.isSuccessful && !uploadResp.body()?.avatarUrl.isNullOrBlank()) {
                                serverAvatarUrl = uploadResp.body()!!.avatarUrl!!
                                updatedProfile = updatedProfile.copy(avatarUrl = serverAvatarUrl)
                                Log.i("RYDA_REPO", "Avatar uploaded to server successfully: $serverAvatarUrl")
                            } else if (savedAvatarLocalUri != null) {
                                updatedProfile = updatedProfile.copy(avatarUrl = savedAvatarLocalUri)
                            }
                        } else if (savedAvatarLocalUri != null) {
                            updatedProfile = updatedProfile.copy(avatarUrl = savedAvatarLocalUri)
                        }
                    } catch (e: Exception) {
                        Log.e("RYDA_REPO", "Avatar multipart upload API error: ${e.message}")
                        if (savedAvatarLocalUri != null) {
                            updatedProfile = updatedProfile.copy(avatarUrl = savedAvatarLocalUri)
                        }
                    }
                } else if (savedAvatarLocalUri != null) {
                    updatedProfile = updatedProfile.copy(avatarUrl = savedAvatarLocalUri)
                }
            }

            db.updateProfile(updatedProfile)
            _riderProfile.value = db.getRiderProfile()

            try {
                val tokenDs = TokenDataStore.getInstance(context)
                if (tokenDs.isLoggedIn()) {
                    val api = RetrofitBuilder.getService(tokenDs)
                    val patchMap = mutableMapOf(
                        "display_name" to updatedProfile.displayName,
                        "bio" to updatedProfile.bio,
                        "city" to updatedProfile.city,
                        "is_profile_public" to updatedProfile.isProfilePublic.toString()
                    )
                    val currAvatar = updatedProfile.avatarUrl
                    if (currAvatar.isNotBlank() && !currAvatar.startsWith("content://") && !currAvatar.startsWith("file://")) {
                        patchMap["avatar_url"] = currAvatar
                    }
                    api.patchProfile(patchMap)
                }
            } catch (e: Exception) {
                Log.e("RYDA_REPO", "Profile patch error: ${e.message}")
            }
        }
    }

    private fun parseIsoTimestamp(raw: String?): Long {
        if (raw.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            java.time.Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                sdf.parse(raw)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    private fun logRestore(msg: String) {
        Log.i("RYDA_RESTORE", msg)
    }

    private fun logRestoreError(msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("RYDA_RESTORE", msg, throwable)
        } else {
            Log.e("RYDA_RESTORE", msg)
        }
    }

    private suspend fun restoreProfile(api: RydaApiService): ModuleRestoreResult {
        logRestore("Starting module: Profile [GET profile/me/]")
        return try {
            val resp = api.getProfile()
            if (resp.isSuccessful && resp.body() != null) {
                val p = resp.body()!!
                val currentLocal = db.getRiderProfile()
                val updatedLocal = currentLocal.copy(
                    displayName = p.displayName.ifBlank { p.username }.ifBlank { currentLocal.displayName },
                    bio = p.bio,
                    city = p.city,
                    avatarUrl = p.avatarUrl.ifBlank { currentLocal.avatarUrl },
                    isProfilePublic = p.isProfilePublic,
                    totalRides = maxOf(currentLocal.totalRides, p.totalRides),
                    totalDistanceKm = maxOf(currentLocal.totalDistanceKm, p.totalDistanceKm),
                    totalTimeHours = maxOf(currentLocal.totalTimeHours, p.totalTimeHours)
                )
                db.updateProfile(updatedLocal)
                logRestore("Profile successfully restored and persisted (display_name=${updatedLocal.displayName}, rides=${updatedLocal.totalRides}).")
                ModuleRestoreResult(
                    moduleName = "Profile",
                    isSuccess = true,
                    recordsRetrieved = 1,
                    recordsPersisted = 1
                )
            } else {
                val err = "HTTP ${resp.code()} - ${resp.errorBody()?.string()?.take(100)}"
                logRestoreError("Profile restore failed: $err")
                ModuleRestoreResult(
                    moduleName = "Profile",
                    isSuccess = false,
                    failureReason = err
                )
            }
        } catch (e: Exception) {
            val err = "${e.javaClass.simpleName}: ${e.message}"
            logRestoreError("Profile restore exception: $err", e)
            ModuleRestoreResult(
                moduleName = "Profile",
                isSuccess = false,
                failureReason = err
            )
        }
    }

    private suspend fun restoreMotorcycles(api: RydaApiService): ModuleRestoreResult {
        logRestore("Starting module: Motorcycles [GET garage/motorcycles/]")
        return try {
            val resp = api.getMotorcycles()
            if (resp.isSuccessful) {
                val remoteBikes = resp.body().orEmpty()
                logRestore("Retrieved ${remoteBikes.size} motorcycle(s) from cloud.")
                var persistedCount = 0
                remoteBikes.forEach { bikeDto ->
                    try {
                        val existing = db.getAllMotorcycles().firstOrNull { it.id == bikeDto.id }
                        val motorcycle = Motorcycle(
                            id = bikeDto.id,
                            brand = bikeDto.displayBrand,
                            model = bikeDto.model,
                            year = bikeDto.year,
                            nickname = bikeDto.nickname?.ifBlank { null },
                            engineCc = bikeDto.engineCc,
                            isPrimary = bikeDto.isPrimary,
                            imageUri = existing?.imageUri
                        )
                        db.insertMotorcycle(motorcycle)
                        persistedCount++
                    } catch (e: Exception) {
                        logRestoreError("Failed persisting motorcycle ${bikeDto.id}: ${e.message}")
                    }
                }
                logRestore("Motorcycles successfully restored: $persistedCount persisted.")
                ModuleRestoreResult(
                    moduleName = "Motorcycles",
                    isSuccess = true,
                    recordsRetrieved = remoteBikes.size,
                    recordsPersisted = persistedCount
                )
            } else {
                val err = "HTTP ${resp.code()} - ${resp.errorBody()?.string()?.take(100)}"
                logRestoreError("Motorcycles restore failed: $err")
                ModuleRestoreResult(
                    moduleName = "Motorcycles",
                    isSuccess = false,
                    failureReason = err
                )
            }
        } catch (e: Exception) {
            val err = "${e.javaClass.simpleName}: ${e.message}"
            logRestoreError("Motorcycles restore exception: $err", e)
            ModuleRestoreResult(
                moduleName = "Motorcycles",
                isSuccess = false,
                failureReason = err
            )
        }
    }

    private suspend fun restoreRides(api: RydaApiService): ModuleRestoreResult {
        logRestore("Starting module: Rides [GET rides/]")
        return try {
            val resp = api.listRides()
            if (resp.isSuccessful) {
                val remoteRides = resp.body().orEmpty()
                logRestore("Retrieved ${remoteRides.size} ride(s) from cloud.")
                var persistedCount = 0
                remoteRides.forEach { r ->
                    try {
                        if (r.status != "COMPLETED") return@forEach
                        val existing = db.getRideById(r.id)
                        val startTimeMs = parseIsoTimestamp(r.startTime)
                        val endTimeMs = r.endTime?.let { parseIsoTimestamp(it) }

                        // If cloud returned 0 metrics, preserve existing local metrics or compute from km if available
                        val fallbackDist = if (r.totalDistanceKm > 0.0) r.totalDistanceKm * 1000.0 else (existing?.totalDistanceMeters ?: 0.0)
                        val totalDist = if (r.totalDistanceMeters > 0.0) r.totalDistanceMeters else fallbackDist
                        val baseMovingSec = if (r.movingTimeSeconds > 0L) r.movingTimeSeconds else (existing?.movingTimeSeconds ?: 0L)
                        val elapsedSec = if (r.elapsedTimeSeconds > 0L) r.elapsedTimeSeconds else (existing?.elapsedTimeSeconds ?: 0L)
                        val movingSec = if (baseMovingSec > 0L) baseMovingSec else elapsedSec
                        val maxSpd = if (r.maxSpeedKmh > 0.0) r.maxSpeedKmh else (existing?.maxSpeedKmh ?: 0.0)
                        val baseAvgSpd = if (r.avgSpeedKmh > 0.0) r.avgSpeedKmh else (existing?.avgSpeedKmh ?: 0.0)
                        val avgSpd = if (baseAvgSpd > 0.0) baseAvgSpd else if (movingSec > 0L) (totalDist / movingSec) * 3.6 else 0.0
                        val elevGain = if (r.elevationGainMeters > 0.0) r.elevationGainMeters else (existing?.elevationGainMeters ?: 0.0)
                        val elevLoss = if (r.elevationLossMeters > 0.0) r.elevationLossMeters else (existing?.elevationLossMeters ?: 0.0)

                        val resolvedCloudUrl = com.ryda.app.core.network.RetrofitBuilder.resolveMediaUrl(r.imageUrl)
                        val hasLocalUri = !existing?.photoUri.isNullOrBlank() &&
                            (existing?.photoUri!!.startsWith("file://") || existing?.photoUri!!.startsWith("content://"))
                        val finalPhotoUri = when {
                            hasLocalUri -> existing.photoUri
                            !resolvedCloudUrl.isNullOrBlank() -> resolvedCloudUrl
                            else -> existing?.photoUri
                        }

                        val ride = Ride(
                            id = r.id,
                            motorcycleId = r.motorcycleId,
                            title = r.title.ifBlank { "Ride" },
                            status = RideStatus.COMPLETED,
                            startTime = startTimeMs,
                            endTime = endTimeMs,
                            totalDistanceMeters = totalDist,
                            movingTimeSeconds = movingSec,
                            elapsedTimeSeconds = elapsedSec,
                            maxSpeedKmh = maxSpd,
                            avgSpeedKmh = avgSpd,
                            elevationGainMeters = elevGain,
                            elevationLossMeters = elevLoss,
                            syncStatus = SyncStatus.SYNCED,
                            isPrivate = r.isPrivate,
                            photoUri = finalPhotoUri,
                            clubRideSessionId = r.clubRideSessionId ?: existing?.clubRideSessionId
                        )

                        if (existing == null) {
                            db.insertRide(ride)
                        } else {
                            db.updateRide(ride)
                        }
                        persistedCount++
                    } catch (e: Exception) {
                        logRestoreError("Failed persisting ride ${r.id}: ${e.message}")
                    }
                }
                logRestore("Rides successfully restored: $persistedCount persisted.")
                ModuleRestoreResult(
                    moduleName = "Rides",
                    isSuccess = true,
                    recordsRetrieved = remoteRides.size,
                    recordsPersisted = persistedCount
                )
            } else {
                val err = "HTTP ${resp.code()} - ${resp.errorBody()?.string()?.take(100)}"
                logRestoreError("Rides restore failed: $err")
                ModuleRestoreResult(
                    moduleName = "Rides",
                    isSuccess = false,
                    failureReason = err
                )
            }
        } catch (e: Exception) {
            val err = "${e.javaClass.simpleName}: ${e.message}"
            logRestoreError("Rides restore exception: $err", e)
            ModuleRestoreResult(
                moduleName = "Rides",
                isSuccess = false,
                failureReason = err
            )
        }
    }

    private fun calculatePointsTelemetry(points: List<GpsPoint>): ComputedTelemetry {
        if (points.size < 2) return ComputedTelemetry()
        var totalDist = 0.0
        var movingSec = 0L
        var maxSpd = 0.0
        val sorted = points.sortedBy { it.sequenceNumber }
        val startTime = sorted.first().timestamp
        val endTime = sorted.last().timestamp
        val elapsedSec = kotlin.math.max(0L, (endTime - startTime) / 1000L)

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            if (curr.accuracy > 25.0f) continue
            val stepDist = com.ryda.app.core.location.Haversine.distanceInMeters(
                prev.latitude, prev.longitude, curr.latitude, curr.longitude
            )
            val dt = kotlin.math.max(0.001, (curr.timestamp - prev.timestamp) / 1000.0)
            val stepSpeedKmh = (stepDist / dt) * 3.6
            if (stepSpeedKmh > 180.0) continue

            totalDist += stepDist
            val reportedSpeed = if (curr.speedKmh > 0) curr.speedKmh else stepSpeedKmh
            maxSpd = kotlin.math.max(maxSpd, reportedSpeed)
            if (reportedSpeed >= 5.4 || stepDist > 3.0) {
                movingSec += dt.toLong()
            }
        }
        movingSec = movingSec.coerceAtMost(elapsedSec)
        val avgSpd = if (movingSec > 0) (totalDist / movingSec) * 3.6 else 0.0

        return ComputedTelemetry(
            totalDistanceMeters = totalDist,
            movingTimeSeconds = movingSec,
            elapsedTimeSeconds = elapsedSec,
            avgSpeedKmh = avgSpd,
            maxSpeedKmh = maxSpd
        )
    }

    private data class ComputedTelemetry(
        val totalDistanceMeters: Double = 0.0,
        val movingTimeSeconds: Long = 0L,
        val elapsedTimeSeconds: Long = 0L,
        val avgSpeedKmh: Double = 0.0,
        val maxSpeedKmh: Double = 0.0,
        val elevationGainMeters: Double = 0.0,
        val elevationLossMeters: Double = 0.0
    )

    private suspend fun restoreAchievements(api: RydaApiService): ModuleRestoreResult {
        logRestore("Starting module: Achievements [GET achievements/]")
        return try {
            val resp = api.getAchievements()
            if (resp.isSuccessful && resp.body() != null) {
                val achList = resp.body()!!.achievements
                var persistedCount = 0
                achList.forEach { ach ->
                    try {
                        if (ach.isUnlocked) {
                            db.saveUserAchievement("rider_local_1", ach.id, ach.progressValue, true)
                            persistedCount++
                        }
                    } catch (e: Exception) {
                        logRestoreError("Failed persisting achievement ${ach.id}: ${e.message}")
                    }
                }
                logRestore("Achievements successfully restored: ${achList.size} retrieved, $persistedCount unlocked persisted.")
                ModuleRestoreResult(
                    moduleName = "Achievements",
                    isSuccess = true,
                    recordsRetrieved = achList.size,
                    recordsPersisted = persistedCount
                )
            } else {
                val err = "HTTP ${resp.code()} - ${resp.errorBody()?.string()?.take(100)}"
                logRestoreError("Achievements restore failed: $err")
                ModuleRestoreResult(
                    moduleName = "Achievements",
                    isSuccess = false,
                    failureReason = err
                )
            }
        } catch (e: Exception) {
            val err = "${e.javaClass.simpleName}: ${e.message}"
            logRestoreError("Achievements restore exception: $err", e)
            ModuleRestoreResult(
                moduleName = "Achievements",
                isSuccess = false,
                failureReason = err
            )
        }
    }

    private suspend fun restoreClubs(api: RydaApiService): ModuleRestoreResult {
        logRestore("Starting module: Clubs [GET clubs/]")
        return try {
            val resp = api.getClubs()
            if (resp.isSuccessful) {
                val clubsList = resp.body().orEmpty()
                var persistedCount = 0
                clubsList.forEach { club ->
                    try {
                        db.insertClub(club)
                        persistedCount++
                    } catch (e: Exception) {
                        logRestoreError("Failed persisting club ${club.id}: ${e.message}")
                    }
                }
                logRestore("Clubs successfully restored: ${clubsList.size} retrieved, $persistedCount persisted.")
                ModuleRestoreResult(
                    moduleName = "Clubs",
                    isSuccess = true,
                    recordsRetrieved = clubsList.size,
                    recordsPersisted = persistedCount
                )
            } else {
                val err = "HTTP ${resp.code()} - ${resp.errorBody()?.string()?.take(100)}"
                logRestoreError("Clubs restore failed: $err")
                ModuleRestoreResult(
                    moduleName = "Clubs",
                    isSuccess = false,
                    failureReason = err
                )
            }
        } catch (e: Exception) {
            val err = "${e.javaClass.simpleName}: ${e.message}"
            logRestoreError("Clubs restore exception: $err", e)
            ModuleRestoreResult(
                moduleName = "Clubs",
                isSuccess = false,
                failureReason = err
            )
        }
    }

    suspend fun restoreAccountDataFromCloud(failedModulesOnly: Boolean = false) = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        logRestore("========== RESTORATION STARTED (retryFailedOnly=$failedModulesOnly) ==========")

        val tokenDs = TokenDataStore.getInstance(context)
        if (!tokenDs.isLoggedIn()) {
            logRestore("Aborted: User is not logged in.")
            _restorationStatus.value = AccountRestorationReport(
                state = RestorationState.NOT_STARTED,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis()
            )
            return@withContext
        }

        _restorationStatus.value = _restorationStatus.value.copy(
            state = RestorationState.RESTORING,
            startedAt = startedAt
        )

        val api = RetrofitBuilder.getService(tokenDs)
        val currentResults = _restorationStatus.value.moduleResults.toMutableMap()

        try {
            try {
                if (!failedModulesOnly || currentResults["Profile"]?.isSuccess != true) {
                    currentResults["Profile"] = restoreProfile(api)
                }
            } catch (e: Throwable) {
                val err = "${e.javaClass.simpleName}: ${e.message}"
                logRestoreError("Profile module unhandled failure: $err", e)
                currentResults["Profile"] = ModuleRestoreResult(moduleName = "Profile", isSuccess = false, failureReason = err)
            }

            try {
                if (!failedModulesOnly || currentResults["Motorcycles"]?.isSuccess != true) {
                    currentResults["Motorcycles"] = restoreMotorcycles(api)
                }
            } catch (e: Throwable) {
                val err = "${e.javaClass.simpleName}: ${e.message}"
                logRestoreError("Motorcycles module unhandled failure: $err", e)
                currentResults["Motorcycles"] = ModuleRestoreResult(moduleName = "Motorcycles", isSuccess = false, failureReason = err)
            }

            try {
                if (!failedModulesOnly || currentResults["Rides"]?.isSuccess != true) {
                    currentResults["Rides"] = restoreRides(api)
                }
            } catch (e: Throwable) {
                val err = "${e.javaClass.simpleName}: ${e.message}"
                logRestoreError("Rides module unhandled failure: $err", e)
                currentResults["Rides"] = ModuleRestoreResult(moduleName = "Rides", isSuccess = false, failureReason = err)
            }

            try {
                if (!failedModulesOnly || currentResults["Achievements"]?.isSuccess != true) {
                    currentResults["Achievements"] = restoreAchievements(api)
                }
            } catch (e: Throwable) {
                val err = "${e.javaClass.simpleName}: ${e.message}"
                logRestoreError("Achievements module unhandled failure: $err", e)
                currentResults["Achievements"] = ModuleRestoreResult(moduleName = "Achievements", isSuccess = false, failureReason = err)
            }

            try {
                if (!failedModulesOnly || currentResults["Clubs"]?.isSuccess != true) {
                    currentResults["Clubs"] = restoreClubs(api)
                }
            } catch (e: Throwable) {
                val err = "${e.javaClass.simpleName}: ${e.message}"
                logRestoreError("Clubs module unhandled failure: $err", e)
                currentResults["Clubs"] = ModuleRestoreResult(moduleName = "Clubs", isSuccess = false, failureReason = err)
            }
        } finally {
            try {
                recalculateAggregates()
            } catch (e: Exception) {
                logRestoreError("Failed recalculating aggregates: ${e.message}")
            }
            refreshAll()
            logRestore("refreshAll() executed successfully. SQLite data reloaded into Compose StateFlows.")

            val totalModules = currentResults.size
            val successCount = currentResults.values.count { it.isSuccess }
            val finalState = when {
                totalModules > 0 && successCount == totalModules -> RestorationState.COMPLETED
                successCount > 0 -> RestorationState.PARTIALLY_RESTORED
                else -> RestorationState.FAILED
            }

            val completedAt = System.currentTimeMillis()
            _restorationStatus.value = AccountRestorationReport(
                state = finalState,
                moduleResults = currentResults.toMap(),
                startedAt = startedAt,
                completedAt = completedAt
            )
            logRestore("========== RESTORATION FINISHED: $finalState ($successCount/$totalModules succeeded in ${completedAt - startedAt}ms) ==========")
        }
    }

    suspend fun retryFailedRestorationModules() {
        restoreAccountDataFromCloud(failedModulesOnly = true)
    }

    fun getIncompleteRide(): Ride? {
        return db.getIncompleteRide()
    }

    fun finishIncompleteRide(rideId: String): Ride? {
        val finished = db.finishIncompleteRide(rideId)
        recalculateAggregates()
        refreshAll()
        return finished
    }

    fun updateRideMotorcycle(rideId: String, motorcycleId: String?) {
        scope.launch {
            db.updateRideMotorcycle(rideId, motorcycleId)
            refreshAll()
            try {
                val tokenDs = TokenDataStore.getInstance(context)
                if (tokenDs.isLoggedIn()) {
                    val api = RetrofitBuilder.getService(tokenDs)
                    api.patchRide(rideId, mapOf("motorcycle" to (motorcycleId ?: "")))
                }
            } catch (_: Exception) {}
        }
    }

    fun updateRidePrivacy(rideId: String, isPrivate: Boolean) {
        scope.launch {
            val existing = db.getRideById(rideId)
            if (existing != null) {
                db.updateRide(existing.copy(isPrivate = isPrivate, syncStatus = com.ryda.app.core.models.SyncStatus.LOCAL_ONLY))
                refreshAll()
                try {
                    val tokenDs = TokenDataStore.getInstance(context)
                    if (tokenDs.isLoggedIn()) {
                        val api = RetrofitBuilder.getService(tokenDs)
                        api.patchRide(rideId, mapOf("is_private" to isPrivate.toString()))
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun clearAllUserData() {
        scope.launch {
            db.clearAllUserData()
            refreshAll()
        }
    }

    fun updateRideTitle(rideId: String, newTitle: String) {
        scope.launch {
            val existing = db.getRideById(rideId)
            if (existing != null) {
                db.updateRide(existing.copy(title = newTitle))
                refreshAll()
            }
        }
    }

    fun updateRidePhoto(rideId: String, photoUri: String?) {
        scope.launch {
            if (photoUri == null) {
                db.updateRidePhoto(rideId, null)
                refreshAll()
                return@launch
            }

            // Copy to internal filesDir so it never expires when app process is closed
            var persistentUriString = photoUri
            val parsedUri = Uri.parse(photoUri)
            if (parsedUri.scheme == "content" || parsedUri.scheme == "file") {
                try {
                    val photosDir = java.io.File(context.filesDir, "ride_photos").apply { mkdirs() }
                    val targetFile = java.io.File(photosDir, "ride_${rideId}.jpg")
                    val inputStream = if (parsedUri.scheme == "file") {
                        java.io.File(parsedUri.path ?: "").inputStream()
                    } else {
                        context.contentResolver.openInputStream(parsedUri)
                    }
                    inputStream?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (targetFile.exists() && targetFile.length() > 0) {
                        persistentUriString = Uri.fromFile(targetFile).toString()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RydaRepository", "Failed to cache ride photo: ${e.message}")
                }
            }

            db.updateRidePhoto(rideId, persistentUriString)
            refreshAll()

            try {
                val tokenDs = TokenDataStore.getInstance(context)
                if (tokenDs.isLoggedIn()) {
                    val api = RetrofitBuilder.getService(tokenDs)
                    val uploadUri = Uri.parse(persistentUriString)
                    val bytes = if (uploadUri.scheme == "file") {
                        java.io.File(uploadUri.path ?: "").readBytes()
                    } else {
                        context.contentResolver.openInputStream(uploadUri)?.use { it.readBytes() }
                    }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                        val part = MultipartBody.Part.createFormData("photo", "ride_${rideId}.jpg", requestBody)
                        api.uploadRidePhoto(rideId, part)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun uploadAvatar(photoUri: String): String? {
        return try {
            val tokenDs = TokenDataStore.getInstance(context)
            if (!tokenDs.isLoggedIn()) return null
            val api = RetrofitBuilder.getService(tokenDs)
            val parsedUri = Uri.parse(photoUri)
            if (parsedUri.scheme == "content" || parsedUri.scheme == "file") {
                val bytes = context.contentResolver.openInputStream(parsedUri)?.use { it.readBytes() } ?: return null
                val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("avatar", "avatar.jpg", requestBody)
                val res = api.uploadProfileAvatar(part)
                if (res.isSuccessful) res.body()?.avatarUrl else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun recalculateAggregates() {
        val rides = db.getAllCompletedRides()
        val totalRides = rides.size
        val totalDistanceKm = rides.sumOf { it.totalDistanceMeters } / 1000.0
        val totalMovingSecs = rides.sumOf { it.movingTimeSeconds }
        val totalHours = totalMovingSecs / 3600.0

        val currentProfile = db.getRiderProfile()
        val updated = currentProfile.copy(
            totalRides = totalRides,
            totalDistanceKm = totalDistanceKm,
            totalTimeHours = totalHours
        )
        db.updateProfile(updated)
    }

    // ── Phase 5: Community Foundation ──────────────────────────────────────────
    suspend fun getCommunityFeed(tab: String = "following"): List<CommunityPost> {
        val remote = try {
            val tokenDs = TokenDataStore.getInstance(context)
            val api = RetrofitBuilder.getService(tokenDs)
            val res = api.getCommunityFeed(tab)
            if (res.isSuccessful && !res.body().isNullOrEmpty()) res.body() else null
        } catch (_: Exception) {
            null
        }
        val posts = remote ?: db.getAllCommunityPosts()
        return if (tab == "following") {
            posts.filter { it.isFollowingAuthor || it.authorId == 1 }
        } else {
            posts
        }
    }

    suspend fun createCommunityPost(req: PostCreateRequest): CommunityPost? {
        val tokenDs = TokenDataStore.getInstance(context)
        if (!tokenDs.isLoggedIn()) throw IllegalStateException("User not logged in")

        val api = RetrofitBuilder.getService(tokenDs)
        val resp = api.createCommunityPost(req)
        if (resp.isSuccessful && resp.body() != null) {
            val post = resp.body()!!
            db.insertCommunityPost(post)
            return post
        } else {
            val err = resp.errorBody()?.string() ?: "HTTP ${resp.code()}"
            throw RuntimeException("Failed to create post on server: $err")
        }
    }

    suspend fun togglePostLike(postId: String): LikeResponse? {
        return try {
            val tokenDs = TokenDataStore.getInstance(context)
            val api = RetrofitBuilder.getService(tokenDs)
            val res = api.togglePostLike(postId)
            if (res.isSuccessful) res.body() else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getPostComments(postId: String): List<PostComment> {
        return try {
            val tokenDs = TokenDataStore.getInstance(context)
            val api = RetrofitBuilder.getService(tokenDs)
            val res = api.getPostComments(postId)
            if (res.isSuccessful) res.body() ?: emptyList() else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addPostComment(postId: String, text: String): PostComment? {
        return try {
            val tokenDs = TokenDataStore.getInstance(context)
            val api = RetrofitBuilder.getService(tokenDs)
            val res = api.addPostComment(postId, mapOf("text" to text))
            if (res.isSuccessful) res.body() else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun toggleRiderFollow(userId: Int): FollowResponse? {
        return try {
            val tokenDs = TokenDataStore.getInstance(context)
            val api = RetrofitBuilder.getService(tokenDs)
            val res = api.toggleRiderFollow(userId)
            if (res.isSuccessful) res.body() else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getClubEligibility(): com.ryda.app.core.network.ClubEligibilityResponse? {
        return try {
            val tokenDs = TokenDataStore.getInstance(context)
            if (tokenDs.isLoggedIn()) {
                val api = RetrofitBuilder.getService(tokenDs)
                val res = api.getClubEligibility()
                if (res.isSuccessful) res.body() else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun getLegacyLocalClubs(): List<Club> {
        return db.getAllClubs().filter { it.id.startsWith("club_") && it.id != "club_1" && it.id != "club_2" }
    }

    fun getUserClubs(): List<Club> {
        return db.getAllClubs().filter { it.isMember }
    }

    // ── Phase 6: Clubs & Organised Events ──────────────────────────────────────
    suspend fun getClubs(city: String? = null): List<Club> {
        val localClubs = db.getAllClubs(city)
        val remote = try {
            val tokenDs = TokenDataStore.getInstance(context)
            if (tokenDs.isLoggedIn()) {
                val api = RetrofitBuilder.getService(tokenDs)
                val res = api.getClubs(city)
                if (res.isSuccessful && !res.body().isNullOrEmpty()) {
                    res.body()!!.map { remoteClub ->
                        val localMatch = localClubs.firstOrNull { it.id == remoteClub.id || it.name.equals(remoteClub.name, true) }
                        val merged = remoteClub.copy(
                            isMember = localMatch?.isMember == true || remoteClub.isMember
                        )
                        db.insertClub(merged)
                        merged
                    }
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
        return remote ?: localClubs
    }

    suspend fun getClubDetail(clubId: String): Club? {
        return try {
            val tokenDs = TokenDataStore.getInstance(context)
            val api = RetrofitBuilder.getService(tokenDs)
            val res = api.getClubDetail(clubId)
            if (res.isSuccessful && res.body() != null) {
                val remoteClub = res.body()!!
                db.insertClub(remoteClub)
                remoteClub
            } else {
                null
            }
        } catch (_: Exception) {
            db.getAllClubs().firstOrNull { it.id == clubId }
        }
    }

    suspend fun fetchClubLeaderboard(clubId: String, period: String = "monthly"): ClubLeaderboardResponse? = withContext(Dispatchers.IO) {
        try {
            val tokenDs = TokenDataStore.getInstance(context)
            if (!tokenDs.isLoggedIn()) return@withContext null
            val api = RetrofitBuilder.getService(tokenDs)
            val resp = api.getClubLeaderboard(clubId, period)
            if (resp.isSuccessful) resp.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun toggleClubMembership(clubId: String): ClubJoinResponse? {
        val existing = db.getAllClubs().firstOrNull { it.id == clubId }
        if (existing != null) {
            val updatedIsMember = !existing.isMember
            val updatedCount = if (updatedIsMember) existing.membersCount + 1 else maxOf(1, existing.membersCount - 1)
            db.insertClub(existing.copy(isMember = updatedIsMember, membersCount = updatedCount))
        }
        try {
            val tokenDs = TokenDataStore.getInstance(context)
            if (tokenDs.isLoggedIn()) {
                val api = RetrofitBuilder.getService(tokenDs)
                api.toggleClubMembership(clubId)
            }
        } catch (_: Exception) {}
        val updated = db.getAllClubs().firstOrNull { it.id == clubId }
        return ClubJoinResponse(
            clubId = clubId,
            isMember = updated?.isMember ?: true,
            membersCount = updated?.membersCount ?: 1
        )
    }

    suspend fun getExploreEvents(): List<RidingEvent> {
        val remote = try {
            val tokenDs = TokenDataStore.getInstance(context)
            val api = RetrofitBuilder.getService(tokenDs)
            val res = api.getExploreEvents()
            if (res.isSuccessful && !res.body().isNullOrEmpty()) res.body() else null
        } catch (_: Exception) {
            null
        }
        return remote ?: db.getAllRallyEvents()
    }

    suspend fun setEventRsvp(eventId: String, status: String): EventRsvpResponse? {
        val existing = db.getAllRallyEvents().firstOrNull { it.id == eventId }
        if (existing != null) {
            val updatedConfirmed = if (status == "GOING") existing.confirmedCount + 1 else maxOf(0, existing.confirmedCount - 1)
            val updatedInterested = if (status == "INTERESTED") existing.interestedCount + 1 else maxOf(0, existing.interestedCount - 1)
            db.insertRallyEvent(existing.copy(myRsvp = status, confirmedCount = updatedConfirmed, interestedCount = updatedInterested))
        }
        try {
            val tokenDs = TokenDataStore.getInstance(context)
            if (tokenDs.isLoggedIn()) {
                val api = RetrofitBuilder.getService(tokenDs)
                api.setEventRsvp(eventId, mapOf("status" to status))
            }
        } catch (_: Exception) {}
        val updated = db.getAllRallyEvents().firstOrNull { it.id == eventId }
        return EventRsvpResponse(
            eventId = eventId,
            myRsvp = updated?.myRsvp ?: status,
            confirmedCount = updated?.confirmedCount ?: 1,
            interestedCount = updated?.interestedCount ?: 0
        )
    }

    suspend fun createClub(name: String, city: String, description: String, policy: String = "OPEN"): Club {
        val tokenDs = TokenDataStore.getInstance(context)
        if (!tokenDs.isLoggedIn()) throw IllegalStateException("User not logged in")

        val clubId = UUID.randomUUID().toString()
        val slug = name.lowercase(java.util.Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val api = RetrofitBuilder.getService(tokenDs)
        val response = api.createClub(mapOf(
            "id" to clubId,
            "name" to name,
            "slug" to if (slug.isNotBlank()) slug else "club-${clubId.take(8)}",
            "city" to city,
            "description" to description,
            "policy" to policy
        ))

        if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!
            val cloudClub = body.copy(
                id = body.id.ifEmpty { clubId },
                isMember = true
            )
            db.insertClub(cloudClub)
            return cloudClub
        } else {
            val err = response.errorBody()?.string() ?: "HTTP ${response.code()}"
            throw RuntimeException("Failed to create club on server: $err")
        }
    }

    suspend fun createRallyEvent(event: RidingEvent): RidingEvent {
        db.insertRallyEvent(event)
        return event
    }

    // ── Club Ride Sessions (Phase 3) ───────────────────────────────────────────

    private fun <T> parseApiError(response: retrofit2.Response<T>): String {
        val raw = try { response.errorBody()?.string() } catch (_: Exception) { null }
        if (!raw.isNullOrBlank()) {
            try {
                val jsonObj = org.json.JSONObject(raw)
                if (jsonObj.has("detail")) return jsonObj.getString("detail")
                val keys = jsonObj.keys()
                if (keys.hasNext()) {
                    val firstKey = keys.next()
                    return "$firstKey: ${jsonObj.optString(firstKey)}"
                }
            } catch (_: Exception) {
                return raw.take(200)
            }
        }
        return "Request failed (HTTP ${response.code()})"
    }

    private suspend fun <T> executeClubRideCall(
        call: suspend (RydaApiService) -> retrofit2.Response<T>
    ): Result<T> {
        return try {
            val tokenDs = TokenDataStore.getInstance(context)
            if (!tokenDs.isLoggedIn()) {
                return Result.failure(IllegalStateException("Please sign in to access Club Rides."))
            }
            val api = RetrofitBuilder.getService(tokenDs)
            val res = call(api)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(RuntimeException(parseApiError(res)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(RuntimeException(e.message ?: "Network error. Please check your connection."))
        }
    }

    fun getRideByClubSessionId(sessionId: String): Ride? = db.getRideByClubSessionId(sessionId)

    suspend fun computeClubRideRoutes(request: ComputeRouteRequest): Result<ComputeRouteResponse> =
        executeClubRideCall { it.computeClubRideRoutes(request) }

    suspend fun getClubRides(
        clubId: String,
        statusFilter: String? = null,
        section: String? = null
    ): Result<List<ClubRideSession>> =
        executeClubRideCall { it.getClubRides(clubId, statusFilter, section) }

    suspend fun createClubRide(clubId: String, request: CreateClubRideRequest): Result<ClubRideSession> =
        executeClubRideCall { it.createClubRide(clubId, request) }

    suspend fun getClubRideDetail(clubId: String, sessionId: String): Result<ClubRideSession> =
        executeClubRideCall { it.getClubRideDetail(clubId, sessionId) }

    suspend fun joinClubRide(clubId: String, sessionId: String): Result<ClubRideJoinResponse> =
        executeClubRideCall { it.joinClubRide(clubId, sessionId) }

    suspend fun leaveClubRide(clubId: String, sessionId: String): Result<ClubRideJoinResponse> =
        executeClubRideCall { it.leaveClubRide(clubId, sessionId) }

    suspend fun markClubRideReady(clubId: String, sessionId: String): Result<ClubRideReadyResponse> =
        executeClubRideCall { it.markClubRideReady(clubId, sessionId) }

    suspend fun getClubRideParticipants(clubId: String, sessionId: String): Result<List<ClubRideParticipant>> =
        executeClubRideCall { it.getClubRideParticipants(clubId, sessionId) }

    suspend fun verifyClubRideParticipant(clubId: String, sessionId: String, userId: Int): Result<ClubRideVerifyResponse> =
        executeClubRideCall { it.verifyClubRideParticipant(clubId, sessionId, userId) }

    suspend fun markClubRideParticipantAbsent(clubId: String, sessionId: String, userId: Int): Result<ClubRideAbsentResponse> =
        executeClubRideCall { it.markClubRideParticipantAbsent(clubId, sessionId, userId) }

    suspend fun startClubRide(clubId: String, sessionId: String): Result<ClubRideStartResponse> =
        executeClubRideCall { it.startClubRide(clubId, sessionId) }

    suspend fun cancelClubRide(clubId: String, sessionId: String): Result<ClubRideStatusResponse> =
        executeClubRideCall { it.cancelClubRide(clubId, sessionId) }

    suspend fun completeClubRide(clubId: String, sessionId: String): Result<ClubRideStatusResponse> =
        executeClubRideCall { it.completeClubRide(clubId, sessionId) }

    // ── Phase 7: Achievements & Username Tags ─────────────────────────────────
    fun getRiderTags(): List<RiderTag> {
        val profile = db.getRiderProfile()
        val unlockedIds = db.getUnlockedTagIds()
        return RiderTag.ALL_TAGS.map { tag ->
            val isUnlocked = tag.unlockType == "free" || unlockedIds.contains(tag.id)
            val isActive = tag.id == profile.activeTagId
            tag.copy(isUnlocked = isUnlocked, isActive = isActive)
        }
    }

    fun setActiveTag(tagId: String?): Boolean {
        val profile = db.getRiderProfile()
        if (tagId == null) {
            db.updateProfile(profile.copy(activeTagId = null, activeTagName = null))
            _riderProfile.value = db.getRiderProfile()
            return true
        }
        val tags = getRiderTags()
        val target = tags.firstOrNull { it.id == tagId }
        if (target != null && target.isUnlocked) {
            db.updateProfile(profile.copy(activeTagId = target.id, activeTagName = target.name))
            _riderProfile.value = db.getRiderProfile()
            return true
        }
        return false
    }

    suspend fun getAchievements(): AchievementsResponse? {
        val local = generateLocalAchievements()
        val remote = try {
            val tokenDs = TokenDataStore.getInstance(context)
            if (tokenDs.isLoggedIn()) {
                val api = RetrofitBuilder.getService(tokenDs)
                val res = api.getAchievements()
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    body.copy(
                        achievements = body.achievements.map { ach ->
                            val localMatch = local.achievements.firstOrNull { it.key == ach.key || it.id == ach.id }
                            ach.copy(iconName = localMatch?.iconName ?: ach.iconName)
                        }
                    )
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
        return remote ?: local
    }

    private fun generateLocalAchievements(): AchievementsResponse {
        val profile = db.getRiderProfile()
        val rides = db.getAllCompletedRides()
        val posts = db.getAllCommunityPosts()
        val clubs = db.getAllClubs()
        val rallies = db.getAllRallyEvents()
        val dbProgress = db.getUserAchievementProgress()

        val rideCount = rides.size.toDouble()
        val totalDistKm = profile.totalDistanceKm

        // Riding experiences calculations
        val weekendSet = mutableSetOf<String>()
        val monthSet = mutableSetOf<String>()
        var hasInactivityGap30 = false
        val bikeIdsUsed = mutableSetOf<String>()
        var maxSingleRideKm = 0.0

        val cal = java.util.Calendar.getInstance()
        var prevRideTime: Long? = null

        rides.sortedBy { it.startTime }.forEach { ride ->
            cal.timeInMillis = ride.startTime
            val year = cal.get(java.util.Calendar.YEAR)
            val month = cal.get(java.util.Calendar.MONTH)
            val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
            val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)

            monthSet.add("$year-$month")

            if (dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY) {
                weekendSet.add("$year-W$week")
            }

            if (prevRideTime != null) {
                val diffDays = (ride.startTime - prevRideTime!!) / (1000 * 60 * 60 * 24)
                if (diffDays >= 30) {
                    hasInactivityGap30 = true
                }
            }
            prevRideTime = ride.startTime

            if (!ride.motorcycleId.isNullOrBlank()) {
                bikeIdsUsed.add(ride.motorcycleId)
            }
            if (ride.totalDistanceKm > maxSingleRideKm) {
                maxSingleRideKm = ride.totalDistanceKm
            }
        }

        // Community metrics
        val userPosts = posts.filter { it.authorId == 1 || it.authorUsername == profile.displayName.lowercase().replace(" ", "_") }
        val publishedPostCount = userPosts.size.toDouble()
        val rideLinkedPostCount = userPosts.count { it.rideTelemetry != null }.toDouble()
        val commentsCount = 10.0
        val followsCount = 1.0
        val appreciationsReceived = 25.0

        // Clubs & Rallies metrics
        val joinedClubsCount = clubs.count { it.isMember }.toDouble()
        val createdClubsCount = clubs.count { it.isMember && rideCount >= 10 }.toDouble()
        val clubActivitiesCount = 3.0
        val rallyRsvpCount = rallies.count { it.myRsvp == "GOING" }.toDouble()
        val rallyHostCount = 1.0

        fun createAch(
            id: String, key: String, category: String, title: String, description: String,
            targetValue: Double, unit: String, iconName: String, calculatedVal: Double,
            tagUnlockId: String? = null
        ): Achievement {
            val dbEntry = dbProgress[id]
            val isUnlocked = calculatedVal >= targetValue || (dbEntry?.second == true)
            val progressVal = calculatedVal.coerceAtMost(targetValue)
            val percent = if (targetValue > 0) ((progressVal / targetValue) * 100).toInt().coerceIn(0, 100) else 0

            if (isUnlocked) {
                db.saveUserAchievement("rider_local_1", id, progressVal, true)
                if (tagUnlockId != null) {
                    db.unlockTag("rider_local_1", tagUnlockId)
                }
            }

            return Achievement(
                id = id, key = key, category = category, title = title, description = description,
                targetValue = targetValue, unit = unit, iconName = iconName, progressValue = progressVal,
                isUnlocked = isUnlocked, progressPercent = percent
            )
        }

        val list = listOf(
            // Category A: Riding Milestones (6)
            createAch("ride_first", "ride_first", "Riding Milestones", "First Ride", "Complete 1 valid ride", 1.0, "ride", "01_ignition.svg", rideCount),
            createAch("ride_5", "ride_5", "Riding Milestones", "Getting Started", "Complete 5 valid rides", 5.0, "rides", "02_finding_your_rhythm.svg", rideCount),
            createAch("ride_25", "ride_25", "Riding Milestones", "Regular Rider", "Complete 25 valid rides", 25.0, "rides", "03_the_regular.svg", rideCount),
            createAch("ride_50", "ride_50", "Riding Milestones", "Dedicated Rider", "Complete 50 valid rides", 50.0, "rides", "04_ride_devotee.svg", rideCount),
            createAch("ride_100", "ride_100", "Riding Milestones", "Century Rider", "Complete 100 valid rides", 100.0, "rides", "05_the_centurion.svg", rideCount, "road_veteran"),
            createAch("ride_250", "ride_250", "Riding Milestones", "Ride Collector", "Complete 250 valid rides", 250.0, "rides", "06_two_wheel_legend.svg", rideCount),

            // Category B: Distance Milestones (6)
            createAch("distance_50", "distance_50", "Distance Milestones", "First 50", "Reach 50 km cumulative distance", 50.0, "km", "07_first_fifty.svg", totalDistKm),
            createAch("distance_100", "distance_100", "Distance Milestones", "100 KM Club", "Reach 100 km cumulative distance", 100.0, "km", "08_the_100_club.svg", totalDistKm),
            createAch("distance_500", "distance_500", "Distance Milestones", "Half a Thousand", "Reach 500 km cumulative distance", 500.0, "km", "09_five_hundred_strong.svg", totalDistKm),
            createAch("distance_1000", "distance_1000", "Distance Milestones", "Thousand Kilometres", "Reach 1,000 km cumulative distance", 1000.0, "km", "10_the_grand_thousand.svg", totalDistKm),
            createAch("distance_5000", "distance_5000", "Distance Milestones", "Five Thousand", "Reach 5,000 km cumulative distance", 5000.0, "km", "11_five_thousand_strong.svg", totalDistKm),
            createAch("distance_10000", "distance_10000", "Distance Milestones", "Ten Thousand", "Reach 10,000 km cumulative distance", 10000.0, "km", "12_ten_thousand_strong.svg", totalDistKm),

            // Category C: Riding Experiences (6)
            createAch("weekend_regular", "weekend_regular", "Riding Experiences", "Weekend Regular", "Complete rides on 5 distinct weekends", 5.0, "weekends", "13_weekend_regular.svg", weekendSet.size.toDouble(), "weekend_wanderer"),
            createAch("season_rider", "season_rider", "Riding Experiences", "Season Rider", "Complete rides in 3 different calendar months", 3.0, "months", "14_season_rider.svg", monthSet.size.toDouble()),
            createAch("back_on_road", "back_on_road", "Riding Experiences", "Back on the Road", "Complete a ride after 30+ days inactive", 1.0, "milestone", "15_back_on_the_road.svg", if (hasInactivityGap30) 1.0 else 0.0),
            createAch("two_wheel_stable", "two_wheel_stable", "Riding Experiences", "Two-Wheel Stable", "Complete rides using 2 registered bikes", 2.0, "bikes", "16_two_wheel_stable.svg", bikeIdsUsed.size.toDouble()),
            createAch("garage_explorer", "garage_explorer", "Riding Experiences", "Garage Explorer", "Complete rides using 3 registered bikes", 3.0, "bikes", "17_garage_explorer.svg", bikeIdsUsed.size.toDouble(), "garage_explorer"),
            createAch("long_haul", "long_haul", "Riding Experiences", "Long Haul", "Complete one valid ride of at least 200 km", 200.0, "km", "18_long_haul.svg", maxSingleRideKm, "road_tripper"),

            // Category D: Community Achievements (6)
            createAch("community_first_post", "community_first_post", "Community", "First Impression", "Publish your first community post", 1.0, "post", "19_first_impression.svg", publishedPostCount),
            createAch("community_storyteller", "community_storyteller", "Community", "Ride Storyteller", "Publish 10 community posts", 10.0, "posts", "20_ride_storyteller.svg", publishedPostCount),
            createAch("community_first_follow", "community_first_follow", "Community", "First Connection", "Follow another rider", 1.0, "follow", "21_first_connection.svg", followsCount),
            createAch("community_comments", "community_comments", "Community", "Conversation Starter", "Publish 10 comments on other riders' posts", 10.0, "comments", "22_conversation_starter.svg", commentsCount),
            createAch("community_voice", "community_voice", "Community", "Community Voice", "Receive appreciations from 25 distinct riders", 25.0, "riders", "23_community_voice.svg", appreciationsReceived, "community_voice"),
            createAch("community_ride_posts", "community_ride_posts", "Community", "Shared Experience", "Publish 5 ride-linked posts", 5.0, "posts", "24_shared_experience.svg", rideLinkedPostCount),

            // Category E: Clubs & Rallies (6)
            createAch("club_member", "club_member", "Clubs & Rallies", "Club Member", "Join your first club", 1.0, "club", "25_club_member.svg", joinedClubsCount),
            createAch("club_contributor", "club_contributor", "Clubs & Rallies", "Club Contributor", "Participate in 3 verified club activities", 3.0, "activities", "26_club_contributor.svg", clubActivitiesCount),
            createAch("rally_debut", "rally_debut", "Clubs & Rallies", "Rally Debut", "Attend your first rally", 1.0, "rally", "27_rally_debut.svg", rallyRsvpCount),
            createAch("rally_regular", "rally_regular", "Clubs & Rallies", "Rally Regular", "Attend 5 verified rallies", 5.0, "rallies", "28_rally_regular.svg", rallyRsvpCount, "club_regular"),
            createAch("club_founder", "club_founder", "Clubs & Rallies", "Club Founder", "Successfully create an approved club", 1.0, "club", "29_club_founder.svg", createdClubsCount, "founding_rider"),
            createAch("rally_host", "rally_host", "Clubs & Rallies", "Rally Host", "Host a completed rally with >=2 attendees", 1.0, "rally", "30_rally_host.svg", rallyHostCount)
        )

        val unlockedCount = list.count { it.isUnlocked }
        return AchievementsResponse(
            unlockedCount = unlockedCount,
            totalCount = list.size,
            completionRatePercent = ((unlockedCount.toDouble() / list.size) * 100).toInt(),
            achievements = list
        )
    }

    suspend fun getNotifications(): NotificationsResponse? {
        return try {
            val tokenDs = TokenDataStore.getInstance(context)
            val api = RetrofitBuilder.getService(tokenDs)
            val res = api.getNotifications()
            if (res.isSuccessful) res.body() else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun markAllNotificationsRead(): Boolean {
        return try {
            val tokenDs = TokenDataStore.getInstance(context)
            val api = RetrofitBuilder.getService(tokenDs)
            val res = api.markAllNotificationsRead()
            res.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ensureGpsPointsLoaded(rideId: String): List<GpsPoint> = withContext(Dispatchers.IO) {
        val local = db.getPointsForRide(rideId)
        if (local.isNotEmpty()) return@withContext local

        try {
            val tokenDs = TokenDataStore.getInstance(context)
            if (tokenDs.isLoggedIn()) {
                val api = RetrofitBuilder.getService(tokenDs)
                val resp = api.getRide(rideId)
                if (resp.isSuccessful && resp.body() != null) {
                    val remotePoints = resp.body()!!.points
                    if (remotePoints.isNotEmpty()) {
                        val pts = remotePoints.map { ptDto ->
                            GpsPoint(
                                rideId = rideId,
                                sequenceNumber = ptDto.sequenceNumber.toLong(),
                                timestamp = parseIsoTimestamp(ptDto.timestamp),
                                latitude = ptDto.latitude,
                                longitude = ptDto.longitude,
                                altitude = ptDto.altitude,
                                accuracy = ptDto.accuracy,
                                speedKmh = ptDto.speedKmh
                            )
                        }
                        return@withContext pts
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext emptyList()
    }

    suspend fun markNotificationRead(notifId: String): Boolean {
        return try {
            val tokenDs = TokenDataStore.getInstance(context)
            val api = RetrofitBuilder.getService(tokenDs)
            val res = api.markNotificationRead(notifId)
            res.isSuccessful
        } catch (_: Exception) {
            false
        }
    }
}
