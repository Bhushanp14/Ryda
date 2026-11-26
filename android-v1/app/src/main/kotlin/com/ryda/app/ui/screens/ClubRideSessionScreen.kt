package com.ryda.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.ryda.app.core.models.Club
import com.ryda.app.core.models.ClubRideSession
import com.ryda.app.core.service.LiveLocationService
import com.ryda.app.core.service.RideTrackingService
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ClubRideSessionScreen(
    club: Club,
    sessionId: String,
    repository: RydaRepository,
    onBackClick: () -> Unit,
    onOpenAdminRoster: (ClubRideSession) -> Unit,
    onOpenActiveRideHud: () -> Unit = {},
    onOpenGroupMap: (ClubRideSession) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trackingState by RideTrackingService.trackingState.collectAsState()
    val liveState by LiveLocationService.liveState.collectAsState()
    val motorcycles by repository.motorcycles.collectAsState()
    val primaryBikeId = remember(motorcycles) {
        (motorcycles.firstOrNull { it.isPrimary } ?: motorcycles.firstOrNull())?.id
    }
    var session by remember { mutableStateOf<ClubRideSession?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var autoNavOpened by remember { mutableStateOf(false) }

    fun maybeAutoStartIndividualRide(loaded: ClubRideSession) {
        if (loaded.status == "ACTIVE" && loaded.myStatus == "VERIFIED" && !loaded.myIndividualRideId.isNullOrBlank()) {
            val existingRide = repository.getRideByClubSessionId(loaded.id)
            if (existingRide == null && !trackingState.isTracking) {
                val fineGranted = ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (fineGranted) {
                    RideTrackingService.startRide(
                        context = context,
                        motorcycleId = primaryBikeId,
                        title = loaded.title,
                        isSimulation = false,
                        rideIdOverride = loaded.myIndividualRideId,
                        clubRideSessionId = loaded.id
                    )
                    LiveLocationService.startSharing(context, loaded.id, loaded.title)
                    infoMessage = "Individual club ride recording & live sharing started: ${loaded.title}"
                } else {
                    errorMessage = "Location permission is required to record your individual club ride."
                }
            }
            if (!autoNavOpened) {
                autoNavOpened = true
                onOpenGroupMap(loaded)
            }
        }
    }

    fun reload() {
        scope.launch {
            isLoading = true
            errorMessage = null
            repository.getClubRideDetail(club.id, sessionId)
                .onSuccess {
                    session = it
                    maybeAutoStartIndividualRide(it)
                    // ── Disconnected-device reconciliation ────────────────────────────────────────
                    // If this device missed the `session_completed` WebSocket event while offline,
                    // detect the terminal state from the backend and safely finalize the individual
                    // ride here. This path uses persistent backend state (ClubRideSession.status),
                    // not ephemeral WS events, so it is reliable across reconnections.
                    if (it.status == "COMPLETED" || it.status == "CANCELLED") {
                        val tracking = RideTrackingService.trackingState.value
                        val myRideId = it.myIndividualRideId
                        val isLinkedRide = myRideId == null || tracking.currentRideId == myRideId
                        if (tracking.isTracking && isLinkedRide) {
                            // Club session ended while this device was offline.
                            // Finalize the individual ride safely — no data is deleted.
                            RideTrackingService.finishRide(context)
                            LiveLocationService.stopSharing(context)
                            com.ryda.app.core.sync.SyncCoordinator.getInstance(context).triggerSync()
                            infoMessage = "Club Ride has ended. Your personal ride recording has been saved and will sync shortly."
                        }
                    }
                    // ─────────────────────────────────────────────────────────────────────────────
                }
                .onFailure { errorMessage = it.message ?: "Failed to load club ride." }
            isLoading = false
        }
    }

    LaunchedEffect(club.id, sessionId) {
        reload()
    }

    val isLeader = club.myRole == "LEADER"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Club Ride", style = Typography.titleLarge, color = TextPrimary)
                    Text(club.name, style = Typography.bodySmall, color = MotoOrange)
                }
            }
            IconButton(onClick = { reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
            }
        }

        HorizontalDivider(color = BorderSubtle)

        if (isLoading && session == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MotoOrange)
            }
            return@Column
        }

        val current = session
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(errorMessage ?: "Club ride unavailable.", color = CrimsonRed, style = Typography.bodyMedium)
                    Button(onClick = { reload() }, colors = ButtonDefaults.buttonColors(containerColor = MotoOrange)) {
                        Text("Retry", color = AsphaltBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CrimsonRed.copy(alpha = 0.15f))
                        .border(1.dp, CrimsonRed.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(errorMessage!!, color = CrimsonRed, style = Typography.bodySmall)
                }
            }
            if (infoMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ForestGreen.copy(alpha = 0.15f))
                        .border(1.dp, ForestGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(infoMessage!!, color = ForestGreen, style = Typography.bodySmall)
                }
            }

            // Session Header Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarbonDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(current.status, sessionStatusColor(current.status))
                    if (current.myStatus != null) {
                        StatusPill("YOU: ${current.myStatus}", participantStatusColor(current.myStatus))
                    }
                }

                Text(current.title, style = Typography.headlineMedium, color = TextPrimary)
                if (current.description.isNotBlank()) {
                    Text(current.description, style = Typography.bodySmall, color = TextSecondary)
                }

                HorizontalDivider(color = BorderSubtle.copy(alpha = 0.6f))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(16.dp))
                    Text("Start: ${current.meetingPoint}", style = Typography.bodySmall, color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Flag, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Text("Destination: ${current.destination}", style = Typography.bodySmall, color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = WarningGold, modifier = Modifier.size(16.dp))
                    Text("Scheduled: ${formatClubRideTime(current.scheduledStart)}", style = Typography.bodySmall, color = TextSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.TimerOff, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                    Text("Expires: ${formatClubRideTime(current.expiresAt)}", style = Typography.bodySmall, color = TextTertiary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
                    Text(
                        "${current.participantCount} / ${current.maxParticipants} Riders Joined",
                        style = Typography.bodySmall,
                        color = ForestGreen
                    )
                }
            }

            // Planned Route Preview (Saved Encoded Polyline + Ordered Pit Stops)
            val decodedRoute = remember(current.encodedPolyline) {
                com.ryda.app.core.location.PolylineDecoder.decode(current.encodedPolyline)
            }
            if (decodedRoute.size >= 2 || current.waypoints.isNotEmpty() || (current.startLat != null && current.startLng != null)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CarbonDark)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "PLANNED ROUTE (${current.waypoints.size} PIT STOPS)",
                        style = Typography.labelSmall,
                        color = TextSecondary
                    )
                    if (current.routeDistanceMeters > 0) {
                        Text(
                            "${com.ryda.app.core.location.PolylineDecoder.formatDistance(current.routeDistanceMeters)} • ${com.ryda.app.core.location.PolylineDecoder.formatDuration(current.routeDurationSeconds)}",
                            style = Typography.labelMedium.copy(fontFamily = StatFontFamily),
                            color = MotoOrange
                        )
                    }
                    val anchor = decodedRoute.firstOrNull()
                        ?: current.startLat?.let { LatLng(it, current.startLng ?: 0.0) }
                        ?: LatLng(current.waypoints.first().lat, current.waypoints.first().lng)
                    val cameraState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(anchor, 11f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraState) {
                            if (decodedRoute.size >= 2) {
                                Polyline(
                                    points = decodedRoute,
                                    color = MotoOrange,
                                    width = 10f,
                                    geodesic = true
                                )
                            }
                            if (current.startLat != null && current.startLng != null) {
                                Marker(
                                    state = MarkerState(position = LatLng(current.startLat, current.startLng)),
                                    title = "Start: ${current.meetingPoint}",
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                                )
                            }
                            if (current.destLat != null && current.destLng != null) {
                                Marker(
                                    state = MarkerState(position = LatLng(current.destLat, current.destLng)),
                                    title = "Destination: ${current.destination}",
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                                )
                            }
                            current.waypoints.forEachIndexed { idx, wp ->
                                Marker(
                                    state = MarkerState(position = LatLng(wp.lat, wp.lng)),
                                    title = "Pit Stop ${idx + 1}: ${wp.label}",
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                                )
                            }
                        }
                    }
                    current.waypoints.forEachIndexed { idx, wp ->
                        Text("Pit Stop ${idx + 1}: ${wp.label}", style = Typography.bodySmall, color = TextPrimary)
                    }
                }
            }

            // Live Location Sharing Card (Phase 5)
            if (current.status == "ACTIVE" && current.myStatus == "VERIFIED") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CarbonDark)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("LIVE GROUP LOCATION", style = Typography.labelSmall, color = ElectricCyan)
                        StatusPill(liveState.connectionState.name, ElectricCyan)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Share My Location", style = Typography.bodyMedium, color = TextPrimary)
                            Text(
                                "${liveState.participantLocations.size} active rider(s) sharing",
                                style = Typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = liveState.isSharingOwnLocation,
                            onCheckedChange = { checked ->
                                LiveLocationService.setSharingVisibility(context, checked)
                            }
                        )
                    }
                    Button(
                        onClick = { onOpenGroupMap(current) },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = AsphaltBlack)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OPEN GROUP MAP & ROUTE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Leader Management Card
            if (isLeader) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CarbonDark)
                        .border(1.dp, MotoOrange.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("CLUB LEADER CONTROLS", style = Typography.labelSmall, color = MotoOrange)
                    Button(
                        onClick = { onOpenAdminRoster(current) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = TextPrimary)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Attendance Verification & Roster", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Bottom Member Action Bar
        if (current.status == "SCHEDULED" && club.isMember) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AsphaltBlack)
                    .border(0.5.dp, BorderSubtle)
                    .padding(16.dp)
            ) {
                when (current.myStatus) {
                    null -> {
                        Button(
                            onClick = {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    repository.joinClubRide(club.id, current.id)
                                        .onSuccess {
                                            infoMessage = "You joined this club ride."
                                            reload()
                                        }
                                        .onFailure { errorMessage = it.message }
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MotoOrange, contentColor = AsphaltBlack)
                        ) {
                            Text("JOIN CLUB RIDE", style = Typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                    "JOINED" -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    isBusy = true
                                    errorMessage = null
                                    scope.launch {
                                        repository.leaveClubRide(club.id, current.id)
                                            .onSuccess {
                                                infoMessage = "You left this club ride."
                                                reload()
                                            }
                                            .onFailure { errorMessage = it.message }
                                        isBusy = false
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Leave", color = CrimsonRed, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    isBusy = true
                                    errorMessage = null
                                    scope.launch {
                                        repository.markClubRideReady(club.id, current.id)
                                            .onSuccess {
                                                infoMessage = "Marked as Ready! Present yourself to the Club Leader for verification."
                                                reload()
                                            }
                                            .onFailure { errorMessage = it.message }
                                        isBusy = false
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier.weight(2f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = HighVisLime, contentColor = AsphaltBlack)
                            ) {
                                Text("I'M READY", style = Typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    "READY" -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    isBusy = true
                                    errorMessage = null
                                    scope.launch {
                                        repository.leaveClubRide(club.id, current.id)
                                            .onSuccess {
                                                infoMessage = "You left this club ride."
                                                reload()
                                            }
                                            .onFailure { errorMessage = it.message }
                                        isBusy = false
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Leave", color = CrimsonRed, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(2f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(WarningGold.copy(alpha = 0.2f))
                                    .border(1.dp, WarningGold, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("READY • AWAITING VERIFICATION", color = WarningGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    "VERIFIED" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ForestGreen.copy(alpha = 0.2f))
                                .border(1.dp, ForestGreen, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("VERIFIED FOR STARTING ROSTER ✓", color = ForestGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Individual Recording Banner (for VERIFIED participants when ACTIVE)
        if (current.status == "ACTIVE" && current.myStatus == "VERIFIED") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AsphaltBlack)
                    .border(0.5.dp, BorderSubtle)
                    .padding(16.dp)
            ) {
                val isRecordingThisSession = trackingState.isTracking &&
                    trackingState.currentRideId == current.myIndividualRideId
                Button(
                    onClick = {
                        if (isRecordingThisSession) {
                            onOpenActiveRideHud()
                        } else {
                            maybeAutoStartIndividualRide(current)
                            if (RideTrackingService.trackingState.value.isTracking) {
                                onOpenActiveRideHud()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HighVisLime, contentColor = AsphaltBlack)
                ) {
                    Icon(Icons.Default.SportsMotorsports, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isRecordingThisSession) "OPEN ACTIVE RIDE HUD" else "START INDIVIDUAL RIDE RECORDING",
                        style = Typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
