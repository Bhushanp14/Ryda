package com.ryda.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.core.models.RiderProfile
import com.ryda.app.ui.theme.*

import kotlinx.coroutines.flow.first
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    profile: RiderProfile,
    onUpdateProfile: (RiderProfile) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var isPublic by remember { mutableStateOf(profile.isProfilePublic) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var showBatteryGuidanceDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showLegacyClubsDialog by remember { mutableStateOf(false) }
    val repository = remember { com.ryda.app.data.repository.RydaRepository.getInstance(context) }

    val tokenDs = remember { com.ryda.app.core.auth.TokenDataStore.getInstance(context) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf<String?>(null) }
    var isOfflineOnly by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoggedIn = tokenDs.isLoggedIn()
        username = tokenDs.usernameFlow.first()
        isOfflineOnly = tokenDs.isOfflineOnly()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Profile",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "PREFERENCES & SECURITY",
                    color = SafetyAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Settings",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Private Rides by Default", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Rides stay private unless shared", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = !isPublic,
                        onCheckedChange = {
                            isPublic = !it
                            onUpdateProfile(profile.copy(isProfilePublic = isPublic))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SafetyAmber,
                            checkedTrackColor = SafetyAmber.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Offline Sync Manager
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // ── Status row ──────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CloudSync, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Offline & Cloud Sync", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = when {
                                    isOfflineOnly -> "Offline Only • Rides will NOT sync to cloud"
                                    isLoggedIn    -> "Logged in as ${username ?: "Rider"} • Cloud Sync Active"
                                    else          -> "Offline Mode (Guest) • Log in to sync"
                                },
                                color = when {
                                    isOfflineOnly -> SafetyAmber
                                    isLoggedIn    -> HighVisLime
                                    else          -> SafetyAmber
                                },
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Offline-only toggle ──────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Offline-Only Mode", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isOfflineOnly) "Sync is PAUSED — rides stay local only"
                                   else "Cloud sync enabled — rides upload automatically",
                            color = if (isOfflineOnly) SafetyAmber else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isOfflineOnly,
                        onCheckedChange = { newValue ->
                            isOfflineOnly = newValue
                            coroutineScope.launch {
                                tokenDs.setOfflineOnly(newValue)
                                syncMessage = if (newValue)
                                    "Offline-only mode ON. Rides will NOT be uploaded until you disable this."
                                else
                                    "Cloud sync re-enabled. Tap 'Sync Now' to upload pending rides."
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SafetyAmber,
                            checkedTrackColor = SafetyAmber.copy(alpha = 0.3f),
                            uncheckedThumbColor = ElectricCyan,
                            uncheckedTrackColor = ElectricCyan.copy(alpha = 0.25f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Automatic Sync Status Description
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCard)
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (isOfflineOnly)
                            "Auto-Sync Paused: Offline-Only mode is enabled. All rides remain saved locally in SQLite DB."
                        else if (isLoggedIn)
                            "Auto-Sync Active: Unsynced rides upload automatically on app launch and post-ride finish."
                        else
                            "Offline Mode (Guest): Log in or Register to enable automatic cloud backup.",
                        color = if (isOfflineOnly || !isLoggedIn) SafetyAmber else ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Account & Security
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DATA PORTABILITY & PRIVACY", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Button(
                    onClick = { com.ryda.app.core.utils.DataExporter.shareDataExport(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = ElectricCyan)
                ) {
                    Text("Export All Ride & Telemetry Data (JSON)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { showBatteryGuidanceDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = TextPrimary)
                ) {
                    Text("OEM Battery Optimization Guide", fontSize = 13.sp)
                }

                Button(
                    onClick = { showLegacyClubsDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = SafetyAmber)
                ) {
                    Text("Legacy Local Clubs Recovery", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("SESSION & ACCOUNT", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = TextPrimary)
                ) {
                    Text("Log Out", fontSize = 13.sp)
                }

                Button(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed.copy(alpha = 0.15f), contentColor = CrimsonRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f))
                ) {
                    Text("Delete Account & Purge Data", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Account & Data?", color = CrimsonRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "In compliance with privacy policies, this will permanently delete your account, your rider profile, and purge all your recorded GPS tracks and motorcycle data from both this device and the cloud. This action cannot be undone.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed, contentColor = androidx.compose.ui.graphics.Color.White)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CarbonDark
        )
    }

    if (showBatteryGuidanceDialog) {
        com.ryda.app.ui.components.BatteryGuidanceDialog(
            onDismiss = { showBatteryGuidanceDialog = false }
        )
    }

    if (showLegacyClubsDialog) {
        val legacyClubs = remember { repository.getLegacyLocalClubs() }

        AlertDialog(
            onDismissRequest = { showLegacyClubsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Groups, contentDescription = null, tint = SafetyAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Legacy Local Clubs", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "These 7 clubs were created locally on this device during earlier testing and are preserved safely in your phone's local database:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (legacyClubs.isEmpty()) {
                        Text("No legacy local clubs found.", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        legacyClubs.forEach { club ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CarbonDark)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(club.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("${club.city} • ${club.policy}", color = TextSecondary, fontSize = 11.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SurfaceCard)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Local Only", color = SafetyAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLegacyClubsDialog = false }) {
                    Text("Close", color = SafetyAmber)
                }
            },
            containerColor = SurfaceCard
        )
    }
}
