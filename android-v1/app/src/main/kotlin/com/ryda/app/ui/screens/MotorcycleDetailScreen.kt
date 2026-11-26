package com.ryda.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.ryda.app.core.network.MotorcycleStatsResponse
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotorcycleDetailScreen(
    motorcycle: Motorcycle,
    repository: RydaRepository,
    onRideClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var stats by remember { mutableStateOf<MotorcycleStatsResponse?>(null) }
    var rides by remember { mutableStateOf<List<Ride>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(motorcycle.id) {
        isLoading = true
        val remoteStats = repository.fetchMotorcycleStats(motorcycle.id)
        stats = remoteStats ?: repository.calculateLocalMotorcycleStats(motorcycle.id)
        rides = repository.getCompletedRidesForMotorcycle(motorcycle.id)
        isLoading = false
    }

    Scaffold(
        containerColor = AsphaltBlack,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CarbonDark)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${motorcycle.brand} ${motorcycle.model}",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!motorcycle.nickname.isNullOrBlank()) {
                            Text(
                                text = "\"${motorcycle.nickname}\"",
                                color = SafetyAmber,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Motorcycle Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonDark),
                    border = BorderStroke(1.dp, if (motorcycle.isPrimary) SafetyAmber else BorderSubtle)
                ) {
                    Column {
                        if (!motorcycle.imageUri.isNullOrBlank()) {
                            RideImageThumbnail(
                                photoUri = motorcycle.imageUri,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            )
                        }
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = motorcycle.displayName,
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (motorcycle.isPrimary) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "DEFAULT", color = ForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${motorcycle.year} • ${motorcycle.engineCc} cc ${if (motorcycle.variant != null) "• ${motorcycle.variant}" else ""}",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // --- BIKE STATISTICS SECTION ---
            item {
                Text(
                    text = "BIKE STATISTICS",
                    color = SafetyAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                if (isLoading && stats == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SafetyAmber)
                    }
                } else {
                    val currentStats = stats ?: MotorcycleStatsResponse(
                        motorcycleId = motorcycle.id,
                        motorcycleName = motorcycle.displayName,
                        totalDistanceKm = 0.0,
                        totalDistanceMeters = 0.0,
                        totalRides = 0,
                        totalRidingTimeSeconds = 0,
                        avgDistanceKm = 0.0,
                        longestRideKm = 0.0,
                        longestRideId = null,
                        longestRideTitle = null,
                        avgSpeedKmh = 0.0
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatBox(
                                label = "Total Distance",
                                value = "${currentStats.totalDistanceKm} km",
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "Total Rides",
                                value = "${currentStats.totalRides}",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatBox(
                                label = "Riding Time",
                                value = formatDuration(currentStats.totalRidingTimeSeconds),
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "Average Speed",
                                value = "${currentStats.avgSpeedKmh} km/h",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatBox(
                                label = "Avg Distance / Ride",
                                value = "${currentStats.avgDistanceKm} km",
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "Longest Ride",
                                value = "${currentStats.longestRideKm} km",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Monthly breakdown card if present
                        if (currentStats.monthlyDistance.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(text = "Monthly Distance", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    currentStats.monthlyDistance.entries.toList().takeLast(6).forEach { entry ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = entry.key, color = TextSecondary, fontSize = 12.sp)
                                            Text(text = "${entry.value} km", color = SafetyAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- MOTORCYCLE-SPECIFIC RIDING HISTORY SECTION ---
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RIDING HISTORY (${rides.size})",
                        color = SafetyAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            if (rides.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CarbonDark),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "No rides recorded with this bike yet.", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(rides) { ride ->
                    MotorcycleRideHistoryItem(ride = ride, onClick = { onRideClick(ride.id) })
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(text = label.uppercase(), color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MotorcycleRideHistoryItem(ride: Ride, onClick: () -> Unit) {
    val dateStr = remember(ride.startTime) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(ride.startTime))
    }
    val distStr = String.format(Locale.US, "%.1f km", ride.totalDistanceKm)
    val durationStr = remember(ride.movingTimeSeconds) {
        val hrs = ride.movingTimeSeconds / 3600
        val mins = (ride.movingTimeSeconds % 3600) / 60
        if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ride.title.ifBlank { "Motorcycle Ride" },
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateStr,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = distStr, color = SafetyAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = durationStr, color = TextSecondary, fontSize = 12.sp)
                    Text(text = "${ride.avgSpeedKmh} km/h avg", color = TextSecondary, fontSize = 12.sp)
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

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
}
