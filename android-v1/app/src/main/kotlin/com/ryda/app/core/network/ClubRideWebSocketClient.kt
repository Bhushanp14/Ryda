package com.ryda.app.core.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

enum class WsConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class LiveParticipantLocation(
    val userId: Int,
    val username: String,
    val lat: Double,
    val lng: Double,
    val speedKmh: Double,
    val heading: Float,
    val timestampIso: String,
    val receivedAtMs: Long = System.currentTimeMillis()
) {
    fun isStale(nowMs: Long = System.currentTimeMillis(), thresholdMs: Long = 15_000L): Boolean =
        (nowMs - receivedAtMs) > thresholdMs
}

class ClubRideWebSocketClient(
    private val sessionId: String,
    private val authToken: String,
    private val onSessionEvent: (String) -> Unit = {}
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var intentionallyClosed = false
    private var reconnectAttempt = 0

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private val _participants = MutableStateFlow<Map<Int, LiveParticipantLocation>>(emptyMap())
    val participants: StateFlow<Map<Int, LiveParticipantLocation>> = _participants.asStateFlow()

    fun connect() {
        intentionallyClosed = false
        reconnectJob?.cancel()
        openSocket()
    }

    private fun openSocket() {
        if (intentionallyClosed) return
        _connectionState.value = if (reconnectAttempt == 0) {
            WsConnectionState.CONNECTING
        } else {
            WsConnectionState.RECONNECTING
        }

        val origin = RetrofitBuilder.getServerOrigin()
        val wsOrigin = when {
            origin.startsWith("https://") -> origin.replaceFirst("https://", "wss://")
            origin.startsWith("http://") -> origin.replaceFirst("http://", "ws://")
            else -> "ws://$origin"
        }
        val url = "$wsOrigin/ws/club-rides/$sessionId/location/?token=$authToken"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                _connectionState.value = WsConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!intentionallyClosed && code !in 4000..4099) {
                    scheduleReconnect()
                } else {
                    _connectionState.value = WsConnectionState.DISCONNECTED
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!intentionallyClosed) {
                    scheduleReconnect()
                } else {
                    _connectionState.value = WsConnectionState.DISCONNECTED
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (intentionallyClosed) return
        _connectionState.value = WsConnectionState.RECONNECTING
        reconnectAttempt++
        val delayMs = (2000L * (1 shl minOf(reconnectAttempt - 1, 3))).coerceAtMost(15_000L)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!intentionallyClosed) openSocket()
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "location_snapshot" -> {
                    val arr = json.optJSONArray("participants") ?: return
                    val updated = _participants.value.toMutableMap()
                    val now = System.currentTimeMillis()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val uid = item.optInt("user_id", -1)
                        if (uid != -1) {
                            updated[uid] = LiveParticipantLocation(
                                userId = uid,
                                username = item.optString("username"),
                                lat = item.optDouble("lat"),
                                lng = item.optDouble("lng"),
                                speedKmh = item.optDouble("speed_kmh", 0.0),
                                heading = item.optDouble("heading", 0.0).toFloat(),
                                timestampIso = item.optString("timestamp"),
                                receivedAtMs = now
                            )
                        }
                    }
                    _participants.value = updated
                }
                "participant_location" -> {
                    val uid = json.optInt("user_id", -1)
                    if (uid != -1) {
                        val updated = _participants.value.toMutableMap()
                        updated[uid] = LiveParticipantLocation(
                            userId = uid,
                            username = json.optString("username"),
                            lat = json.optDouble("lat"),
                            lng = json.optDouble("lng"),
                            speedKmh = json.optDouble("speed_kmh", 0.0),
                            heading = json.optDouble("heading", 0.0).toFloat(),
                            timestampIso = json.optString("timestamp"),
                            receivedAtMs = System.currentTimeMillis()
                        )
                        _participants.value = updated
                    }
                }
                "participant_sharing_paused" -> {
                    val uid = json.optInt("user_id", -1)
                    if (uid != -1) {
                        _participants.value = _participants.value - uid
                    }
                }
                "session_started", "session_completed", "session_cancelled" -> {
                    onSessionEvent(json.optString("type"))
                }
            }
        } catch (_: Exception) {}
    }

    fun sendLocationUpdate(
        lat: Double,
        lng: Double,
        speedKmh: Double,
        heading: Float,
        isSharing: Boolean = true
    ) {
        val payload = JSONObject().apply {
            put("type", "location_update")
            put("lat", lat)
            put("lng", lng)
            put("speed_kmh", speedKmh)
            put("heading", heading.toDouble())
            put("is_sharing", isSharing)
            put("timestamp", Instant.now().toString())
        }.toString()
        webSocket?.send(payload)
    }

    fun sendSharingVisibility(isSharing: Boolean) {
        val payload = JSONObject().apply {
            put("type", "sharing_visibility")
            put("is_sharing", isSharing)
        }.toString()
        webSocket?.send(payload)
    }

    fun disconnect() {
        intentionallyClosed = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client closed")
        webSocket = null
        _connectionState.value = WsConnectionState.DISCONNECTED
        scope.cancel()
    }
}
