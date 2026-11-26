package com.ryda.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.NavigationView
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.RoutingOptions
import com.google.android.libraries.navigation.Waypoint
import com.ryda.app.core.auth.TokenDataStore
import com.ryda.app.core.location.NavigationSoundMode
import com.ryda.app.core.location.PolylineDecoder
import com.ryda.app.core.location.RouteGuidanceCalculator
import com.ryda.app.core.models.ClubRideSession
import com.ryda.app.core.service.ClubRideShutdownCoordinator
import com.ryda.app.core.service.LiveLocationService
import com.ryda.app.core.service.RideTrackingService
import com.ryda.app.core.sync.SyncCoordinator
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class SosAlertData(
    val eventId: String,
    val userId: Int,
    val displayName: String,
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val timestampIso: String
)

@Composable
fun ClubRideActiveScreen(
    session: ClubRideSession,
    isClubLeader: Boolean = false,
    repository: RydaRepository? = null,
    tokenDataStore: TokenDataStore? = null,
    onBackClick: () -> Unit,
    onOpenRideHud: () -> Unit,
    onFinishMyRide: () -> Unit = {},
    onClubRideEnded: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val trackingState by RideTrackingService.trackingState.collectAsState()
    val liveState by LiveLocationService.liveState.collectAsState()

    val persistedSoundMode by tokenDataStore?.navSoundModeFlow?.collectAsState(initial = NavigationSoundMode.SOUND_ON)
        ?: remember { mutableStateOf(NavigationSoundMode.SOUND_ON) }

    var soundMode by remember { mutableStateOf(persistedSoundMode) }
    var lastNonMutedMode by remember { mutableStateOf(NavigationSoundMode.SOUND_ON) }

    LaunchedEffect(persistedSoundMode) {
        soundMode = persistedSoundMode
        if (persistedSoundMode != NavigationSoundMode.MUTED) {
            lastNonMutedMode = persistedSoundMode
        }
    }

    fun updateSoundMode(newMode: NavigationSoundMode) {
        soundMode = newMode
        if (newMode != NavigationSoundMode.MUTED) {
            lastNonMutedMode = newMode
        }
        scope.launch {
            tokenDataStore?.saveNavSoundMode(newMode)
        }
    }

    val navigationView = remember {
        NavigationView(context).apply {
            onCreate(null)
        }
    }

    var navigator by remember { mutableStateOf<Navigator?>(null) }
    var navMap by remember { mutableStateOf<GoogleMap?>(null) }

    suspend fun safelyCleanupNavigation() {
        withContext(Dispatchers.Main) {
            try {
                navigator?.stopGuidance()
            } catch (_: Exception) {}
            try {
                navigator?.cleanup()
            } catch (_: Exception) {}
            try {
                navigationView.onStop()
                navigationView.onPause()
                navigationView.onDestroy()
            } catch (_: Exception) {}
            navigator = null
            navMap = null
        }
    }

    LaunchedEffect(liveState.lastSessionEvent) {
        if (liveState.lastSessionEvent == "session_completed" || liveState.lastSessionEvent == "session_cancelled") {
            ClubRideShutdownCoordinator.handleSessionEnd(
                context = context,
                repository = repository,
                clubId = session.clubId,
                sessionId = session.id,
                isAdminEnding = false,
                onNavigationCleanup = { safelyCleanupNavigation() },
                onComplete = { onClubRideEnded() }
            )
        }
    }

    // Confirmation dialogs & ending state
    var showFinishRideDialog by remember { mutableStateOf(false) }
    var showEndClubRideDialog by remember { mutableStateOf(false) }
    var isEndingClubRide by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var showSosConfirmDialog by remember { mutableStateOf(false) }
    var activeSosAlert by remember { mutableStateOf<SosAlertData?>(null) }
    var sosToastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(liveState.lastSosAlert) {
        liveState.lastSosAlert?.let { sos ->
            activeSosAlert = SosAlertData(
                eventId = sos.eventId,
                userId = sos.userId,
                displayName = sos.displayName,
                lat = sos.lat,
                lng = sos.lng,
                accuracy = sos.accuracy,
                timestampIso = sos.timestampIso
            )
        }
    }

    LaunchedEffect(sosToastMessage) {
        if (sosToastMessage != null) {
            kotlinx.coroutines.delay(3000)
            sosToastMessage = null
        }
    }

    // Local marker visibility
    var showOtherRiders by remember { mutableStateOf(true) }
    var navError by remember { mutableStateOf<String?>(null) }
    var hasArrived by remember { mutableStateOf(false) }
    var isReroutingWarning by remember { mutableStateOf(false) }

    LaunchedEffect(navigator, soundMode) {
        navigator?.setAudioGuidance(soundMode.toAudioGuidance())
    }

    val participantMarkers = remember { mutableListOf<Marker>() }
    var returnGuidePolyline by remember { mutableStateOf<Polyline?>(null) }

    val currentLat = trackingState.currentLatitude
    val currentLng = trackingState.currentLongitude

    val fixedGuidance = remember(currentLat, currentLng, session.waypoints, session.encodedPolyline) {
        if (currentLat != null && currentLng != null) {
            RouteGuidanceCalculator.evaluate(
                currentLat = currentLat,
                currentLng = currentLng,
                waypoints = session.waypoints,
                encodedPolyline = session.encodedPolyline
            )
        } else {
            null
        }
    }

    DisposableEffect(lifecycleOwner, navigationView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> try { navigationView.onStart() } catch (_: Exception) {}
                Lifecycle.Event.ON_RESUME -> try { navigationView.onResume() } catch (_: Exception) {}
                Lifecycle.Event.ON_PAUSE -> try { navigationView.onPause() } catch (_: Exception) {}
                Lifecycle.Event.ON_STOP -> try { navigationView.onStop() } catch (_: Exception) {}
                Lifecycle.Event.ON_DESTROY -> try { navigationView.onDestroy() } catch (_: Exception) {}
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                navigator?.stopGuidance()
                navigator?.cleanup()
            } catch (_: Exception) {}
            try {
                navigationView.onDestroy()
            } catch (_: Exception) {}
            navigator = null
            navMap = null
        }
    }

    LaunchedEffect(session.id) {
        navigationView.getMapAsync { googleMap ->
            navMap = googleMap
            val plannedPoints = PolylineDecoder.decode(session.encodedPolyline)
            if (plannedPoints.size >= 2) {
                googleMap.addPolyline(
                    PolylineOptions()
                        .addAll(plannedPoints)
                        .color(android.graphics.Color.parseColor("#FFFF6B35"))
                        .width(14f)
                        .geodesic(true)
                )
            }
            session.waypoints.forEachIndexed { idx, wp ->
                googleMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(wp.lat, wp.lng))
                        .title("Pit Stop ${idx + 1}: ${wp.label}")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                )
            }
        }

        if (activity == null) {
            navError = "Navigation requires an Activity context."
            return@LaunchedEffect
        }

        NavigationApi.getNavigator(
            activity,
            object : NavigationApi.NavigatorListener {
                override fun onNavigatorReady(readyNav: Navigator) {
                    navigator = readyNav
                    readyNav.setAudioGuidance(soundMode.toAudioGuidance())

                    readyNav.addReroutingListener {
                        isReroutingWarning = true
                    }
                    readyNav.addArrivalListener {
                        hasArrived = true
                    }

                    val navWaypoints = buildList {
                        session.waypoints.forEach { wp ->
                            add(
                                Waypoint.builder()
                                    .setLatLng(wp.lat, wp.lng)
                                    .setTitle(wp.label.ifBlank { "Pit Stop" })
                                    .build()
                            )
                        }
                        if (session.destLat != null && session.destLng != null) {
                            add(
                                Waypoint.builder()
                                    .setLatLng(session.destLat, session.destLng)
                                    .setTitle(session.destination.ifBlank { "Destination" })
                                    .build()
                            )
                        }
                    }

                    if (navWaypoints.isNotEmpty()) {
                        val routingOptions = RoutingOptions().apply {
                            travelMode(RoutingOptions.TravelMode.TWO_WHEELER)
                        }
                        readyNav.setDestination(navWaypoints.first(), routingOptions)
                        readyNav.startGuidance()
                    }
                }

                override fun onError(errorCode: Int) {
                    navError = "Navigation error code: $errorCode"
                }
            }
        )
    }

    // Render other riders' live positions
    LaunchedEffect(liveState.participantLocations, navMap, showOtherRiders) {
        val map = navMap ?: return@LaunchedEffect
        participantMarkers.forEach { it.remove() }
        participantMarkers.clear()

        if (!showOtherRiders) return@LaunchedEffect

        val otherRiders = liveState.participantLocations.values
        otherRiders.forEach { p ->
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(p.lat, p.lng))
                    .title(p.username)
                    .snippet("Speed: ${p.speedKmh.toInt()} km/h")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            if (marker != null) {
                participantMarkers.add(marker)
            }
        }
    }

    // Draw return guide polyline when off route
    LaunchedEffect(fixedGuidance, currentLat, currentLng, navMap) {
        val map = navMap ?: return@LaunchedEffect
        returnGuidePolyline?.remove()
        returnGuidePolyline = null

        val guidance = fixedGuidance
        if (guidance != null && guidance.isOffRoute && currentLat != null && currentLng != null) {
            returnGuidePolyline = map.addPolyline(
                PolylineOptions()
                    .add(LatLng(currentLat, currentLng), LatLng(guidance.nearestPointLat, guidance.nearestPointLng))
                    .color(android.graphics.Color.parseColor("#FF3B30"))
                    .width(8f)
                    .pattern(listOf(Dash(30f), Gap(20f)))
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AsphaltBlack)) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CarbonDark)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackClick) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(text = session.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = "Destination: ${session.destination}", color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            }

            // Sound Toggle Button
            IconButton(onClick = {
                if (soundMode == NavigationSoundMode.MUTED) {
                    updateSoundMode(lastNonMutedMode)
                } else {
                    updateSoundMode(NavigationSoundMode.MUTED)
                }
            }) {
                Icon(
                    imageVector = when (soundMode) {
                        NavigationSoundMode.MUTED -> Icons.Default.VolumeOff
                        NavigationSoundMode.SOUND_ON -> Icons.Default.VolumeUp
                        NavigationSoundMode.ALERTS_ONLY -> Icons.Default.NotificationsActive
                    },
                    contentDescription = "Sound Mode",
                    tint = if (soundMode == NavigationSoundMode.MUTED) TextSecondary else MotoOrange
                )
            }
        }

        // Arrival Banner
        if (hasArrived) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ForestGreen.copy(alpha = 0.2f))
                    .border(0.5.dp, ForestGreen)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                Text("Destination Reached! Great ride.", color = ForestGreen, style = Typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        } else if (fixedGuidance?.isOffRoute == true || isReroutingWarning) {
            val distText = fixedGuidance?.let { "${it.distanceToRouteMeters.toInt()} m" } ?: ""
            val dirText = fixedGuidance?.let { "Head ${it.returnCardinal} to rejoin the fixed club route" }
                ?: "Return to the shared planned route"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CrimsonRed.copy(alpha = 0.2f))
                    .border(0.5.dp, CrimsonRed)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(18.dp))
                Column {
                    Text("OFF PLANNED CLUB ROUTE $distText", color = CrimsonRed, style = Typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(dirText, color = TextPrimary, style = Typography.bodySmall)
                }
            }
        }

        // Full-Screen Google Navigation SDK NavigationView + SOS Overlay Button
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { navigationView },
                modifier = Modifier.fillMaxSize()
            )

            // Prominent Circular Red SOS Button Overlay on Map
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(CrimsonRed)
                    .border(2.dp, Color.White, CircleShape)
                    .clickable { showSosConfirmDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = "SOS", tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("SOS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }

            if (sosToastMessage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CarbonDark)
                        .border(1.dp, CrimsonRed, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = sosToastMessage!!, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bottom Telemetry & Sharing Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CarbonDark)
                .border(0.5.dp, BorderSubtle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("DISTANCE", style = Typography.labelSmall, color = TextSecondary)
                    Text(
                        "${String.format("%.2f", trackingState.totalDistanceKm)} km",
                        style = Typography.titleMedium.copy(fontFamily = StatFontFamily),
                        color = TextPrimary
                    )
                }
                Column {
                    Text("SPEED", style = Typography.labelSmall, color = TextSecondary)
                    Text(
                        "${trackingState.currentSpeedKmh.toInt()} km/h",
                        style = Typography.titleMedium.copy(fontFamily = StatFontFamily),
                        color = MotoOrange
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("SHARE ME", style = Typography.labelSmall, color = TextSecondary)
                    Switch(
                        checked = liveState.isSharingOwnLocation,
                        onCheckedChange = { LiveLocationService.setSharingVisibility(context, it) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (session.myStatus == "VERIFIED" || trackingState.isTracking) {
                    Button(
                        onClick = { showFinishRideDialog = true },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MotoOrange, contentColor = AsphaltBlack)
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("FINISH MY RIDE", style = Typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                if (isClubLeader) {
                    Button(
                        onClick = { showEndClubRideDialog = true },
                        enabled = !isEndingClubRide,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed, contentColor = TextPrimary)
                    ) {
                        if (isEndingClubRide) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("END CLUB RIDE", style = Typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog: Finish My Ride
    if (showFinishRideDialog) {
        AlertDialog(
            onDismissRequest = { showFinishRideDialog = false },
            containerColor = CarbonDark,
            title = { Text("Finish Individual Ride?", style = Typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = { Text("This will complete and archive your solo ride recording. You will remain in the group ride session.") },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishRideDialog = false
                        onFinishMyRide()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MotoOrange, contentColor = AsphaltBlack)
                ) {
                    Text("Finish Ride", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishRideDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Confirmation Dialog: End Club Ride (Club Owner / Admin only)
    if (showEndClubRideDialog && isClubLeader) {
        AlertDialog(
            onDismissRequest = { if (!isEndingClubRide) showEndClubRideDialog = false },
            containerColor = CarbonDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("End Club Ride?", style = Typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will end the active Club Ride session for all participants and stop group location sharing. " +
                        "Participants' locally recorded rides will remain preserved on their devices."
                )
            },
            confirmButton = {
                Button(
                    enabled = !isEndingClubRide,
                    onClick = {
                        isEndingClubRide = true
                        actionError = null
                        ClubRideShutdownCoordinator.handleSessionEnd(
                            context = context,
                            repository = repository,
                            clubId = session.clubId,
                            sessionId = session.id,
                            isAdminEnding = true,
                            onNavigationCleanup = { safelyCleanupNavigation() },
                            onComplete = {
                                isEndingClubRide = false
                                showEndClubRideDialog = false
                                onClubRideEnded()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed, contentColor = TextPrimary)
                ) {
                    Text("End Club Ride", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isEndingClubRide,
                    onClick = { showEndClubRideDialog = false }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // SOS Confirmation Dialog
    if (showSosConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSosConfirmDialog = false },
            containerColor = CarbonDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TRIGGER EMERGENCY SOS?", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "This will immediately broadcast an urgent emergency alert with your current GPS coordinates to all active participants in this group ride.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSosConfirmDialog = false
                        val lat = currentLat ?: session.startLat ?: 0.0
                        val lng = currentLng ?: session.startLng ?: 0.0
                        LiveLocationService.sendSosAlert(context, lat, lng, trackingState.currentSpeedKmh.toFloat())
                        sosToastMessage = "🚨 SOS Alert Broadcasted to Group Ride!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed, contentColor = TextPrimary)
                ) {
                    Text("BROADCAST SOS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Received SOS Alert Dialog
    activeSosAlert?.let { sos ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = CarbonDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🚨 RIDER SOS EMERGENCY 🚨", color = CrimsonRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${sos.displayName} has raised an urgent SOS emergency alert during the group ride!",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Last Known Coordinates:\nLat: ${String.format(Locale.US, "%.5f", sos.lat)}, Lng: ${String.format(Locale.US, "%.5f", sos.lng)}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Timestamp: ${sos.timestampIso.take(19).replace('T', ' ')}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            navMap?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(sos.lat, sos.lng), 16f
                                )
                            )
                            activeSosAlert = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MotoOrange, contentColor = AsphaltBlack)
                    ) {
                        Text("View on Map", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { activeSosAlert = null },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = TextPrimary)
                    ) {
                        Text("Acknowledge", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {}
        )
    }
}
