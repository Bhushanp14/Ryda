package com.ryda.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.core.models.Club
import com.ryda.app.core.models.ClubRideSession
import com.ryda.app.core.models.CommunityPost
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ClubDetailScreen(
    club: Club,
    repository: RydaRepository,
    onBackClick: () -> Unit,
    onOpenClubRide: (Club, ClubRideSession) -> Unit = { _, _ -> },
    onCreateClubRide: (Club) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentClub by remember { mutableStateOf(club) }
    var clubPosts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var isLoadingFeed by remember { mutableStateOf(false) }
    var showMoreInfoDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Feed (Default), 1 = Events, 2 = Club Rides
    var clubEvents by remember { mutableStateOf<List<com.ryda.app.core.models.RidingEvent>>(emptyList()) }
    var upcomingClubRides by remember { mutableStateOf<List<ClubRideSession>>(emptyList()) }
    var previousClubRides by remember { mutableStateOf<List<ClubRideSession>>(emptyList()) }
    var isLoadingClubRides by remember { mutableStateOf(false) }
    var clubRidesError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(club.id) {
        repository.getClubDetail(club.id)?.let { refreshed ->
            currentClub = refreshed
        }
        isLoadingFeed = true
        try {
            val feed = repository.getCommunityFeed("discover")
            clubPosts = feed.filter { post ->
                post.clubId == club.id
            }
        } catch (_: Exception) {
            clubPosts = emptyList()
        } finally {
            isLoadingFeed = false
        }
        try {
            val allEvents = repository.getExploreEvents()
            // Strictly match ONLY events belonging to this specific club
            clubEvents = allEvents.filter { it.clubId == club.id }
        } catch (_: Exception) {
            clubEvents = emptyList()
        }
        isLoadingClubRides = true
        clubRidesError = null
        repository.getClubRides(club.id, section = "upcoming")
            .onSuccess { sessions ->
                upcomingClubRides = sessions
            }
            .onFailure { err ->
                clubRidesError = err.message
            }
        repository.getClubRides(club.id, section = "previous")
            .onSuccess { sessions ->
                previousClubRides = sessions
            }
            .onFailure { err ->
                if (clubRidesError == null) clubRidesError = err.message
            }
        isLoadingClubRides = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
    ) {
        // Banner Header Block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SurfaceElevated, AsphaltBlack)
                    )
                )
        ) {
            // Back & More Info Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                TextButton(
                    onClick = { showMoreInfoDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("More Info", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Club Avatar & Join Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CarbonDark)
                        .border(2.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Groups, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(36.dp))
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            val res = repository.toggleClubMembership(currentClub.id)
                            if (res != null) {
                                currentClub = currentClub.copy(
                                    isMember = res.isMember,
                                    membersCount = res.membersCount
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentClub.isMember) ForestGreen else MotoOrange
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (currentClub.isMember) "Member (Joined) ✓" else (if (currentClub.policy == "REQUEST") "Request to Join" else "Join Club"),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Club Meta Header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = currentClub.name, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (currentClub.isVerified) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceCard)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Verified Club", color = ForestGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (currentClub.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = currentClub.description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3-Metric Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CarbonDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Active Riders", color = TextSecondary, fontSize = 10.sp)
                    Text("${currentClub.membersCount}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Club Mileage", color = TextSecondary, fontSize = 10.sp)
                    Text("${currentClub.totalMileageKm.toInt()} KM", color = MotoOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Policy", color = TextSecondary, fontSize = 10.sp)
                    Text(currentClub.policy, color = ForestGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Club Navigation Underline Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Club Feed (${clubPosts.size})",
                color = if (selectedTab == 0) MotoOrange else TextSecondary,
                style = Typography.labelMedium,
                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { selectedTab = 0 }
                    .padding(bottom = 8.dp)
            )
            Text(
                text = "Club Events (${clubEvents.size})",
                color = if (selectedTab == 1) MotoOrange else TextSecondary,
                style = Typography.labelMedium,
                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { selectedTab = 1 }
                    .padding(bottom = 8.dp)
            )
            Text(
                text = "Club Rides (${upcomingClubRides.size + previousClubRides.size})",
                color = if (selectedTab == 2) MotoOrange else TextSecondary,
                style = Typography.labelMedium,
                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { selectedTab = 2 }
                    .padding(bottom = 8.dp)
            )
        }

        HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // ── TAB CONTENT ────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (selectedTab == 0) {
                // CLUB ACTIVITY FEED (DEFAULT)
                if (isLoadingFeed) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MotoOrange)
                    }
                } else if (clubPosts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.RssFeed, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No club posts or ride stories yet", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Share a ride or announcement to start the community feed", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(clubPosts, key = { it.id }) { post ->
                            CommunityPostCard(
                                post = post,
                                onLikeClick = {
                                    coroutineScope.launch {
                                        val res = repository.togglePostLike(post.id)
                                        if (res != null) {
                                            clubPosts = clubPosts.map {
                                                if (it.id == post.id) it.copy(isLiked = res.isLiked, likesCount = res.likesCount) else it
                                            }
                                        }
                                    }
                                },
                                onFollowClick = {
                                    coroutineScope.launch {
                                        val res = repository.toggleRiderFollow(post.authorId)
                                        if (res != null) {
                                            clubPosts = clubPosts.map {
                                                if (it.authorId == post.authorId) it.copy(isFollowingAuthor = res.isFollowing) else it
                                            }
                                        }
                                    }
                                },
                                onCommentsClick = {},
                                onShareClick = {
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Check out ${post.authorDisplayName}'s ride on RYDA: ${post.caption}")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Ride"))
                                }
                            )
                        }
                    }
                }
            } else if (selectedTab == 1) {
                // CLUB EVENTS
                if (clubEvents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No upcoming events scheduled", style = Typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Club events will be announced by club leaders.", style = Typography.bodySmall, color = TextSecondary)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(clubEvents, key = { it.id }) { event ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (event.isOfficial) MotoOrange.copy(alpha = 0.5f) else BorderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (event.isOfficial) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MotoOrange.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("NEXT OFFICIAL RALLY", style = Typography.labelSmall, color = MotoOrange, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Text("CLUB MEET", style = Typography.labelSmall, color = TextSecondary)
                                        }
                                        Text(event.startTime.take(16), style = Typography.labelSmall.copy(fontFamily = StatFontFamily), color = TextPrimary)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(event.title, style = Typography.titleMedium, color = TextPrimary)
                                    if (event.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(event.description, style = Typography.bodySmall, color = TextSecondary)
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.6f))
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (event.meetingPoint.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.Place, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(14.dp))
                                                Text("Meeting: ${event.meetingPoint}", style = Typography.bodySmall, color = TextSecondary)
                                            }
                                        }
                                        if (event.destination.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.Flag, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
                                                Text("Destination: ${event.destination}", style = Typography.bodySmall, color = TextSecondary)
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.Group, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(14.dp))
                                            Text("${event.confirmedCount} Riders Confirmed (Limit: ${event.riderLimit})", style = Typography.bodySmall, color = ForestGreen)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // RSVP Controls
                                    var rsvpStatus by remember { mutableStateOf(event.myRsvp) }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                rsvpStatus = "GOING"
                                                coroutineScope.launch { repository.setEventRsvp(event.id, "GOING") }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = if (rsvpStatus == "GOING") MotoOrange else SurfaceCard)
                                        ) {
                                            Text(
                                                text = if (rsvpStatus == "GOING") "✓ Going (RSVP'd)" else "Going",
                                                style = Typography.labelSmall,
                                                color = if (rsvpStatus == "GOING") AsphaltBlack else TextPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                rsvpStatus = "INTERESTED"
                                                coroutineScope.launch { repository.setEventRsvp(event.id, "INTERESTED") }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = if (rsvpStatus == "INTERESTED") ElectricCyan else SurfaceCard)
                                        ) {
                                            Text(
                                                text = if (rsvpStatus == "INTERESTED") "★ Interested" else "Interested",
                                                style = Typography.labelSmall,
                                                color = if (rsvpStatus == "INTERESTED") AsphaltBlack else TextPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // CLUB RIDES (Scheduled & Active Sessions)
                Column(modifier = Modifier.fillMaxSize()) {
                    if (currentClub.myRole == "LEADER") {
                        Button(
                            onClick = { onCreateClubRide(currentClub) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MotoOrange, contentColor = AsphaltBlack)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Schedule Club Ride", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    when {
                        isLoadingClubRides -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MotoOrange)
                            }
                        }
                        clubRidesError != null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(clubRidesError!!, color = CrimsonRed, style = Typography.bodySmall)
                            }
                        }
                        upcomingClubRides.isEmpty() && previousClubRides.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.TwoWheeler, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No club rides yet", style = Typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Club leaders can schedule group rides here.", style = Typography.bodySmall, color = TextSecondary)
                                }
                            }
                        }
                        else -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                item(key = "header_upcoming") {
                                    Text(
                                        "UPCOMING RIDES",
                                        style = Typography.labelSmall,
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (upcomingClubRides.isEmpty()) {
                                    item(key = "empty_upcoming") {
                                        Text(
                                            "No upcoming club rides scheduled.",
                                            style = Typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                } else {
                                    items(upcomingClubRides, key = { "upcoming_${it.id}" }) { session ->
                                        ClubRideSummaryCard(
                                            session = session,
                                            isHistorical = false,
                                            onClick = { onOpenClubRide(currentClub, session) }
                                        )
                                    }
                                }

                                item(key = "divider_history") {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        thickness = 1.dp,
                                        color = BorderSubtle
                                    )
                                }

                                item(key = "header_previous") {
                                    Text(
                                        "PREVIOUS RIDES",
                                        style = Typography.labelSmall,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (previousClubRides.isEmpty()) {
                                    item(key = "empty_previous") {
                                        Text(
                                            "No completed or expired club rides yet.",
                                            style = Typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                } else {
                                    items(previousClubRides, key = { "previous_${it.id}" }) { session ->
                                        ClubRideSummaryCard(
                                            session = session,
                                            isHistorical = true,
                                            onClick = { onOpenClubRide(currentClub, session) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ℹ️ More Info Dialog (About, Members, Statistics, Management)
    if (showMoreInfoDialog) {
        AlertDialog(
            onDismissRequest = { showMoreInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MotoOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(currentClub.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // About Section
                    Text("ABOUT", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(currentClub.description.ifEmpty { "No description provided." }, color = TextPrimary, fontSize = 13.sp)
                    Text("Location: ${currentClub.city}", color = TextSecondary, fontSize = 12.sp)
                    Text("Joining Policy: ${currentClub.policy}", color = TextSecondary, fontSize = 12.sp)

                    HorizontalDivider(color = BorderSubtle)

                    // Members Section
                    Text("MEMBERS & LEADERSHIP", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(SurfaceCard), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text("Chapter Owner / Admin", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Total Active Members: ${currentClub.membersCount}", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = BorderSubtle)

                    // Statistics Section
                    Text("CLUB STATISTICS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Mileage:", color = TextSecondary, fontSize = 12.sp)
                        Text("${currentClub.totalMileageKm.toInt()} KM", color = MotoOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Verification Status:", color = TextSecondary, fontSize = 12.sp)
                        Text(if (currentClub.isVerified) "Verified Chapter" else "Standard", color = ForestGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (currentClub.isMember) {
                        HorizontalDivider(color = BorderSubtle)
                        Text("CLUB MANAGEMENT", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    repository.toggleClubMembership(currentClub.id)
                                    showMoreInfoDialog = false
                                    onBackClick()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = CrimsonRed)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Leave Club", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoreInfoDialog = false }) {
                    Text("Close", color = MotoOrange)
                }
            },
            containerColor = CarbonDark
        )
    }
}

@Composable
private fun ClubRideSummaryCard(
    session: ClubRideSession,
    isHistorical: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark.copy(alpha = if (isHistorical) 0.78f else 1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(session.status, sessionStatusColor(session.status))
                if (!isHistorical && session.myStatus != null) {
                    StatusPill(session.myStatus, participantStatusColor(session.myStatus))
                }
            }
            Text(session.title, style = Typography.titleMedium, color = TextPrimary)
            if (!isHistorical) {
                Text("Start: ${session.meetingPoint}", style = Typography.bodySmall, color = TextSecondary)
                Text("Dest: ${session.destination}", style = Typography.bodySmall, color = TextSecondary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatClubRideTime(session.scheduledStart),
                    style = Typography.labelSmall.copy(fontFamily = StatFontFamily),
                    color = if (isHistorical) TextSecondary else ElectricCyan
                )
                if (!isHistorical) {
                    Text(
                        "${session.participantCount}/${session.maxParticipants} riders",
                        style = Typography.labelSmall,
                        color = ForestGreen
                    )
                }
            }
        }
    }
}
