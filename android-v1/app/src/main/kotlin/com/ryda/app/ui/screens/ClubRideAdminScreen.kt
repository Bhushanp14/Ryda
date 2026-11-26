package com.ryda.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.core.models.Club
import com.ryda.app.core.models.ClubRideParticipant
import com.ryda.app.core.models.ClubRideSession
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ClubRideAdminScreen(
    club: Club,
    sessionId: String,
    repository: RydaRepository,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<ClubRideSession?>(null) }
    var participants by remember { mutableStateOf<List<ClubRideParticipant>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    fun reloadAll() {
        scope.launch {
            isLoading = true
            errorMessage = null
            repository.getClubRideDetail(club.id, sessionId)
                .onSuccess { session = it }
                .onFailure { errorMessage = it.message }
            repository.getClubRideParticipants(club.id, sessionId)
                .onSuccess { participants = it }
                .onFailure { if (errorMessage == null) errorMessage = it.message }
            isLoading = false
        }
    }

    LaunchedEffect(club.id, sessionId) {
        reloadAll()
    }

    val verifiedCount = participants.count { it.status == "VERIFIED" }
    val readyCount = participants.count { it.status == "READY" }

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
                    Text("Attendance & Roster", style = Typography.titleLarge, color = TextPrimary)
                    Text(session?.title ?: club.name, style = Typography.bodySmall, color = MotoOrange)
                }
            }
            IconButton(onClick = { reloadAll() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
            }
        }

        HorizontalDivider(color = BorderSubtle)

        // Summary Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CarbonDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total", style = Typography.labelSmall, color = TextSecondary)
                Text("${participants.size}", style = Typography.titleMedium, color = TextPrimary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ready", style = Typography.labelSmall, color = TextSecondary)
                Text("$readyCount", style = Typography.titleMedium, color = WarningGold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Verified", style = Typography.labelSmall, color = TextSecondary)
                Text("$verifiedCount", style = Typography.titleMedium, color = ForestGreen)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Status", style = Typography.labelSmall, color = TextSecondary)
                Text(session?.status ?: "—", style = Typography.labelMedium, color = sessionStatusColor(session?.status ?: ""))
            }
        }

        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CrimsonRed.copy(alpha = 0.15f))
                    .padding(10.dp)
            ) {
                Text(errorMessage!!, color = CrimsonRed, style = Typography.bodySmall)
            }
        }
        if (statusBanner != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ForestGreen.copy(alpha = 0.15f))
                    .padding(10.dp)
            ) {
                Text(statusBanner!!, color = ForestGreen, style = Typography.bodySmall)
            }
        }

        // Participant List
        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (isLoading && participants.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MotoOrange)
                }
            } else if (participants.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No riders have joined this club ride yet.", color = TextSecondary, style = Typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(participants, key = { it.id }) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CarbonDark)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    p.displayName.ifBlank { p.username },
                                    style = Typography.titleMedium,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text("@${p.username}", style = Typography.bodySmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                StatusPill(p.status, participantStatusColor(p.status))
                            }

                            if (session?.status == "SCHEDULED") {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (p.status == "READY") {
                                        Button(
                                            onClick = {
                                                isBusy = true
                                                errorMessage = null
                                                scope.launch {
                                                    repository.verifyClubRideParticipant(club.id, sessionId, p.userId)
                                                        .onSuccess { reloadAll() }
                                                        .onFailure { errorMessage = it.message }
                                                    isBusy = false
                                                }
                                            },
                                            enabled = !isBusy,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = AsphaltBlack),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("Verify", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (p.status == "JOINED" || p.status == "READY") {
                                        OutlinedButton(
                                            onClick = {
                                                isBusy = true
                                                errorMessage = null
                                                scope.launch {
                                                    repository.markClubRideParticipantAbsent(club.id, sessionId, p.userId)
                                                        .onSuccess { reloadAll() }
                                                        .onFailure { errorMessage = it.message }
                                                    isBusy = false
                                                }
                                            },
                                            enabled = !isBusy,
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("Absent", color = CrimsonRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Session Controls
        val currentStatus = session?.status ?: ""
        if (currentStatus == "SCHEDULED" || currentStatus == "ACTIVE") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AsphaltBlack)
                    .border(0.5.dp, BorderSubtle)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentStatus == "SCHEDULED") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    repository.cancelClubRide(club.id, sessionId)
                                        .onSuccess {
                                            statusBanner = "Session cancelled."
                                            reloadAll()
                                        }
                                        .onFailure { errorMessage = it.message }
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = CrimsonRed, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    repository.startClubRide(club.id, sessionId)
                                        .onSuccess { res ->
                                            statusBanner = "Session started! Roster locked with ${res.roster.size} verified rider(s)."
                                            reloadAll()
                                        }
                                        .onFailure { errorMessage = it.message }
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy && verifiedCount >= 1,
                            modifier = Modifier.weight(2f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HighVisLime, contentColor = AsphaltBlack)
                        ) {
                            Text(
                                if (verifiedCount >= 1) "START SESSION ($verifiedCount)" else "VERIFY ≥1 RIDER TO START",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (currentStatus == "ACTIVE") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    repository.cancelClubRide(club.id, sessionId)
                                        .onSuccess {
                                            statusBanner = "Session cancelled."
                                            reloadAll()
                                        }
                                        .onFailure { errorMessage = it.message }
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = CrimsonRed, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    repository.completeClubRide(club.id, sessionId)
                                        .onSuccess {
                                            statusBanner = "Session completed."
                                            reloadAll()
                                        }
                                        .onFailure { errorMessage = it.message }
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(2f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = AsphaltBlack)
                        ) {
                            Text("COMPLETE SESSION", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
