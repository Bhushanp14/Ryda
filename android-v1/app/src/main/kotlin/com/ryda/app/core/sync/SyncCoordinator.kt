package com.ryda.app.core.sync

import android.content.Context
import android.net.Uri
import com.ryda.app.core.auth.TokenDataStore
import com.ryda.app.core.database.RydaDatabaseHelper
import com.ryda.app.core.models.MediaSyncStatus
import com.ryda.app.core.models.OutboxOperation
import com.ryda.app.core.models.OutboxStatus
import com.ryda.app.core.models.SyncStatus
import com.ryda.app.core.network.BatchPointsRequest
import com.ryda.app.core.network.CompleteRideRequest
import com.ryda.app.core.network.GpsPointDto
import com.ryda.app.core.network.NetworkMonitor
import com.ryda.app.core.network.RetrofitBuilder
import com.ryda.app.core.network.RideRequest
import com.ryda.app.core.service.RideTrackingService
import com.ryda.app.data.repository.RydaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class SyncState {
    IDLE,
    SYNCING,
    COMPLETED,
    FAILED,
    SKIPPED_ACTIVE_RIDE,
    SKIPPED_OFFLINE,
    SKIPPED_NOT_LOGGED_IN
}

class SyncCoordinator private constructor(private val context: Context) {

    private val db = RydaDatabaseHelper.getInstance(context)
    private val tokenDs = TokenDataStore.getInstance(context)
    private val networkMonitor = NetworkMonitor.getInstance(context)
    private val repository = RydaRepository.getInstance(context)

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var syncJob: Job? = null

    private val isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: SyncCoordinator? = null

        fun getInstance(context: Context): SyncCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncCoordinator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Evaluates all gating conditions:
     * [1] User Logged In?
     * [2] Device Online?
     * [3] Offline-Only Mode Disabled?
     * [4] CRITICAL GUARD: Is Ride Active or Paused? (Must be FALSE)
     */
    suspend fun canSync(): Boolean {
        val trackingState = RideTrackingService.trackingState.value
        if (trackingState.isTracking || trackingState.isPaused) {
            _syncState.value = SyncState.SKIPPED_ACTIVE_RIDE
            return false
        }
        if (!tokenDs.isLoggedIn()) {
            _syncState.value = SyncState.SKIPPED_NOT_LOGGED_IN
            return false
        }
        if (tokenDs.isOfflineOnly()) {
            _syncState.value = SyncState.SKIPPED_OFFLINE
            return false
        }
        if (!networkMonitor.isOnline.value) {
            _syncState.value = SyncState.SKIPPED_OFFLINE
            return false
        }
        return true
    }

