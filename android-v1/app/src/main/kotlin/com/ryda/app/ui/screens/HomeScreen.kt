package com.ryda.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.core.models.Motorcycle
import com.ryda.app.core.models.Ride
import com.ryda.app.core.models.RiderProfile
import com.ryda.app.core.models.TrackingState
import com.ryda.app.ui.components.RydaMetricCard
import com.ryda.app.ui.theme.AsphaltBlack
import com.ryda.app.ui.theme.BorderSubtle
import com.ryda.app.ui.theme.CarbonDark
import com.ryda.app.ui.theme.CrimsonRed
import com.ryda.app.ui.theme.ElectricCyan
import com.ryda.app.ui.theme.HighVisLime
import com.ryda.app.ui.theme.MotoOrange
import com.ryda.app.ui.theme.SafetyAmber
import com.ryda.app.ui.theme.SurfaceCard
import com.ryda.app.ui.theme.SurfaceElevated
import com.ryda.app.ui.theme.TextPrimary
import com.ryda.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    profile: RiderProfile,
    primaryMotorcycle: Motorcycle?,
    recentRides: List<Ride>,
    trackingState: TrackingState,
    incompleteRide: Ride? = null,
    showBatteryOptimizationPrompt: Boolean = false,
    onStartRideClick: () -> Unit,
    onRideClick: (String) -> Unit,
    onGarageClick: () -> Unit,
    onActiveHudClick: () -> Unit,
    onResumeIncompleteRide: (Ride) -> Unit = {},
    onFinishIncompleteRide: (Ride) -> Unit = {},
    onDiscardIncompleteRide: (Ride) -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    onNotificationClick: () -> Unit = {},
    onViewAllRidesClick: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            // Rider Header with Brand Square Logo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Stitch Brand Orange "R" Square Logo
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MotoOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "R",
                            color = AsphaltBlack,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = com.ryda.app.ui.theme.HeadlineFontFamily
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "RYDA",
                                color = TextPrimary,
                                style = com.ryda.app.ui.theme.Typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceCard)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("PRO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MotoOrange)
                            }
                        }
                        Text(
                            text = "Namaste, ${profile.displayName} • ${if (profile.city.isNotBlank()) profile.city else "India"}",
                            style = com.ryda.app.ui.theme.Typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Notification Bell Button with Unread Badge
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { onNotificationClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = if (unreadNotificationCount > 0) MotoOrange else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        if (unreadNotificationCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MotoOrange)
                            )
                        }
                    }

                    // Garage Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                            .clickable { onGarageClick() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                            contentDescription = "Garage",
                            tint = MotoOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Garage",
                            color = TextPrimary,
                            style = com.ryda.app.ui.theme.Typography.labelSmall
                        )
                    }
                }
            }
        }

        // Unfinished Ride Recovery Banner (PRD §5.4 & §15 Crash Recovery)
        if (incompleteRide != null && !trackingState.isTracking) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonDark),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, SafetyAmber)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚠️ UNFINISHED RIDE DETECTED", color = SafetyAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "'${incompleteRide.title}' was interrupted (${String.format("%.1f", incompleteRide.totalDistanceKm)} km recorded).",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onResumeIncompleteRide(incompleteRide) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack)
                            ) {
                                Text("Resume", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Button(
                                onClick = { onFinishIncompleteRide(incompleteRide) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = HighVisLime)
                            ) {
                                Text("Save & Finish", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { onDiscardIncompleteRide(incompleteRide) },
                                modifier = Modifier.weight(0.7f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = CrimsonRed)
                            ) {
                                Text("Discard", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Battery Optimization Guidance (PRD §14 & §15)
        if (showBatteryOptimizationPrompt && !trackingState.isTracking) {
            item {
                var showDialog by remember { mutableStateOf(false) }

                if (showDialog) {
                    com.ryda.app.ui.components.BatteryGuidanceDialog(
                        onDismiss = { showDialog = false }
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⚡ OEM Battery Optimization Guide", color = ElectricCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Tap to see setup steps for ${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} to prevent GPS drops.", color = TextSecondary, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { showDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = AsphaltBlack)
                        ) {
                            Text("Configure", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Ride Banner if recording is already in progress
        if (trackingState.isTracking) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onActiveHudClick() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (trackingState.isPaused) SafetyAmber else HighVisLime)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (trackingState.isPaused) "RIDE PAUSED" else "RIDE RECORDING ACTIVE",
                                    color = if (trackingState.isPaused) SafetyAmber else HighVisLime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${String.format("%.1f", trackingState.totalDistanceKm)} km • ${trackingState.formattedMovingTime}",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = "OPEN HUD →",
                            color = ElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Main START RIDE Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GPS TRACKING",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(HighVisLime)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Offline Ready",
                                color = HighVisLime,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Hit the road. RYDA records in background while you navigate.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Motorcycle Selector Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AsphaltBlack)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .clickable { onGarageClick() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(16.dp))
                            Text(
                                text = primaryMotorcycle?.let { "${it.brand} ${it.model}" } ?: "Select Motorcycle",
                                style = com.ryda.app.ui.theme.Typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                        Text("Switch >", style = com.ryda.app.ui.theme.Typography.labelSmall, color = MotoOrange)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onStartRideClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MotoOrange,
                            contentColor = AsphaltBlack
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (trackingState.isTracking) "CONTINUE RECORDING" else "START RIDE",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // Expressive Telemetry Snapshot (Season / Lifetime Metrics) - 2x2 Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MotoOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "TELEMETRY ODOMETER",
                        style = com.ryda.app.ui.theme.Typography.labelSmall.copy(fontFamily = com.ryda.app.ui.theme.HeadlineFontFamily),
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CarbonDark)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Season 2026",
                        style = com.ryda.app.ui.theme.Typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2x2 Grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Total Distance
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CarbonDark)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Total Distance", style = com.ryda.app.ui.theme.Typography.bodySmall, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format(Locale.US, "%,.0f", profile.totalDistanceKm),
                                    style = com.ryda.app.ui.theme.Typography.headlineMedium.copy(fontFamily = com.ryda.app.ui.theme.StatFontFamily),
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("KM", style = com.ryda.app.ui.theme.Typography.labelSmall.copy(fontFamily = com.ryda.app.ui.theme.HeadlineFontFamily), color = MotoOrange)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Lifetime recorded", style = com.ryda.app.ui.theme.Typography.labelSmall, color = com.ryda.app.ui.theme.TextTertiary)
                        }
                    }

                    // Total Rides
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CarbonDark)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Rides Logged", style = com.ryda.app.ui.theme.Typography.bodySmall, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${profile.totalRides}",
                                    style = com.ryda.app.ui.theme.Typography.headlineMedium.copy(fontFamily = com.ryda.app.ui.theme.StatFontFamily),
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rides", style = com.ryda.app.ui.theme.Typography.labelSmall.copy(fontFamily = com.ryda.app.ui.theme.HeadlineFontFamily), color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Verified sessions", style = com.ryda.app.ui.theme.Typography.labelSmall, color = com.ryda.app.ui.theme.ForestGreen)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Saddle Time
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CarbonDark)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Saddle Time", style = com.ryda.app.ui.theme.Typography.bodySmall, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", profile.totalTimeHours),
                                    style = com.ryda.app.ui.theme.Typography.headlineMedium.copy(fontFamily = com.ryda.app.ui.theme.StatFontFamily),
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("HRS", style = com.ryda.app.ui.theme.Typography.labelSmall.copy(fontFamily = com.ryda.app.ui.theme.HeadlineFontFamily), color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Moving duration", style = com.ryda.app.ui.theme.Typography.labelSmall, color = com.ryda.app.ui.theme.TextTertiary)
                        }
                    }

                    // Average Pace
                    val avgSpeed = if (profile.totalTimeHours > 0.0) profile.totalDistanceKm / profile.totalTimeHours else 0.0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CarbonDark)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Avg Speed", style = com.ryda.app.ui.theme.Typography.bodySmall, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", avgSpeed),
                                    style = com.ryda.app.ui.theme.Typography.headlineMedium.copy(fontFamily = com.ryda.app.ui.theme.StatFontFamily),
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("KM/H", style = com.ryda.app.ui.theme.Typography.labelSmall.copy(fontFamily = com.ryda.app.ui.theme.HeadlineFontFamily), color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Touring rhythm", style = com.ryda.app.ui.theme.Typography.labelSmall, color = com.ryda.app.ui.theme.ForestGreen)
                        }
                    }
                }
            }
        }

        // Recent Rides Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT RIDES",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onViewAllRidesClick() }
                ) {
                    Text(
                        text = "View All →",
                        color = MotoOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (recentRides.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No rides recorded yet. Start your first journey!",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(recentRides.take(5)) { ride ->
                RecentRideItem(ride = ride, onClick = { onRideClick(ride.id) })
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun RecentRideItem(ride: Ride, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val dateString = dateFormat.format(Date(ride.startTime))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column {
            // Render actual ride photograph if available (H-03)
            if (!ride.photoUri.isNullOrBlank()) {
                RideImageThumbnail(
                    photoUri = ride.photoUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ride.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dateString,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format("%.1f", ride.totalDistanceKm)} km",
                        color = SafetyAmber,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ride.formattedDuration(ride.movingTimeSeconds),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
@Composable
fun RideImageThumbnail(photoUri: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    coil.compose.AsyncImage(
        model = coil.request.ImageRequest.Builder(context)
            .data(photoUri)
            .crossfade(true)
            .build(),
        contentDescription = "Ride Photograph",
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}
