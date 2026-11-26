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

    // Persisted navigation sound preference (defaults to SOUND_ON)
    val persistedSoundMode by (
        tokenDataStore?.navSoundModeFlow?.collectAsState(initial = NavigationSoundMode.SOUND_ON)
            ?: remember { mutableStateOf(NavigationSoundMode.SOUND_ON) }
        )
    var soundMode by remember(persistedSoundMode) { mutableStateOf(persistedSoundMode) }
    var lastNonMutedMode by remember { mutableStateOf(NavigationSoundMode.SOUND_ON) }
    var showSoundMenu by remember { mutableStateOf(false) }

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

    // ── Auto-finalize individual ride when group session ends (connected devices) ──────────────
    // Triggered by the WebSocket `session_completed` event received by LiveLocationService.
    // Guard: only finalize if this device is actively tracking a ride linked to this session.
    // LiveLocationService.stopLiveSession() is already called by LiveLocationService itself when
    // `session_completed` is received; we only need to finalize the individual RideTrackingService.
    LaunchedEffect(liveState.lastSessionEvent) {
        if (liveState.lastSessionEvent == "session_completed") {
            val tracking = RideTrackingService.trackingState.value
            val myRideId = session.myIndividualRideId
            // Finalize only if actively tracking and the ride is linked to this club session
            val isLinkedRide = myRideId == null || tracking.currentRideId == myRideId
            if (tracking.isTracking && isLinkedRide) {
                RideTrackingService.finishRide(context)
                SyncCoordinator.getInstance(context).triggerSync()
            }
            // Note: group live-location sharing is already stopped by LiveLocationService.
            // Do NOT call LiveLocationService.stopSharing() here to avoid double-stop.
        }
    }

    // Confirmation dialogs & ending state
    var showFinishRideDialog by remember { mutableStateOf(false) }
    var showEndClubRideDialog by remember { mutableStateOf(false) }
    var isEndingClubRide by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    // Local marker visibility: hides/shows OTHER riders' markers without disabling own sharing.
    var showOtherRiders by remember { mutableStateOf(true) }

    var navigator by remember { mutableStateOf<Navigator?>(null) }
    var navMap by remember { mutableStateOf<GoogleMap?>(null) }
    var navError by remember { mutableStateOf<String?>(null) }
    var hasArrived by remember { mutableStateOf(false) }
    var isReroutingWarning by remember { mutableStateOf(false) }

    // Apply sound mode changes directly to Navigation SDK without interrupting navigation/GPS/sharing
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

    val navigationView = remember {
        NavigationView(context).apply {
            onCreate(null)
        }
    }

    // Forward Android lifecycle events to NavigationView
    DisposableEffect(lifecycleOwner, navigationView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> navigationView.onStart()
                Lifecycle.Event.ON_RESUME -> navigationView.onResume()
                Lifecycle.Event.ON_PAUSE -> navigationView.onPause()
                Lifecycle.Event.ON_STOP -> navigationView.onStop()
                Lifecycle.Event.ON_DESTROY -> navigationView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            navigator?.stopGuidance()
            navigator?.cleanup()
            navigationView.onDestroy()
        }
    }

    // Initialize Navigation SDK Navigator & draw fixed shared route on NavigationView's GoogleMap
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
                        // Flag off-route deviation; never overwrite the shared ClubRideSession route
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
                        val pending = readyNav.setDestinations(navWaypoints, routingOptions)
                        pending.setOnResultListener { status ->
                            if (status == Navigator.RouteStatus.OK) {
                                readyNav.startGuidance()
                            } else {
                                navError = "Navigation route status: $status"
                            }
                        }
                    } else {
                        navError = "This Club Ride has no destination coordinates."
                    }
                }

                override fun onError(@NavigationApi.ErrorCode errorCode: Int) {
                    navError = when (errorCode) {
                        NavigationApi.ErrorCode.NOT_AUTHORIZED ->
                            "Navigation SDK not authorized for this API key."
                        NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED ->
                            "Google Navigation Terms must be accepted."
                        else ->
                            "Navigation SDK initialization failed (code $errorCode)."
                    }
                }
            }
        )
    }

    // Overlay live verified participant markers + return-to-fixed-route guidance line
    LaunchedEffect(navMap, liveState.participantLocations, showOtherRiders, fixedGuidance) {
        val map = navMap ?: return@LaunchedEffect

        // Clear and re-render participant markers
        participantMarkers.forEach { it.remove() }
        participantMarkers.clear()

        if (showOtherRiders) {
            val nowMs = System.currentTimeMillis()
            liveState.participantLocations.values.forEach { p ->
                val isStale = p.isStale(nowMs)
                val ageSec = ((nowMs - p.receivedAtMs) / 1000L).coerceAtLeast(0L)
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(p.lat, p.lng))
                        .title(p.username)
                        .snippet(if (isStale) "Stale • ${ageSec}s ago" else "${p.speedKmh.toInt()} km/h")
                        .alpha(if (isStale) 0.55f else 1.0f)
                        .icon(
                            BitmapDescriptorFactory.defaultMarker(
                                if (isStale) BitmapDescriptorFactory.HUE_YELLOW
                                else BitmapDescriptorFactory.HUE_AZURE
                            )
                        )
                )
                if (marker != null) participantMarkers.add(marker)
            }
        }

        // Dashed return-to-fixed-route polyline when deviated
        returnGuidePolyline?.remove()
        returnGuidePolyline = null
        if (fixedGuidance != null && fixedGuidance.isOffRoute && currentLat != null && currentLng != null) {
            returnGuidePolyline = map.addPolyline(
                PolylineOptions()
                    .add(
                        LatLng(currentLat, currentLng),
                        LatLng(fixedGuidance.nearestPointLat, fixedGuidance.nearestPointLng)
                    )
                    .color(android.graphics.Color.parseColor("#FF00E5FF"))
                    .width(9f)
                    .pattern(listOf(Dash(20f), Gap(12f)))
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CarbonDark)
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(session.title, style = Typography.titleMedium, color = TextPrimary, maxLines = 1)
                    Text(
                        "${liveState.connectionState.name} • ${liveState.participantLocations.size} sharing",
                        style = Typography.labelSmall,
                        color = ElectricCyan
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quick Mute/Unmute Speaker Icon + 3-Mode Sound Selector
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(CarbonDark)
                            .border(
                                1.dp,
                                if (soundMode == NavigationSoundMode.MUTED) BorderSubtle else MotoOrange.copy(alpha = 0.6f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when (soundMode) {
                                NavigationSoundMode.SOUND_ON -> Icons.Default.VolumeUp
                                NavigationSoundMode.ALERTS_ONLY -> Icons.Default.NotificationsActive
                                NavigationSoundMode.MUTED -> Icons.Default.VolumeOff
                            },
                            contentDescription = "Toggle Navigation Mute",
                            tint = when (soundMode) {
                                NavigationSoundMode.SOUND_ON -> MotoOrange
                                NavigationSoundMode.ALERTS_ONLY -> WarningGold
                                NavigationSoundMode.MUTED -> TextSecondary
                            },
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    if (soundMode == NavigationSoundMode.MUTED) {
                                        updateSoundMode(lastNonMutedMode)
                                    } else {
                                        updateSoundMode(NavigationSoundMode.MUTED)
                                    }
                                }
                        )
                        Row(
                            modifier = Modifier.clickable { showSoundMenu = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = soundMode.shortLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Sound Mode",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSoundMenu,
                        onDismissRequest = { showSoundMenu = false },
                        containerColor = CarbonDark
                    ) {
                        NavigationSoundMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (mode) {
                                                NavigationSoundMode.SOUND_ON -> Icons.Default.VolumeUp
                                                NavigationSoundMode.ALERTS_ONLY -> Icons.Default.NotificationsActive
                                                NavigationSoundMode.MUTED -> Icons.Default.VolumeOff
                                            },
                                            contentDescription = null,
                                            tint = if (soundMode == mode) MotoOrange else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = mode.label,
                                            color = if (soundMode == mode) MotoOrange else TextPrimary,
                                            fontWeight = if (soundMode == mode) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (soundMode == mode) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    updateSoundMode(mode)
                                    showSoundMenu = false
                                }
                            )
                        }
                    }
                }

                FilterChip(
                    selected = showOtherRiders,
                    onClick = { showOtherRiders = !showOtherRiders },
                    label = {
                        Text(
                            if (showOtherRiders) "Riders On" else "Riders Hidden",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (showOtherRiders) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        // Session Ended by Leader Notification Banner
        if (liveState.lastSessionEvent == "session_completed") {
            val rideFinalized = !RideTrackingService.trackingState.value.isTracking
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElectricCyan.copy(alpha = 0.16f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    if (rideFinalized)
                        "Club Ride ended by leader. Your personal ride has been saved and will sync shortly. ✓"
                    else
                        "Club Ride ended by leader. Group sharing stopped; your personal ride recording is preserved.",
                    color = ElectricCyan,
                    style = Typography.bodySmall
                )
            }
        }

        // Action Error Banner
        if (actionError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CrimsonRed.copy(alpha = 0.2f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(actionError!!, color = CrimsonRed, style = Typography.bodySmall)
            }
        }

        // Navigation Error Banner
        if (navError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarningGold.copy(alpha = 0.2f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(navError!!, color = WarningGold, style = Typography.bodySmall)
            }
        }

        // Fixed-Route Deviation / Arrival Banner
        if (hasArrived) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ForestGreen.copy(alpha = 0.2f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Arrived at destination: ${session.destination}", color = ForestGreen, style = Typography.labelMedium)
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

        // Full-Screen Google Navigation SDK NavigationView
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { navigationView },
                modifier = Modifier.fillMaxSize()
            )
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

            OutlinedButton(
                onClick = onOpenRideHud,
                modifier = Modifier.fillMaxWidth().height(38.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("OPEN RECORDING HUD", style = Typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Confirmation Dialog: Finish My Ride (individual participant only)
    if (showFinishRideDialog) {
        AlertDialog(
            onDismissRequest = { showFinishRideDialog = false },
            containerColor = CarbonDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Finish My Ride?", style = Typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will stop and save your personal ride recording and sync it normally. " +
                        "The Club Ride session will remain active for other participants."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishRideDialog = false
                        onFinishMyRide()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MotoOrange, contentColor = AsphaltBlack)
                ) {
                    Text("Finish My Ride", fontWeight = FontWeight.Bold)
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
                        val repo = repository
                        if (repo == null) {
                            showEndClubRideDialog = false
                            onClubRideEnded()
                            return@Button
                        }
                        isEndingClubRide = true
                        actionError = null
                        scope.launch {
                            repo.completeClubRide(session.clubId, session.id)
                                .onSuccess {
                                    isEndingClubRide = false
                                    showEndClubRideDialog = false
                                    onClubRideEnded()
                                }
                                .onFailure { err ->
                                    isEndingClubRide = false
                                    showEndClubRideDialog = false
                                    actionError = err.message ?: "Failed to end Club Ride."
                                }
                        }
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
}
