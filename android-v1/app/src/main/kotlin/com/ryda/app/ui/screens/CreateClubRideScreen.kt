package com.ryda.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.ryda.app.core.location.*
import com.ryda.app.core.models.*
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

@Composable
fun CreateClubRideScreen(
    club: Club,
    repository: RydaRepository,
    onBackClick: () -> Unit,
    onCreated: (ClubRideSession) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var maxParticipantsText by remember { mutableStateOf("25") }

    var planner by remember { mutableStateOf(RoutePlannerState()) }
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // Default scheduled start: 2 hours from now
    val defaultCal = remember {
        Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 2) }
    }
    var selectedYear by remember { mutableStateOf(defaultCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(defaultCal.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableStateOf(defaultCal.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableStateOf(defaultCal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(defaultCal.get(Calendar.MINUTE)) }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scheduledZdt = remember(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute) {
        ZonedDateTime.of(
            selectedYear, selectedMonth + 1, selectedDay,
            selectedHour, selectedMinute, 0, 0, ZoneId.systemDefault()
        )
    }
    val scheduledDisplay = remember(scheduledZdt) {
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm").format(scheduledZdt)
    }

    val mapCameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(19.0760, 72.8777), 10f)
    }

    fun recalculateRoutes(targetState: RoutePlannerState) {
        val origin = targetState.start
        val dest = targetState.destination
        if (origin == null || dest == null) return

        planner = targetState.copy(isComputingRoute = true, routeError = null)
        scope.launch {
            val result = repository.computeClubRideRoutes(
                ComputeRouteRequest(
                    clubId = club.id,
                    origin = RouteLatLng(origin.lat, origin.lng, origin.name),
                    destination = RouteLatLng(dest.lat, dest.lng, dest.name),
                    intermediates = targetState.pitStops.map { RouteLatLng(it.lat, it.lng, it.label) },
                    computeAlternatives = targetState.pitStops.isEmpty(),
                    travelMode = "TWO_WHEELER"
                )
            )
            result.onSuccess { resp ->
                if (resp.routes.isEmpty()) {
                    planner = planner.copy(
                        isComputingRoute = false,
                        alternatives = emptyList(),
                        routeError = "No supported motorcycle route is available for the selected points."
                    )
                } else {
                    planner = planner.copy(
                        isComputingRoute = false,
                        alternatives = resp.routes,
                        selectedRouteIndex = 0,
                        routeError = null
                    )
                }
            }.onFailure { err ->
                planner = planner.copy(
                    isComputingRoute = false,
                    alternatives = emptyList(),
                    routeError = err.message ?: "Failed to compute routes."
                )
            }
        }
    }

    // Debounced Google Places Autocomplete search
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length < 2) {
            suggestions = emptyList()
            searchError = null
            return@LaunchedEffect
        }
        delay(350)
        PlacesSearchHelper.searchPlaces(context, searchQuery.trim())
            .onSuccess {
                suggestions = it
                searchError = null
            }
            .onFailure {
                suggestions = emptyList()
                searchError = it.message
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                Text("Schedule Club Ride", style = Typography.titleLarge, color = TextPrimary)
                Text(club.name, style = Typography.bodySmall, color = MotoOrange)
            }
        }

        HorizontalDivider(color = BorderSubtle)

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
                        .clip(RoundedCornerShape(12.dp))
                        .background(CrimsonRed.copy(alpha = 0.15f))
                        .border(1.dp, CrimsonRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(errorMessage!!, color = CrimsonRed, style = Typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Ride Title *") },
                placeholder = { Text("e.g. Sunday Lonavala Ghat Run") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MotoOrange,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = MotoOrange,
                    unfocusedLabelColor = TextSecondary
                )
            )

            // ── Unified Interactive Route Planner ─────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarbonDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("INTERACTIVE ROUTE PLANNER", style = Typography.labelSmall, color = MotoOrange)

                // Selection Mode Tabs: Start / Destination / Pit Stop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        PlannerSelectionMode.START to "Set Start",
                        PlannerSelectionMode.DESTINATION to "Set Dest",
                        PlannerSelectionMode.PIT_STOP to "+ Pit Stop"
                    ).forEach { (mode, label) ->
                        val active = planner.selectionMode == mode
                        FilterChip(
                            selected = active,
                            onClick = { planner = planner.copy(selectionMode = mode) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Places Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ElectricCyan) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                suggestions = emptyList()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            when (planner.selectionMode) {
                                PlannerSelectionMode.START -> "Search starting point or tap map..."
                                PlannerSelectionMode.DESTINATION -> "Search destination or tap map..."
                                PlannerSelectionMode.PIT_STOP -> "Search pit stop or tap map..."
                            },
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                if (searchError != null) {
                    Text(searchError!!, color = WarningGold, fontSize = 11.sp)
                }

                // Autocomplete Suggestions
                suggestions.take(4).forEach { sug ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .clickable {
                                scope.launch {
                                    PlacesSearchHelper.fetchPlaceCoordinates(context, sug.placeId, sug.primaryText)
                                        .onSuccess { resolved ->
                                            searchQuery = ""
                                            suggestions = emptyList()
                                            mapCameraState.animate(
                                                CameraUpdateFactory.newLatLngZoom(LatLng(resolved.lat, resolved.lng), 12f)
                                            )
                                            val updated = when (planner.selectionMode) {
                                                PlannerSelectionMode.START -> planner.copy(
                                                    start = resolved,
                                                    selectionMode = if (planner.destination == null) PlannerSelectionMode.DESTINATION else PlannerSelectionMode.START
                                                )
                                                PlannerSelectionMode.DESTINATION -> planner.copy(destination = resolved)
                                                PlannerSelectionMode.PIT_STOP -> planner.withPitStopAdded(
                                                    ClubRideWaypoint(resolved.lat, resolved.lng, resolved.name)
                                                )
                                            }
                                            planner = updated
                                            recalculateRoutes(updated)
                                        }
                                        .onFailure { err ->
                                            searchError = err.message
                                        }
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(sug.primaryText, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (sug.secondaryText.isNotBlank()) {
                                Text(sug.secondaryText, color = TextSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Interactive Map
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = mapCameraState,
                        onMapClick = { latLng ->
                            val label = "${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
                            val updated = when (planner.selectionMode) {
                                PlannerSelectionMode.START -> planner.copy(
                                    start = ResolvedPlace("Start ($label)", latLng.latitude, latLng.longitude),
                                    selectionMode = if (planner.destination == null) PlannerSelectionMode.DESTINATION else PlannerSelectionMode.START
                                )
                                PlannerSelectionMode.DESTINATION -> planner.copy(
                                    destination = ResolvedPlace("Dest ($label)", latLng.latitude, latLng.longitude)
                                )
                                PlannerSelectionMode.PIT_STOP -> planner.withPitStopAdded(
                                    ClubRideWaypoint(latLng.latitude, latLng.longitude, "Pit Stop ${planner.pitStops.size + 1}")
                                )
                            }
                            planner = updated
                            recalculateRoutes(updated)
                        }
                    ) {
                        // Unselected alternative polylines (muted)
                        planner.alternatives.forEachIndexed { idx, alt ->
                            if (idx != planner.selectedRouteIndex) {
                                val pts = PolylineDecoder.decode(alt.encodedPolyline)
                                if (pts.size >= 2) {
                                    Polyline(
                                        points = pts,
                                        color = TextTertiary.copy(alpha = 0.65f),
                                        width = 8f,
                                        clickable = true,
                                        onClick = { planner = planner.withSelectedRoute(idx) }
                                    )
                                }
                            }
                        }

                        // Selected route polyline (highlighted MotoOrange)
                        planner.selectedRoute?.let { sel ->
                            val pts = PolylineDecoder.decode(sel.encodedPolyline)
                            if (pts.size >= 2) {
                                Polyline(
                                    points = pts,
                                    color = MotoOrange,
                                    width = 13f,
                                    geodesic = true
                                )
                            }
                        }

                        planner.start?.let { s ->
                            Marker(
                                state = MarkerState(position = LatLng(s.lat, s.lng)),
                                title = "Start: ${s.name}",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                            )
                        }
                        planner.destination?.let { d ->
                            Marker(
                                state = MarkerState(position = LatLng(d.lat, d.lng)),
                                title = "Destination: ${d.name}",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                            )
                        }
                        planner.pitStops.forEachIndexed { idx, wp ->
                            Marker(
                                state = MarkerState(position = LatLng(wp.lat, wp.lng)),
                                title = "Pit Stop ${idx + 1}: ${wp.label}",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                            )
                        }
                    }
                }

                // Selected Start & Destination Readout
                Text(
                    "Start: ${planner.start?.name ?: "Tap map or search"}",
                    style = Typography.bodySmall,
                    color = if (planner.start != null) ForestGreen else TextSecondary
                )
                Text(
                    "Destination: ${planner.destination?.name ?: "Tap map or search"}",
                    style = Typography.bodySmall,
                    color = if (planner.destination != null) ElectricCyan else TextSecondary
                )

                // Route Alternatives Selector
                if (planner.isComputingRoute) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = MotoOrange, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Calculating routes via Google Routes API...", style = Typography.bodySmall, color = TextSecondary)
                    }
                }
                if (planner.routeError != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CrimsonRed.copy(alpha = 0.15f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(planner.routeError!!, color = CrimsonRed, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { recalculateRoutes(planner) }) {
                            Text("Retry", color = MotoOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (planner.alternatives.isNotEmpty()) {
                    Text("ROUTE ALTERNATIVES (${planner.alternatives.size})", style = Typography.labelSmall, color = TextSecondary)
                    planner.alternatives.forEachIndexed { idx, routeOpt ->
                        val isSelected = idx == planner.selectedRouteIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MotoOrange.copy(alpha = 0.18f) else SurfaceCard)
                                .border(
                                    1.dp,
                                    if (isSelected) MotoOrange else BorderSubtle,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { planner = planner.withSelectedRoute(idx) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    routeOpt.summary.ifBlank { "Route ${idx + 1}" },
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${PolylineDecoder.formatDistance(routeOpt.distanceMeters)} • ${PolylineDecoder.formatDuration(routeOpt.durationSeconds)}",
                                    color = if (isSelected) MotoOrange else ElectricCyan,
                                    fontSize = 11.sp,
                                    fontFamily = StatFontFamily
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MotoOrange)
                            }
                        }
                    }
                    Text(
                        text = "Note: Two-wheeler (motorcycle) routes are in beta and may not always provide optimal motorcycle paths.",
                        color = WarningGold,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                // Ordered Pit Stops Management (Add/Remove/Reorder)
                if (planner.pitStops.isNotEmpty()) {
                    HorizontalDivider(color = BorderSubtle)
                    Text("ORDERED PIT STOPS (${planner.pitStops.size})", style = Typography.labelSmall, color = TextSecondary)
                    planner.pitStops.forEachIndexed { idx, wp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceCard)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${idx + 1}. ${wp.label}", color = TextPrimary, style = Typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${String.format("%.4f", wp.lat)}, ${String.format("%.4f", wp.lng)}",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        val updated = planner.withPitStopMoved(idx, idx - 1)
                                        planner = updated
                                        recalculateRoutes(updated)
                                    },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = if (idx > 0) ElectricCyan else TextTertiary)
                                }
                                IconButton(
                                    onClick = {
                                        val updated = planner.withPitStopMoved(idx, idx + 1)
                                        planner = updated
                                        recalculateRoutes(updated)
                                    },
                                    enabled = idx < planner.pitStops.lastIndex,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = if (idx < planner.pitStops.lastIndex) ElectricCyan else TextTertiary)
                                }
                                IconButton(
                                    onClick = {
                                        val updated = planner.withPitStopRemoved(idx)
                                        planner = updated
                                        recalculateRoutes(updated)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = CrimsonRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Ride Briefing / Notes") },
                placeholder = { Text("Pace, gear requirements, fuel stops...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MotoOrange,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = MotoOrange,
                    unfocusedLabelColor = TextSecondary
                )
            )

            // Date & Time Picker Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CarbonDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("SCHEDULED START (EXPIRES IN 12 HOURS)", style = Typography.labelSmall, color = TextSecondary)
                Text(
                    text = scheduledDisplay,
                    style = Typography.titleMedium.copy(fontFamily = StatFontFamily),
                    color = ElectricCyan
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    selectedYear = y
                                    selectedMonth = m
                                    selectedDay = d
                                },
                                selectedYear, selectedMonth, selectedDay
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Date", color = TextPrimary, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, min ->
                                    selectedHour = h
                                    selectedMinute = min
                                },
                                selectedHour, selectedMinute, true
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Time", color = TextPrimary, fontSize = 12.sp)
                    }
                }
            }

            OutlinedTextField(
                value = maxParticipantsText,
                onValueChange = { if (it.all { ch -> ch.isDigit() }) maxParticipantsText = it },
                label = { Text("Max Participants (2–200)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MotoOrange,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = MotoOrange,
                    unfocusedLabelColor = TextSecondary
                )
            )
        }

        // Submit Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsphaltBlack)
                .border(0.5.dp, BorderSubtle)
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    val trimmedTitle = title.trim()
                    val startPlace = planner.start
                    val destPlace = planner.destination
                    val chosenRoute = planner.selectedRoute
                    val maxP = maxParticipantsText.toIntOrNull() ?: 25
                    if (trimmedTitle.isEmpty()) {
                        errorMessage = "Please enter a ride title."
                        return@Button
                    }
                    if (startPlace == null || destPlace == null) {
                        errorMessage = "Please select both a starting point and destination on the route planner."
                        return@Button
                    }
                    if (chosenRoute == null || chosenRoute.encodedPolyline.isBlank()) {
                        errorMessage = "Please calculate and select a valid route before publishing."
                        return@Button
                    }
                    if (scheduledZdt.toInstant().isBefore(Instant.now())) {
                        errorMessage = "Scheduled start time must be in the future."
                        return@Button
                    }
                    if (maxP !in 2..200) {
                        errorMessage = "Max participants must be between 2 and 200."
                        return@Button
                    }

                    isSubmitting = true
                    errorMessage = null
                    val isoUtc = DateTimeFormatter.ISO_INSTANT.format(
                        scheduledZdt.withZoneSameInstant(ZoneOffset.UTC).toInstant()
                    )
                    scope.launch {
                        val result = repository.createClubRide(
                            clubId = club.id,
                            request = CreateClubRideRequest(
                                title = trimmedTitle,
                                description = description.trim(),
                                meetingPoint = startPlace.name,
                                destination = destPlace.name,
                                startLat = startPlace.lat,
                                startLng = startPlace.lng,
                                destLat = destPlace.lat,
                                destLng = destPlace.lng,
                                encodedPolyline = chosenRoute.encodedPolyline,
                                routeDistanceMeters = chosenRoute.distanceMeters,
                                routeDurationSeconds = chosenRoute.durationSeconds,
                                routeSummary = chosenRoute.summary,
                                scheduledStart = isoUtc,
                                maxParticipants = maxP,
                                waypoints = planner.pitStops
                            )
                        )
                        isSubmitting = false
                        result.onSuccess { created ->
                            onCreated(created)
                        }.onFailure { err ->
                            errorMessage = err.message ?: "Failed to schedule club ride."
                        }
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MotoOrange, contentColor = AsphaltBlack)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = AsphaltBlack, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("PUBLISH CLUB RIDE", style = Typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
