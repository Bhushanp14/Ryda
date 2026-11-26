package com.ryda.app.core.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ryda.app.MainActivity
import com.ryda.app.core.database.RydaDatabaseHelper
import com.ryda.app.core.location.GpsFilter
import com.ryda.app.core.location.Haversine
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.Ride
import com.ryda.app.core.models.RideStatus
import com.ryda.app.core.models.SyncStatus
import com.ryda.app.core.models.TrackingState
import com.ryda.app.data.repository.RydaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

class RideTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var dbHelper: RydaDatabaseHelper

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var timerJob: Job? = null
    private var simulationJob: Job? = null

    private val gpsFilter = GpsFilter()
    private var sequenceNumber = 0L
    private var lastRecordedLocation: Location? = null
    private var lastMovingTimestamp: Long = 0L

    companion object {
        const val CHANNEL_ID = "ryda_tracking_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_FINISH = "ACTION_FINISH"
        const val ACTION_DISCARD = "ACTION_DISCARD"
        const val ACTION_START_SIMULATION = "ACTION_START_SIMULATION"
        const val ACTION_RECOVER = "ACTION_RECOVER"

        const val EXTRA_MOTORCYCLE_ID = "EXTRA_MOTORCYCLE_ID"
        const val EXTRA_RIDE_TITLE = "EXTRA_RIDE_TITLE"
        const val EXTRA_RIDE_ID = "EXTRA_RIDE_ID"
        const val EXTRA_CLUB_RIDE_SESSION_ID = "EXTRA_CLUB_RIDE_SESSION_ID"

        private val _trackingState = MutableStateFlow(TrackingState())
        val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

        fun recoverRide(context: Context, rideId: String) {
            val intent = Intent(context, RideTrackingService::class.java).apply {
                action = ACTION_RECOVER
                putExtra(EXTRA_RIDE_ID, rideId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startRide(
            context: Context,
            motorcycleId: String? = null,
            title: String? = null,
            isSimulation: Boolean = false,
            rideIdOverride: String? = null,
            clubRideSessionId: String? = null
        ) {
            val intent = Intent(context, RideTrackingService::class.java).apply {
                action = if (isSimulation) ACTION_START_SIMULATION else ACTION_START
                putExtra(EXTRA_MOTORCYCLE_ID, motorcycleId)
                putExtra(EXTRA_RIDE_TITLE, title)
                if (rideIdOverride != null) putExtra(EXTRA_RIDE_ID, rideIdOverride)
                if (clubRideSessionId != null) putExtra(EXTRA_CLUB_RIDE_SESSION_ID, clubRideSessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseRide(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).apply { action = ACTION_PAUSE }
            context.startService(intent)
        }

        fun resumeRide(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).apply { action = ACTION_RESUME }
            context.startService(intent)
        }

        fun finishRide(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).apply { action = ACTION_FINISH }
            context.startService(intent)
        }

        fun discardRide(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).apply { action = ACTION_DISCARD }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        dbHelper = RydaDatabaseHelper.getInstance(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    processNewLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Android system recreated the killed foreground service (START_STICKY)
            val incompleteRide = dbHelper.getIncompleteRide()
            if (incompleteRide != null) {
                recoverTracking(incompleteRide.id)
            } else {
                stopSelf()
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val bikeId = intent.getStringExtra(EXTRA_MOTORCYCLE_ID)
                val title = intent.getStringExtra(EXTRA_RIDE_TITLE) ?: "Motorcycle Ride"
                val rideIdOverride = intent.getStringExtra(EXTRA_RIDE_ID)
                val clubSessionId = intent.getStringExtra(EXTRA_CLUB_RIDE_SESSION_ID)
                startTracking(bikeId, title, isSimulation = false, rideIdOverride = rideIdOverride, clubRideSessionId = clubSessionId)
            }
            ACTION_START_SIMULATION -> {
                val bikeId = intent.getStringExtra(EXTRA_MOTORCYCLE_ID)
                val title = intent.getStringExtra(EXTRA_RIDE_TITLE) ?: "Simulated Mountain Ride"
                val rideIdOverride = intent.getStringExtra(EXTRA_RIDE_ID)
                val clubSessionId = intent.getStringExtra(EXTRA_CLUB_RIDE_SESSION_ID)
                startTracking(bikeId, title, isSimulation = true, rideIdOverride = rideIdOverride, clubRideSessionId = clubSessionId)
            }
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_FINISH -> finishTracking()
            ACTION_DISCARD -> discardTracking()
            ACTION_RECOVER -> {
                val rideId = intent.getStringExtra(EXTRA_RIDE_ID)
                if (rideId != null) {
                    recoverTracking(rideId)
                }
            }
        }
        return START_STICKY
    }

    private fun recoverTracking(rideId: String) {
        val existingRide = dbHelper.getRideById(rideId) ?: return
        val points = dbHelper.getPointsForRide(rideId)
        sequenceNumber = (points.maxOfOrNull { it.sequenceNumber } ?: 0L) + 1L
        lastRecordedLocation = points.lastOrNull()?.let { pt ->
            Location("recovered").apply {
                latitude = pt.latitude
                longitude = pt.longitude
                altitude = pt.altitude
                speed = (pt.speedKmh / 3.6).toFloat()
                accuracy = pt.accuracy
                time = pt.timestamp
            }
        }
        gpsFilter.reset()

        val isPaused = existingRide.status == RideStatus.PAUSED
        _trackingState.value = TrackingState(
            isTracking = true,
            isPaused = isPaused,
            currentRideId = rideId,
            totalDistanceMeters = existingRide.totalDistanceMeters,
            movingTimeSeconds = existingRide.movingTimeSeconds,
            elapsedTimeSeconds = existingRide.elapsedTimeSeconds,
            maxSpeedKmh = existingRide.maxSpeedKmh,
            averageSpeedKmh = existingRide.avgSpeedKmh,
            isSimulationMode = false
        )

        startForeground(NOTIFICATION_ID, buildNotification("Recovered Ride Tracking", "${String.format("%.2f", existingRide.totalDistanceMeters / 1000.0)} km"))
        startTimer()
        startLocationUpdates()
    }

    private fun startTracking(
        motorcycleId: String?,
        title: String,
        isSimulation: Boolean,
        rideIdOverride: String? = null,
        clubRideSessionId: String? = null
    ) {
        // Prevent duplicate active recording
        if (_trackingState.value.isTracking) return

        // Prevent duplicate individual ride for the same Club Ride session
        if (clubRideSessionId != null) {
            val existingSessionRide = dbHelper.getRideByClubSessionId(clubRideSessionId)
            if (existingSessionRide != null) {
                if (existingSessionRide.status == RideStatus.RECORDING || existingSessionRide.status == RideStatus.PAUSED) {
                    recoverTracking(existingSessionRide.id)
                }
                return
            }
        }

        val rideId = rideIdOverride ?: UUID.randomUUID().toString()
        sequenceNumber = 0L
        lastRecordedLocation = null
        gpsFilter.reset()

        val initialRide = Ride(
            id = rideId,
            motorcycleId = motorcycleId,
            title = title,
            status = RideStatus.RECORDING,
            startTime = System.currentTimeMillis(),
            clubRideSessionId = clubRideSessionId
        )
        dbHelper.insertRide(initialRide)

        _trackingState.value = TrackingState(
            isTracking = true,
            isPaused = false,
            currentRideId = rideId,
            isSimulationMode = isSimulation
        )

        startForeground(NOTIFICATION_ID, buildNotification("Recording ride...", "0.0 km • 00:00 • 0 km/h"))
        startTimer()

        if (isSimulation) {
            startSimulation(rideId)
        } else {
            startLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500)
                .setMinUpdateIntervalMillis(1000)
                .setMinUpdateDistanceMeters(2.0f)
                .build()
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Handled via permission checking in UI
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun processNewLocation(location: Location) {
        val currentState = _trackingState.value
        if (!currentState.isTracking || currentState.isPaused) return

        val speedKmh = if (location.hasSpeed()) (location.speed * 3.6) else 0.0

        val gpsPoint = GpsPoint(
            rideId = currentState.currentRideId ?: return,
            sequenceNumber = ++sequenceNumber,
            timestamp = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else 0.0,
            accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
            speedKmh = speedKmh
        )

        if (!gpsFilter.isPointValid(gpsPoint)) {
            return
        }

        // Calculate distance added
        val lastLoc = lastRecordedLocation
        var addedDistance = 0.0
        if (lastLoc != null) {
            addedDistance = Haversine.distanceInMeters(
                lastLoc.latitude, lastLoc.longitude,
                location.latitude, location.longitude
            )
        }
        lastRecordedLocation = location

        // Moving time rule: speed >= 1.5 m/s (~5.4 km/h)
        val isMoving = (location.hasSpeed() && location.speed >= 1.5f) || addedDistance > 3.0

        val updatedDistance = currentState.totalDistanceMeters + addedDistance
        val updatedMaxSpeed = max(currentState.maxSpeedKmh, speedKmh)
        val updatedMovingTime = if (isMoving) currentState.movingTimeSeconds + 2 else currentState.movingTimeSeconds
        val updatedAvgSpeed = if (updatedMovingTime > 0) (updatedDistance / updatedMovingTime) * 3.6 else 0.0

        // Save point to local database
        dbHelper.insertGpsPoint(gpsPoint)

        _trackingState.value = currentState.copy(
            currentSpeedKmh = speedKmh,
            maxSpeedKmh = updatedMaxSpeed,
            averageSpeedKmh = updatedAvgSpeed,
            totalDistanceMeters = updatedDistance,
            movingTimeSeconds = updatedMovingTime,
            gpsAccuracyMeters = gpsPoint.accuracy,
            currentLatitude = location.latitude,
            currentLongitude = location.longitude,
            recordedPointsCount = currentState.recordedPointsCount + 1
        )

        updateNotification()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val state = _trackingState.value
                if (state.isTracking && !state.isPaused) {
                    _trackingState.value = state.copy(
                        elapsedTimeSeconds = state.elapsedTimeSeconds + 1
                    )
                    // Periodic update of ride in DB every 5 seconds for crash recovery
                    if (state.elapsedTimeSeconds % 5 == 0L) {
                        saveCurrentProgress()
                        updateNotification()
                    }
                }
            }
        }
    }

    private fun saveCurrentProgress() {
        val state = _trackingState.value
        val rideId = state.currentRideId ?: return
        val currentRide = dbHelper.getRideById(rideId) ?: return

        dbHelper.updateRide(
            currentRide.copy(
                totalDistanceMeters = state.totalDistanceMeters,
                movingTimeSeconds = state.movingTimeSeconds,
                elapsedTimeSeconds = state.elapsedTimeSeconds,
                maxSpeedKmh = state.maxSpeedKmh,
                avgSpeedKmh = state.averageSpeedKmh
            )
        )
    }

    private fun pauseTracking() {
        _trackingState.value = _trackingState.value.copy(isPaused = true, currentSpeedKmh = 0.0)
        saveCurrentProgress()
        updateNotification()
    }

    private fun resumeTracking() {
        _trackingState.value = _trackingState.value.copy(isPaused = false)
        updateNotification()
    }

    private fun finishTracking() {
        val state = _trackingState.value
        val rideId = state.currentRideId

        stopLocationUpdates()
        simulationJob?.cancel()
        timerJob?.cancel()

        if (rideId != null) {
            val currentRide = dbHelper.getRideById(rideId)
            if (currentRide != null) {
                val completedRide = currentRide.copy(
                    status = RideStatus.COMPLETED,
                    endTime = System.currentTimeMillis(),
                    totalDistanceMeters = state.totalDistanceMeters,
                    movingTimeSeconds = state.movingTimeSeconds,
                    elapsedTimeSeconds = state.elapsedTimeSeconds,
                    maxSpeedKmh = state.maxSpeedKmh,
                    avgSpeedKmh = if (state.movingTimeSeconds > 0) (state.totalDistanceMeters / state.movingTimeSeconds) * 3.6 else state.averageSpeedKmh,
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                dbHelper.updateRide(completedRide)
                RydaRepository.getInstance(applicationContext).recalculateAggregates()
                RydaRepository.getInstance(applicationContext).refreshAll()
                com.ryda.app.core.sync.SyncManager.triggerImmediateSync(applicationContext)
            }
        }

        _trackingState.value = TrackingState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun discardTracking() {
        val rideId = _trackingState.value.currentRideId
        stopLocationUpdates()
        simulationJob?.cancel()
        timerJob?.cancel()

        if (rideId != null) {
            dbHelper.deleteRide(rideId)
        }

        _trackingState.value = TrackingState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- REALISTIC MOTORCYCLE SIMULATION FOR EMULATOR TESTING ---

    private data class SimWaypoint(val lat: Double, val lon: Double, val alt: Double, val targetSpeedKmh: Double)

    private fun startSimulation(rideId: String) {
        simulationJob?.cancel()
        simulationJob = serviceScope.launch {
            // Route A: Mumbai to Lonavala via Old NH4 Highway (Ghat climb)
            val routeA = listOf(
                SimWaypoint(18.9894, 73.1175, 30.0, 75.0),   // Panvel
                SimWaypoint(18.9482, 73.1812, 45.0, 85.0),   // Shedung Toll
                SimWaypoint(18.9054, 73.2355, 75.0, 92.0),   // Chowk Junction
                SimWaypoint(18.8352, 73.2981, 95.0, 98.0),   // Khalapur Straight (Max Speed ~98 km/h)
                SimWaypoint(18.7885, 73.3482, 110.0, 55.0),  // Khopoli Base
                SimWaypoint(18.7620, 73.3650, 380.0, 42.0),  // Bhor Ghat Hairpin
                SimWaypoint(18.7554, 73.3762, 580.0, 48.0),  // Khandala Top
                SimWaypoint(18.7502, 73.4065, 625.0, 65.0)   // Lonavala Peak
            )

            // Route B: Pune to Malshej Ghat
            val routeB = listOf(
                SimWaypoint(18.5524, 73.8268, 560.0, 65.0),  // Pune
                SimWaypoint(18.7602, 73.8584, 605.0, 85.0),  // Chakan
                SimWaypoint(18.9621, 73.9125, 620.0, 95.0),  // Pabal Phata
                SimWaypoint(19.1178, 73.9805, 650.0, 98.0),  // Narayangaon Straight (Max Speed ~98 km/h)
                SimWaypoint(19.2215, 74.1025, 640.0, 80.0),  // Alephata Junction
                SimWaypoint(19.2604, 73.9002, 620.0, 60.0),  // Otur Valley
                SimWaypoint(19.3408, 73.7725, 710.0, 38.0),  // Malshej Ghat Pass
                SimWaypoint(19.3512, 73.7610, 735.0, 55.0)   // Malshej Plateau
            )

            // Randomly select Route A or Route B
            val selectedRoute = if ((0..1).random() == 0) routeA else routeB

            var waypointIdx = 0
            var currentLat = selectedRoute[0].lat
            var currentLon = selectedRoute[0].lon
            var currentAlt = selectedRoute[0].alt
            var currentSpeedKmh = selectedRoute[0].targetSpeedKmh

            while (isActive) {
                delay(2000)
                val state = _trackingState.value
                if (state.isTracking && !state.isPaused) {
                    val targetWp = selectedRoute[waypointIdx]

                    // Smooth speed adjustments toward target waypoint speed, strictly capped at 98.5 km/h (speed limit <= 100 km/h)
                    val speedDiff = targetWp.targetSpeedKmh - currentSpeedKmh
                    currentSpeedKmh += (speedDiff * 0.15) + (-2..2).random()
                    currentSpeedKmh = currentSpeedKmh.coerceIn(30.0, 98.5)

                    val speedMps = currentSpeedKmh / 3.6
                    val distanceStepMeters = speedMps * 2.0 // 2 seconds step

                    // Distance to target waypoint
                    val distanceToTarget = Haversine.distanceInMeters(currentLat, currentLon, targetWp.lat, targetWp.lon)

                    if (distanceToTarget <= distanceStepMeters) {
                        // Reached waypoint smoothly, target next waypoint
                        currentLat = targetWp.lat
                        currentLon = targetWp.lon
                        currentAlt = targetWp.alt
                        waypointIdx = (waypointIdx + 1) % selectedRoute.size
                    } else {
                        // Step forward by EXACTLY distanceStepMeters along path
                        val stepFraction = (distanceStepMeters / distanceToTarget).coerceIn(0.0, 1.0)
                        currentLat += (targetWp.lat - currentLat) * stepFraction
                        currentLon += (targetWp.lon - currentLon) * stepFraction
                        currentAlt += (targetWp.alt - currentAlt) * stepFraction
                    }

                    val simulatedLoc = Location("simulator").apply {
                        latitude = currentLat
                        longitude = currentLon
                        altitude = currentAlt
                        speed = speedMps.toFloat()
                        accuracy = (3..7).random().toFloat()
                        time = System.currentTimeMillis()
                    }
                    processNewLocation(simulatedLoc)
                }
            }
        }
    }

    // --- NOTIFICATION HELPERS ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Ride Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live metrics during an ongoing motorcycle ride"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val state = _trackingState.value
        val title = if (state.isPaused) "Ride Paused" else "Recording Ride..."
        val content = "${String.format("%.2f", state.totalDistanceKm)} km • ${state.formattedMovingTime} • ${state.currentSpeedKmh.toInt()} km/h"
        val notification = buildNotification(title, content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val state = _trackingState.value

        val pauseResumeAction = if (state.isPaused) {
            val resumeIntent = Intent(this, RideTrackingService::class.java).apply { action = ACTION_RESUME }
            val pendingResume = PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(android.R.drawable.ic_media_play, "Resume", pendingResume).build()
        } else {
            val pauseIntent = Intent(this, RideTrackingService::class.java).apply { action = ACTION_PAUSE }
            val pendingPause = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(android.R.drawable.ic_media_pause, "Pause", pendingPause).build()
        }

        val finishIntent = Intent(this, RideTrackingService::class.java).apply { action = ACTION_FINISH }
        val pendingFinish = PendingIntent.getService(this, 3, finishIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val finishAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_save, "Finish", pendingFinish).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .addAction(pauseResumeAction)
            .addAction(finishAction)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopLocationUpdates()
        simulationJob?.cancel()
        timerJob?.cancel()
        super.onDestroy()
    }
}
