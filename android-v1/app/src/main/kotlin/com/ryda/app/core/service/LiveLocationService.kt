package com.ryda.app.core.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.ryda.app.MainActivity
import com.ryda.app.core.auth.TokenDataStore
import com.ryda.app.core.network.ClubRideWebSocketClient
import com.ryda.app.core.network.LiveParticipantLocation
import com.ryda.app.core.network.WsConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class LiveSharingState(
    val isActive: Boolean = false,
    val sessionId: String? = null,
    val sessionTitle: String = "",
    val isSharingOwnLocation: Boolean = true,
    val connectionState: WsConnectionState = WsConnectionState.DISCONNECTED,
    val participantLocations: Map<Int, LiveParticipantLocation> = emptyMap(),
    val lastSessionEvent: String? = null
)

class LiveLocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wsClient: ClubRideWebSocketClient? = null
    private var collectorJob: Job? = null

    companion object {
        const val CHANNEL_ID = "ryda_live_location_channel"
        const val NOTIFICATION_ID = 1002

        const val ACTION_START = "ACTION_START_LIVE_LOCATION"
        const val ACTION_STOP = "ACTION_STOP_LIVE_LOCATION"
        const val ACTION_SET_SHARING = "ACTION_SET_SHARING"

        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val EXTRA_SESSION_TITLE = "EXTRA_SESSION_TITLE"
        const val EXTRA_IS_SHARING = "EXTRA_IS_SHARING"

        private val _liveState = MutableStateFlow(LiveSharingState())
        val liveState: StateFlow<LiveSharingState> = _liveState.asStateFlow()

        fun startSharing(context: Context, sessionId: String, sessionTitle: String) {
            val intent = Intent(context, LiveLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_SESSION_TITLE, sessionTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun setSharingVisibility(context: Context, isSharing: Boolean) {
            val intent = Intent(context, LiveLocationService::class.java).apply {
                action = ACTION_SET_SHARING
                putExtra(EXTRA_IS_SHARING, isSharing)
            }
            context.startService(intent)
        }

        fun stopSharing(context: Context) {
            val intent = Intent(context, LiveLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val state = _liveState.value
                if (state.isActive && state.isSharingOwnLocation) {
                    wsClient?.sendLocationUpdate(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        speedKmh = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0,
                        heading = if (loc.hasBearing()) loc.bearing else 0f,
                        isSharing = true
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                if (sessionId.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val title = intent.getStringExtra(EXTRA_SESSION_TITLE) ?: "Club Ride"
                startLiveSession(sessionId, title)
            }
            ACTION_SET_SHARING -> {
                val isSharing = intent.getBooleanExtra(EXTRA_IS_SHARING, true)
                _liveState.value = _liveState.value.copy(isSharingOwnLocation = isSharing)
                wsClient?.sendSharingVisibility(isSharing)
            }
            ACTION_STOP -> {
                stopLiveSession()
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLiveSession(sessionId: String, title: String) {
        if (_liveState.value.isActive && _liveState.value.sessionId == sessionId) return

        startForeground(NOTIFICATION_ID, buildNotification("Live Group Location", title))

        serviceScope.launch {
            val token = TokenDataStore.getInstance(applicationContext).tokenFlow.first()
            if (token.isNullOrBlank()) {
                stopLiveSession()
                return@launch
            }
            wsClient?.disconnect()
            val client = ClubRideWebSocketClient(
                sessionId = sessionId,
                authToken = token,
                onSessionEvent = { evt ->
                    if (evt == "session_completed" || evt == "session_cancelled") {
                        stopLiveSession()
                        _liveState.value = LiveSharingState(lastSessionEvent = evt)
                    }
                }
            )
            wsClient = client
            _liveState.value = LiveSharingState(
                isActive = true,
                sessionId = sessionId,
                sessionTitle = title,
                isSharingOwnLocation = true
            )
            client.connect()

            collectorJob?.cancel()
            collectorJob = serviceScope.launch {
                launch {
                    client.connectionState.collect { conn ->
                        _liveState.value = _liveState.value.copy(connectionState = conn)
                    }
                }
                launch {
                    client.participants.collect { map ->
                        _liveState.value = _liveState.value.copy(participantLocations = map)
                    }
                }
            }
        }

        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(4000L)
                .build()
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Permission denied or revoked: stop live sharing safely
            stopLiveSession()
        }
    }

    private fun stopLiveSession() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        collectorJob?.cancel()
        wsClient?.disconnect()
        wsClient = null
        _liveState.value = LiveSharingState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Club Ride Live Location",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopLiveSession()
        serviceScope.cancel()
        super.onDestroy()
    }
}
