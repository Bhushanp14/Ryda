package com.ryda.app.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncManager {

    private const val UNIQUE_ONE_TIME_WORK = "ryda_one_time_ride_upload"
    private const val UNIQUE_PERIODIC_WORK = "ryda_periodic_ride_sync"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Enqueues an immediate one-time sync task when user taps "Sync Now" or finishes a ride.
     */
    fun triggerImmediateSync(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<RideUploadWorker>()
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ONE_TIME_WORK,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Schedules periodic background sync (every 6 hours) when connected to network.
     */
    fun schedulePeriodicSync(context: Context) {
        val periodicRequest = PeriodicWorkRequestBuilder<RideUploadWorker>(6, TimeUnit.HOURS)
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }
}