    /**
     * Triggers background sync on Dispatchers.IO if all gating conditions pass.
     */
    fun triggerSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            if (!canSync()) return@launch
            executeSyncPipeline()
        }
    }

    private suspend fun executeSyncPipeline() {
        _syncState.value = SyncState.SYNCING
        var allOk = true

        try {
            val api = RetrofitBuilder.getService(tokenDs)
            val unsyncedRides = db.getAllCompletedRides().filter {
                it.syncStatus == SyncStatus.LOCAL_ONLY || it.syncStatus == SyncStatus.SYNC_PENDING
            }

            if (unsyncedRides.isEmpty()) {
                _syncState.value = SyncState.COMPLETED
                return
            }

            for (ride in unsyncedRides) {
                // Check Active Ride Guard again before starting each ride upload
                val currentTracking = RideTrackingService.trackingState.value
                if (currentTracking.isTracking || currentTracking.isPaused) {
                    _syncState.value = SyncState.SKIPPED_ACTIVE_RIDE
                    return
                }

                try {
                    db.updateRide(ride.copy(syncStatus = SyncStatus.SYNC_PENDING))

                    // ── Stage 1: Completed Rides Sync Header ────────────────────────
                    val rideReq = RideRequest(
                        id = ride.id,
                        title = ride.title,
                        startTime = isoFmt.format(Instant.ofEpochMilli(ride.startTime)),
                        endTime = ride.endTime?.let { isoFmt.format(Instant.ofEpochMilli(it)) },
                        motorcycleId = ride.motorcycleId,
                        motorcycleIdAlias = ride.motorcycleId,
                        isPrivate = ride.isPrivate,
                        totalDistanceMeters = ride.totalDistanceMeters,
                        movingTimeSeconds = ride.movingTimeSeconds,
                        elapsedTimeSeconds = ride.elapsedTimeSeconds,
                        maxSpeedKmh = ride.maxSpeedKmh,
                        avgSpeedKmh = ride.avgSpeedKmh,
                        elevationGainMeters = ride.elevationGainMeters,
                        elevationLossMeters = ride.elevationLossMeters
                    )
                    android.util.Log.d("RYDA_SYNC", "Syncing ride header: id=${ride.id}, dist=${ride.totalDistanceMeters}m")
                    val createResp = api.createRide(rideReq)
                    var rideHeaderCreated = createResp.isSuccessful
                    if (!createResp.isSuccessful) {
                        if (createResp.code() == 400) {
                            // If motorcycle ID is invalid on server, fallback without motorcycle reference so ride creation succeeds
                            val fallbackReq = rideReq.copy(motorcycleId = null, motorcycleIdAlias = null)
                            val retryResp = api.createRide(fallbackReq)
                            if (retryResp.isSuccessful) {
                                rideHeaderCreated = true
                            } else {
                                android.util.Log.e("RYDA_SYNC", "Retry createRide failed: HTTP ${retryResp.code()}")
                                db.updateRide(ride.copy(syncStatus = SyncStatus.LOCAL_ONLY))
                                allOk = false
                                continue
                            }
                        } else {
                            android.util.Log.e("RYDA_SYNC", "createRide failed: HTTP ${createResp.code()}")
                            db.updateRide(ride.copy(syncStatus = SyncStatus.LOCAL_ONLY))
                            allOk = false
                            continue
                        }
                    }
                    if (!rideHeaderCreated) {
                        continue
                    }

                    // ── GPS Points 150-Chunk Batch Upload ───────────────────────────
                    val points = db.getPointsForRide(ride.id)
                    val batches = points.chunked(150)
                    for ((batchIdx, batch) in batches.withIndex()) {
                        val seqStart = batchIdx * 150
                        val dtos = batch.mapIndexed { idx, pt ->
                            GpsPointDto(
                                latitude = pt.latitude,
                                longitude = pt.longitude,
                                altitude = pt.altitude,
                                speedKmh = pt.speedKmh,
                                accuracy = pt.accuracy,
                                timestamp = isoFmt.format(Instant.ofEpochMilli(pt.timestamp)),
                                sequenceNumber = seqStart + idx
                            )
                        }
                        val batchResp = api.uploadPoints(
                            ride.id,
                            BatchPointsRequest(
                                points = dtos,
                                batchId = "${ride.id}-batch-$batchIdx",
                                batchIndex = batchIdx,
                                sequenceStart = seqStart,
                                sequenceEnd = seqStart + batch.size - 1
                            )
                        )
                        if (!batchResp.isSuccessful && batchResp.code() in 500..599) {
                            android.util.Log.e("RYDA_SYNC", "uploadPoints batch $batchIdx error: HTTP ${batchResp.code()}")
                            allOk = false
                            break
                        }
                    }

                    // ── Ride Completion Trigger ─────────────────────────────────────
                    val endTime = ride.endTime ?: System.currentTimeMillis()
                    val completeResp = api.completeRide(
                        ride.id,
                        CompleteRideRequest(
                            endTime = isoFmt.format(Instant.ofEpochMilli(endTime)),
                            totalDistanceMeters = ride.totalDistanceMeters,
                            movingTimeSeconds = ride.movingTimeSeconds,
                            elapsedTimeSeconds = ride.elapsedTimeSeconds,
                            maxSpeedKmh = ride.maxSpeedKmh,
                            avgSpeedKmh = ride.avgSpeedKmh,
                            elevationGainMeters = ride.elevationGainMeters,
                            elevationLossMeters = ride.elevationLossMeters,
                            expectedPointsCount = points.size,
                            expectedBatchesCount = batches.size
                        )
                    )
                    if (completeResp.isSuccessful) {
                        android.util.Log.d("RYDA_SYNC", "Ride completion acknowledged for ${ride.id}")
                    } else {
                        android.util.Log.w("RYDA_SYNC", "completeRide response: HTTP ${completeResp.code()}")
                    }

                    // ── Stage 2: Permanent Media Upload (Isolated Guard) ────────────
                    var updatedMediaStatus = ride.mediaSyncStatus
                    if (!ride.photoUri.isNullOrBlank() && ride.mediaSyncStatus != MediaSyncStatus.UPLOADED) {
                        try {
                            val savedInternalUri = com.ryda.app.core.utils.MediaStorageHelper.saveImageToInternalStorage(
                                context,
                                ride.photoUri,
                                "ride_photos",
                                "ride_${ride.id}"
                            ) ?: ride.photoUri
                            db.updateRidePhoto(ride.id, savedInternalUri)

                            val photoUri = Uri.parse(savedInternalUri)
                            context.contentResolver.openInputStream(photoUri)?.use { input ->
                                val bytes = input.readBytes()
                                val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                                val part = MultipartBody.Part.createFormData("photo", "ride_${ride.id}.jpg", body)
                                val photoResp = api.uploadRidePhoto(ride.id, part)
                                if (photoResp.isSuccessful) {
                                    updatedMediaStatus = MediaSyncStatus.UPLOADED
                                } else {
                                    updatedMediaStatus = MediaSyncStatus.FAILED
                                }
                            }
                        } catch (_: Exception) {
                            updatedMediaStatus = MediaSyncStatus.FAILED
                        }
                    }

                    // ── Stage 4: Finalize State & Persist Local Record as SYNCED ────
                    val photoUploadOk = ride.photoUri.isNullOrBlank() || updatedMediaStatus == MediaSyncStatus.UPLOADED
                    if (completeResp.isSuccessful && photoUploadOk) {
                        db.updateRide(ride.copy(
                            syncStatus = SyncStatus.SYNCED,
                            mediaSyncStatus = updatedMediaStatus
                        ))
                    } else {
                        db.updateRide(ride.copy(
                            syncStatus = SyncStatus.LOCAL_ONLY,
                            mediaSyncStatus = updatedMediaStatus
                        ))
                        allOk = false
                    }

                } catch (_: Exception) {
                    db.updateRide(ride.copy(syncStatus = SyncStatus.LOCAL_ONLY))
                    allOk = false
                }
            }

            repository.refreshAll()
            _syncState.value = if (allOk) SyncState.COMPLETED else SyncState.FAILED

        } catch (_: Exception) {
            _syncState.value = SyncState.FAILED
        }
    }
}
