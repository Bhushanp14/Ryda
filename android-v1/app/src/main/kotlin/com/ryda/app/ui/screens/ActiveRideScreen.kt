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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.core.models.Motorcycle
import com.ryda.app.core.models.TrackingState
import com.ryda.app.ui.components.RydaMetricCard
import com.ryda.app.ui.theme.AsphaltBlack
import com.ryda.app.ui.theme.BorderSubtle
import com.ryda.app.ui.theme.CarbonDark
import com.ryda.app.ui.theme.CrimsonRed
import com.ryda.app.ui.theme.ElectricCyan
import com.ryda.app.ui.theme.HighVisLime
import com.ryda.app.ui.theme.SafetyAmber
import com.ryda.app.ui.theme.SurfaceCard
import com.ryda.app.ui.theme.SurfaceElevated
import com.ryda.app.ui.theme.TextPrimary
import com.ryda.app.ui.theme.TextSecondary

@Composable
fun ActiveRideScreen(
    trackingState: TrackingState,
    primaryMotorcycle: Motorcycle?,
    motorcycles: List<Motorcycle> = emptyList(),
    onStartTracking: (isSimulation: Boolean, motorcycleId: String?) -> Unit,
    onPauseTracking: () -> Unit,
    onResumeTracking: () -> Unit,
    onFinishTracking: () -> Unit,
    onDiscardTracking: () -> Unit,
    onCloseHud: () -> Unit
) {
    var showFinishDialog by remember { mutableStateOf(false) }
    var showShortRideDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showBikeSelectDialog by remember { mutableStateOf(false) }
    var simulationToggled by remember { mutableStateOf(false) }

    var selectedBikeId by remember(primaryMotorcycle, motorcycles) {
        mutableStateOf(primaryMotorcycle?.id ?: motorcycles.firstOrNull()?.id)
    }
    val selectedBike = remember(selectedBikeId, motorcycles, primaryMotorcycle) {
        motorcycles.firstOrNull { it.id == selectedBikeId } ?: primaryMotorcycle
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !trackingState.isTracking -> TextSecondary
                                trackingState.isPaused -> SafetyAmber
                                else -> HighVisLime
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        !trackingState.isTracking -> "READY TO RECORD"
                        trackingState.isPaused -> "RECORDING PAUSED"
                        trackingState.isSimulationMode -> "SIMULATION ACTIVE"
                        else -> "LIVE GPS TRACKING"
                    },
                    color = when {
                        !trackingState.isTracking -> TextSecondary
                        trackingState.isPaused -> SafetyAmber
                        else -> HighVisLime
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            IconButton(onClick = onCloseHud) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Minimize HUD",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Prominent Speedometer HUD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CURRENT SPEED",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${trackingState.currentSpeedKmh.toInt()}",
                        color = SafetyAmber,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = " km/h",
                        color = TextSecondary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // GPS Accuracy status
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (trackingState.isSimulationMode) "GPS: SIMULATED (EXCELLENT)"
                               else "GPS ACCURACY: ±${String.format("%.1f", trackingState.gpsAccuracyMeters)} m",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Distance & Moving Time (Big Metrics)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RydaMetricCard(
                label = "Distance",
                value = String.format("%.2f", trackingState.totalDistanceKm),
                unit = "km",
                valueColor = ElectricCyan,
                modifier = Modifier.weight(1f)
            )
            RydaMetricCard(
                label = "Moving Time",
                value = trackingState.formattedMovingTime,
                valueColor = HighVisLime,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Avg Speed & Max Speed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RydaMetricCard(
                label = "Avg Speed",
                value = "${trackingState.averageSpeedKmh.toInt()}",
                unit = "km/h",
                modifier = Modifier.weight(1f)
            )
            RydaMetricCard(
                label = "Max Speed",
                value = "${trackingState.maxSpeedKmh.toInt()}",
                unit = "km/h",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // External Navigation notice
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "BACKGROUND RECORDING",
                    color = SafetyAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You can switch to Google Maps, Mappls, or lock your phone. RYDA records continuously in the background.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Pre-Ride Motorcycle Selection
        if (!trackingState.isTracking) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBikeSelectDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBike,
                            contentDescription = null,
                            tint = SafetyAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SELECTED MOTORCYCLE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = selectedBike?.displayName ?: "No bike selected",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change ▾", color = SafetyAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            // Emulator Simulation Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, RoundedCornerShape(14.dp))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Demo / Simulation Mode",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Simulate motorcycle ride for emulator testing",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = simulationToggled,
                    onCheckedChange = { simulationToggled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SafetyAmber,
                        checkedTrackColor = SafetyAmber.copy(alpha = 0.3f)
                    )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Primary HUD Control Buttons
        if (!trackingState.isTracking) {
            Button(
                onClick = { onStartTracking(simulationToggled, selectedBikeId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SafetyAmber,
                    contentColor = AsphaltBlack
                )
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (simulationToggled) "START SIMULATED RIDE" else "START RECORDING",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pause / Resume Button
                Button(
                    onClick = {
                        if (trackingState.isPaused) onResumeTracking() else onPauseTracking()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (trackingState.isPaused) HighVisLime else SafetyAmber,
                        contentColor = AsphaltBlack
                    )
                ) {
                    Icon(
                        imageVector = if (trackingState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (trackingState.isPaused) "RESUME" else "PAUSE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Finish Ride Button
                Button(
                    onClick = { showFinishDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceElevated,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = CrimsonRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FINISH",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = { showDiscardDialog = true }) {
                Text(text = "Discard this ride", color = CrimsonRed, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // Confirmation Dialogs
    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Finish Ride?", color = TextPrimary) },
            text = {
                Text(
                    "This will complete and save your ride of ${String.format("%.2f", trackingState.totalDistanceKm)} km to offline storage.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishDialog = false
                        if (trackingState.elapsedTimeSeconds < 120) {
                            showShortRideDialog = true
                        } else {
                            onFinishTracking()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack)
                ) {
                    Text("Save & Finish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Keep Riding", color = TextSecondary)
                }
            },
            containerColor = CarbonDark
        )
    }

    if (showShortRideDialog) {
        com.ryda.app.ui.components.ShortRideDialog(
            elapsedTimeSeconds = trackingState.elapsedTimeSeconds,
            distanceMeters = trackingState.totalDistanceMeters,
            onContinueRide = {
                showShortRideDialog = false
                if (trackingState.isPaused) {
                    onResumeTracking()
                }
            },
            onDiscardRide = {
                showShortRideDialog = false
                onDiscardTracking()
            },
            onSaveAnyway = {
                showShortRideDialog = false
                onFinishTracking()
            },
            onDismissRequest = { showShortRideDialog = false }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Ride?", color = CrimsonRed) },
            text = {
                Text(
                    "Are you sure you want to discard this ride? All recorded GPS points for this session will be permanently deleted.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onDiscardTracking()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed, contentColor = Color.White)
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CarbonDark
        )
    }

    if (showBikeSelectDialog) {
        AlertDialog(
            onDismissRequest = { showBikeSelectDialog = false },
            title = { Text("Select Motorcycle for Ride", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    motorcycles.forEach { bike ->
                        val isSelected = bike.id == selectedBikeId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) SurfaceElevated else SurfaceCard)
                                .border(1.dp, if (isSelected) SafetyAmber else BorderSubtle, RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedBikeId = bike.id
                                    showBikeSelectDialog = false
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = bike.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(text = "${bike.year} • ${bike.engineCc} cc", color = TextSecondary, fontSize = 11.sp)
                            }
                            if (isSelected) {
                                Text("DEFAULT ✓", color = SafetyAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBikeSelectDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = CarbonDark
        )
    }
}
