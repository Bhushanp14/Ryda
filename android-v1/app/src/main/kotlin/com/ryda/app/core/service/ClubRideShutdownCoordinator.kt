package com.ryda.app.core.service

import android.content.Context
import android.util.Log
import com.ryda.app.core.sync.SyncCoordinator
import com.ryda.app.data.repository.RydaRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ClubRideShutdownCoordinator {
    private const val TAG = "ClubRideShutdown"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val isShuttingDown = AtomicBoolean(false)

    /**
     * Centralized, idempotent entry point to safely finalize a Club Ride session,
     * clean up Google Navigation SDK resources, stop location sharing, and finalize ride recording.
     */
    fun handleSessionEnd(
        context: Context,
        repository: RydaRepository?,
        clubId: String?,
        sessionId: String?,
        isAdminEnding: Boolean,
        onNavigationCleanup: (suspend () -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        if (!isShuttingDown.compareAndSet(false, true)) {
            Log.d(TAG, "Shutdown sequence already in progress or completed. Skipping duplicate execution.")
            onComplete?.invoke()
            return
        }

        scope.launch {
            try {
                Log.i(TAG, "Starting centralized Club Ride shutdown sequence (isAdminEnding=$isAdminEnding)...")

                // 1. Navigation SDK Graceful Cleanup
                try {
                    onNavigationCleanup?.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during navigation cleanup: ${e.message}", e)
                }

                // 2. Stop Live Location Sharing Service
                try {
                    LiveLocationService.stopSharing(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping LiveLocationService: ${e.message}", e)
                }

                // 3. Finalize Individual Ride Recording if actively tracking a linked ride
                try {
                    val tracking = RideTrackingService.trackingState.value
                    val currentRideId = tracking.currentRideId
                    if (tracking.isTracking) {
                        Log.i(TAG, "Finalizing individual ride tracking (rideId=$currentRideId)...")
                        RideTrackingService.finishRide(context)

                        // Trigger sync for completed ride
                        SyncCoordinator.getInstance(context).triggerSync()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error finalizing RideTrackingService: ${e.message}", e)
                }

                // 4. Admin API Complete (if triggered by Admin tap)
                if (isAdminEnding && !clubId.isNullOrBlank() && !sessionId.isNullOrBlank() && repository != null) {
                    try {
                        repository.completeClubRide(clubId, sessionId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calling completeClubRide API: ${e.message}", e)
                    }
                }

                Log.i(TAG, "Centralized Club Ride shutdown sequence completed successfully.")
            } finally {
                isShuttingDown.set(false)
                onComplete?.invoke()
            }
        }
    }
}
