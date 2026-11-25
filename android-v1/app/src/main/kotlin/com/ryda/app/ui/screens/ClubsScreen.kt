package com.ryda.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.ryda.app.core.models.Club
import com.ryda.app.core.network.NetworkMonitor
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.components.OfflineStateView
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ClubsScreen(
    repository: RydaRepository,
    completedRidesCount: Int = 0,
    onClubClick: (Club) -> Unit,
    onStartClubRide: (Club) -> Unit = {},
    viewModel: ClubsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ClubsViewModel(repository) as T
            }
        }
    )
) {
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor.getInstance(context) }
    val isOnline by networkMonitor.isOnline.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var clubs by remember { mutableStateOf<List<Club>>(emptyList()) }
    var selectedCity by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    // 0 = My Clubs (Default), 1 = Explore Clubs
    var selectedTabIndex by remember { mutableStateOf(0) }

    var showLockDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var eligibilityStatus by remember { mutableStateOf<com.ryda.app.core.network.ClubEligibilityResponse?>(null) }
    var eligibilityErrorMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        coroutineScope.launch {
            isLoading = true
            clubs = repository.getClubs(null)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    LaunchedEffect(viewModel) {
        viewModel.clubCreatedEvents.collect { newClub ->
            showCreateDialog = false
            refresh()
            onClubClick(newClub)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Motorcycle Clubs",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Connect with riders, chapters & communities",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.checkEligibility(
                            onEligible = { showCreateDialog = true },
                            onIneligible = { status ->
                                eligibilityStatus = status
                                showLockDialog = true
                            },
                            onError = {
                                eligibilityErrorMessage = "Couldn't connect to RYDA. Check your internet connection and try again."
                            }
                        )
                    },
                    enabled = !viewModel.isCheckingEligibility
                ) {
                    if (viewModel.isCheckingEligibility) {
                        CircularProgressIndicator(color = MotoOrange, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Create Club",
                            tint = MotoOrange,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Tab Row: MY CLUBS / EXPLORE CLUBS ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("My Clubs", "Explore Clubs").forEachIndexed { idx, label ->
                    val selected = selectedTabIndex == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MotoOrange else Color.Transparent)
                            .clickable { selectedTabIndex = idx }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // City Filter Chips (Shown in Explore Clubs tab)
            if (selectedTabIndex == 1) {
                val cities = listOf("All", "Mumbai", "Pune", "Goa")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cities) { city ->
                        val isSelected = (city == "All" && selectedCity == null) || (city == selectedCity)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) MotoOrange else SurfaceCard)
                                .border(1.dp, if (isSelected) MotoOrange else BorderSubtle, RoundedCornerShape(20.dp))
                                .clickable { selectedCity = if (city == "All") null else city }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = city,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (!isOnline) {
                OfflineStateView(
                    title = "Clubs Unavailable Offline",
                    description = "Browsing motorcycle clubs and joining chapters requires an active internet connection.",
                    icon = Icons.Default.Groups,
                    onRetry = {
                        networkMonitor.forceCheckStatus()
                        refresh()
                    }
                )
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MotoOrange)
                }
            } else {
                when (selectedTabIndex) {
                    // ── MY CLUBS TAB (Joined Clubs) ────────────────────────────────
                    0 -> {
                        val myClubs = clubs.filter { it.isMember }
                        if (myClubs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Groups, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(56.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("You haven't joined any clubs yet.", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Explore discoverable motorcycle chapters and connect with riders.", color = TextSecondary, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = { selectedTabIndex = 1 },
                                        colors = ButtonDefaults.buttonColors(containerColor = MotoOrange),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Explore Clubs", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                item {
                                    Text(
                                        text = "JOINED CLUBS (${myClubs.size})",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                items(myClubs, key = { it.id }) { club ->
                                    ClubCardItem(
                                        club = club,
                                        onClubClick = { onClubClick(club) },
                                        onJoinClick = {
                                            coroutineScope.launch {
                                                val res = repository.toggleClubMembership(club.id)
                                                if (res != null) {
                                                    clubs = clubs.map {
                                                        if (it.id == club.id) it.copy(
                                                            isMember = res.isMember,
                                                            membersCount = res.membersCount
                                                        ) else it
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ── EXPLORE CLUBS TAB (Discoverable Clubs respecting visibility rules) ──
                    1 -> {
                        // Visibility rules: OPEN or REQUEST_TO_JOIN, exclude already joined and INVITE_ONLY
                        val exploreClubs = clubs.filter { 
                            !it.isMember && 
                            it.policy != "INVITE_ONLY" && 
                            (selectedCity == null || it.city.equals(selectedCity, ignoreCase = true) || it.city.contains(selectedCity!!, ignoreCase = true)) 
                        }
                        if (exploreClubs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(imageVector = Icons.Default.Explore, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("No discoverable clubs found", color = TextSecondary, fontSize = 14.sp)
                                    Text("Try selecting a different city filter", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                item {
                                    Text(
                                        text = "DISCOVERABLE CHAPTERS (${exploreClubs.size})",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                items(exploreClubs, key = { it.id }) { club ->
                                    ClubCardItem(
                                        club = club,
                                        onClubClick = { onClubClick(club) },
                                        onJoinClick = {
                                            coroutineScope.launch {
                                                val res = repository.toggleClubMembership(club.id)
                                                if (res != null) {
                                                    clubs = clubs.map {
                                                        if (it.id == club.id) it.copy(
                                                            isMember = res.isMember,
                                                            membersCount = res.membersCount
                                                        ) else it
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 🔒 Eligibility Lock Dialog
        if (showLockDialog) {
            val ridesDone = eligibilityStatus?.completedRides ?: completedRidesCount
            val postsDone = eligibilityStatus?.publicPosts ?: 0

            AlertDialog(
                onDismissRequest = { showLockDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = SafetyAmber)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Club Creation Locked", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Creating an official Motorcycle Club requires completing at least 5 rides and publishing at least 1 public ride post in the community.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CarbonDark)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Completed Rides:", color = TextSecondary, fontSize = 12.sp)
                            Text("$ridesDone / 5 Rides", color = if (ridesDone >= 5) ForestGreen else SafetyAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CarbonDark)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Public Ride Posts:", color = TextSecondary, fontSize = 12.sp)
                            Text("$postsDone / 1 Post", color = if (postsDone >= 1) ForestGreen else SafetyAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showLockDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack)
                    ) {
                        Text("Got It")
                    }
                },
                containerColor = SurfaceCard
            )
        }

        // Connectivity Error Dialog
        if (eligibilityErrorMessage != null) {
            AlertDialog(
                onDismissRequest = { eligibilityErrorMessage = null },
                title = { Text("Connection Error", color = CrimsonRed, fontWeight = FontWeight.Bold) },
                text = { Text(eligibilityErrorMessage!!, color = TextSecondary, fontSize = 13.sp) },
                confirmButton = {
                    Button(onClick = { eligibilityErrorMessage = null }, colors = ButtonDefaults.buttonColors(containerColor = MotoOrange)) {
                        Text("OK")
                    }
                },
                containerColor = CarbonDark
            )
        }

        // ➕ Create Club Form Dialog (with validation & joining criteria policies)
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create Motorcycle Club", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = viewModel.name,
                            onValueChange = { viewModel.updateName(it) },
                            label = { Text("Club Name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = viewModel.city,
                            onValueChange = { viewModel.updateCity(it) },
                            label = { Text("City / Region *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = viewModel.description,
                            onValueChange = { viewModel.updateDescription(it) },
                            label = { Text("Description *") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = viewModel.memberLimit,
                            onValueChange = { viewModel.updateMemberLimit(it) },
                            label = { Text("Member Limit (Max 100) *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Joining Criteria:", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                        // Policy options
                        val policies = listOf(
                            Triple("OPEN", "Anyone Can Join", "Active immediately"),
                            Triple("REQUEST", "Request to Join", "Admin approval required"),
                            Triple("INVITE_ONLY", "Invite Only", "Hidden from public explore")
                        )

                        policies.forEach { (code, label, hint) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (viewModel.policy == code) SurfaceCard else AsphaltBlack)
                                    .clickable { viewModel.updatePolicy(code) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = viewModel.policy == code,
                                    onClick = { viewModel.updatePolicy(code) },
                                    colors = RadioButtonDefaults.colors(selectedColor = MotoOrange)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(hint, color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }

                        if (viewModel.validationError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(viewModel.validationError!!, color = CrimsonRed, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.createClub()
                        },
                        enabled = !viewModel.isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = MotoOrange, contentColor = Color.White)
                    ) {
                        if (viewModel.isSubmitting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Create Club")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = CarbonDark
            )
        }
    }
}

@Composable
fun ClubCardItem(
    club: Club,
    onClubClick: () -> Unit,
    onJoinClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClubClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Groups, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(26.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = club.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (club.isVerified) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SurfaceCard)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Verified", color = ForestGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(text = "${club.city} • ${club.policy} Club", color = TextSecondary, fontSize = 11.sp)
                    }
                }

                // Join Button / Joined Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (club.isMember) SurfaceCard else MotoOrange)
                        .border(1.dp, if (club.isMember) ForestGreen else MotoOrange, RoundedCornerShape(8.dp))
                        .clickable { onJoinClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (club.isMember) "Joined ✓" else (if (club.policy == "REQUEST") "Request" else "Join"),
                        color = if (club.isMember) ForestGreen else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (club.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = club.description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AsphaltBlack)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(text = "Active Riders", color = TextSecondary, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "${club.membersCount}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Total Mileage", color = TextSecondary, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "${club.totalMileageKm.toInt()} KM", color = MotoOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(text = "Policy", color = TextSecondary, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = club.policy, color = ForestGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
