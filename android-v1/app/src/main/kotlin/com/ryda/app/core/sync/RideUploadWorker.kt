package com.ryda.app.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ryda.app.core.auth.TokenDataStore
import com.ryda.app.core.database.RydaDatabaseHelper
import com.ryda.app.core.models.SyncStatus
import com.ryda.app.core.network.BatchPointsRequest
import com.ryda.app.core.network.CompleteRideRequest
import com.ryda.app.core.network.GpsPointDto
import com.ryda.app.core.network.RetrofitBuilder
import com.ryda.app.core.network.RideRequest
import com.ryda.app.data.repository.RydaRepository
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class RideUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)

    override suspend fun doWork(): Result {
        val db = RydaDatabaseHelper.getInstance(applicationContext)
        val tokenDs = TokenDataStore.getInstance(applicationContext)

        // Skip sync if not logged in — rides stay LOCAL_ONLY until user authenticates
        if (!tokenDs.isLoggedIn()) return Result.success()

        // Skip sync if user has explicitly enabled offline-only mode in Settings
        if (tokenDs.isOfflineOnly()) return Result.success()

        val api = RetrofitBuilder.getService(tokenDs)

        val unsyncedRides = db.getAllCompletedRides().filter {
            it.syncStatus == SyncStatus.LOCAL_ONLY || it.syncStatus == SyncStatus.SYNC_PENDING
        }

        if (unsyncedRides.isEmpty()) return Result.success()

        var allOk = true

        for (ride in unsyncedRides) {
            try {
                db.updateRide(ride.copy(syncStatus = SyncStatus.SYNC_PENDING))

                // 1. Register ride on server (idempotent via client UUID)
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
                android.util.Log.d("RYDA_UPLOAD", "Uploading ride header: id=${ride.id}, dist=${ride.totalDistanceMeters}m")
                val createResp = api.createRide(rideReq)
                if (!createResp.isSuccessful && createResp.code() != 400) {
                    // 400 may mean already exists — that's fine, continue with points
                    if (createResp.code() in 500..599) {
                        android.util.Log.e("RYDA_UPLOAD", "createRide failed: HTTP ${createResp.code()}")
                        db.updateRide(ride.copy(syncStatus = SyncStatus.LOCAL_ONLY))
                        allOk = false
                        continue
                    }
                }

                // 2. Upload GPS points in batches of 150
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
                    val batchResp = api.uploadPoints(ride.id, BatchPointsRequest(
                        points = dtos,
                        batchId = "${ride.id}-batch-$batchIdx",
                        batchIndex = batchIdx,
                        sequenceStart = seqStart,
                        sequenceEnd = seqStart + batch.size - 1
                    ))
                    if (!batchResp.isSuccessful && batchResp.code() in 500..599) {
                        android.util.Log.e("RYDA_UPLOAD", "uploadPoints batch $batchIdx failed: HTTP ${batchResp.code()}")
                        db.updateRide(ride.copy(syncStatus = SyncStatus.LOCAL_ONLY))
                        allOk = false
                        break
                    }
                }

                // 3. Mark complete (triggers server-side recalculation)
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
                    android.util.Log.d("RYDA_UPLOAD", "Ride marked complete successfully on backend: ${ride.id}")
                    db.updateRide(ride.copy(syncStatus = SyncStatus.SYNCED))
                } else {
                    android.util.Log.w("RYDA_UPLOAD", "completeRide response: HTTP ${completeResp.code()}")
                    db.updateRide(ride.copy(syncStatus = SyncStatus.LOCAL_ONLY))
                    allOk = false
                }

            } catch (e: Exception) {
                android.util.Log.e("RYDA_UPLOAD", "Sync error for ride ${ride.id}: ${e.message}", e)
                db.updateRide(ride.copy(syncStatus = SyncStatus.LOCAL_ONLY))
                allOk = false
            }
        }

        RydaRepository.getInstance(applicationContext).refreshAll()

        return if (allOk) Result.success() else Result.retry()
    }
}
