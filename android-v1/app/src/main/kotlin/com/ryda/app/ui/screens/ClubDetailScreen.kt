package com.ryda.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import com.ryda.app.core.network.ClubLeaderboardResponse
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
    var selectedTab by remember { mutableStateOf(0) } // 0 = Feed (Default), 1 = Events, 2 = Club Rides, 3 = Leaderboard
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
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showMoreInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "More Info",
                            tint = MotoOrange
                        )
                    }

                    val isMember = currentClub.isMember
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
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMember) SurfaceCard else MotoOrange,
                            contentColor = if (isMember) TextPrimary else AsphaltBlack
                        )
                    ) {
                        Text(
                            text = if (isMember) "Joined ✓" else "Join Club",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Emblem Icon
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 10.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SurfaceCard)
                    .border(2.dp, MotoOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = MotoOrange,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Club Meta
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentClub.name,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (currentClub.isVerified) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ForestGreen.copy(alpha = 0.2f))
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

        // Club Navigation Underline Tabs (Feed, Events, Club Rides, Leaderboard)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Feed (${clubPosts.size})",
                color = if (selectedTab == 0) MotoOrange else TextSecondary,
                style = Typography.labelMedium,
                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { selectedTab = 0 }
                    .padding(bottom = 8.dp)
            )
            Text(
                text = "Events (${clubEvents.size})",
                color = if (selectedTab == 1) MotoOrange else TextSecondary,
                style = Typography.labelMedium,
                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { selectedTab = 1 }
                    .padding(bottom = 8.dp)
            )
            Text(
                text = "Rides (${upcomingClubRides.size + previousClubRides.size})",
                color = if (selectedTab == 2) MotoOrange else TextSecondary,
                style = Typography.labelMedium,
                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { selectedTab = 2 }
                    .padding(bottom = 8.dp)
            )
            Text(
                text = "Leaderboard",
                color = if (selectedTab == 3) MotoOrange else TextSecondary,
                style = Typography.labelMedium,
                fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { selectedTab = 3 }
                    .padding(bottom = 8.dp)
            )
        }

        HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // ── TAB CONTENT ────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (selectedTab == 0) {
                // CLUB ACTIVITY FEED
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
                                border = BorderStroke(1.dp, if (event.isOfficial) MotoOrange.copy(alpha = 0.5f) else BorderSubtle)
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
            } else if (selectedTab == 2) {
                // CLUB RIDES
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
            } else if (selectedTab == 3) {
                // CLUB LEADERBOARD TAB
                ClubLeaderboardTab(clubId = currentClub.id, repository = repository)
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
                    Text("ABOUT", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(currentClub.description.ifEmpty { "No description provided." }, color = TextPrimary, fontSize = 13.sp)
                    Text("Location: ${currentClub.city}", color = TextSecondary, fontSize = 12.sp)
                    Text("Joining Policy: ${currentClub.policy}", color = TextSecondary, fontSize = 12.sp)

                    HorizontalDivider(color = BorderSubtle)

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
private fun ClubLeaderboardTab(
    clubId: String,
    repository: RydaRepository
) {
    var period by remember { mutableStateOf("monthly") }
    var leaderboardResponse by remember { mutableStateOf<ClubLeaderboardResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(clubId, period) {
        isLoading = true
        leaderboardResponse = repository.fetchClubLeaderboard(clubId, period)
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isMonthly = period == "monthly"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isMonthly) SafetyAmber else CarbonDark)
                    .border(1.dp, if (isMonthly) SafetyAmber else BorderSubtle, RoundedCornerShape(10.dp))
                    .clickable { period = "monthly" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Monthly Distance", color = if (isMonthly) AsphaltBlack else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            val isAllTime = period == "all_time"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isAllTime) SafetyAmber else CarbonDark)
                    .border(1.dp, if (isAllTime) SafetyAmber else BorderSubtle, RoundedCornerShape(10.dp))
                    .clickable { period = "all_time" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("All-Time Distance", color = if (isAllTime) AsphaltBlack else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SafetyAmber)
            }
        } else {
            val entries = leaderboardResponse?.leaderboard ?: emptyList()
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No member rides recorded for this period yet", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(entries) { item ->
                        val rankColor = when (item.rank) {
                            1 -> SafetyAmber
                            2 -> Color(0xFFC0C0C0)
                            3 -> Color(0xFFCD7F32)
                            else -> TextSecondary
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CarbonDark),
                            border = BorderStroke(1.dp, if (item.rank <= 3) rankColor.copy(alpha = 0.5f) else BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(rankColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("#${item.rank}", color = rankColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(text = item.displayName.ifBlank { item.username }, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(text = item.role, color = TextSecondary, fontSize = 11.sp)
                                    }
                                }

                                Text(text = "${item.distanceKm} km", color = SafetyAmber, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClubRideSummaryCard(
    session: ClubRideSession,
    isHistorical: Boolean,
    onClick: () -> Unit
) {
    val isActive = session.status == "ACTIVE"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark),
        border = BorderStroke(
            1.dp,
            if (isActive) MotoOrange else BorderSubtle
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = session.title,
                        style = Typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MotoOrange.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("LIVE", style = Typography.labelSmall, color = MotoOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${session.meetingPoint.ifBlank { "Start" }} ➔ ${session.destination}",
                    style = Typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.scheduledStart.take(16),
                        style = Typography.labelSmall.copy(fontFamily = StatFontFamily),
                        color = TextPrimary
                    )
                    Text(
                        text = "${session.participantCount} riders",
                        style = Typography.labelSmall,
                        color = ForestGreen
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
