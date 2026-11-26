package com.ryda.app.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
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
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.Motorcycle
import com.ryda.app.core.models.Ride
import com.ryda.app.core.models.SyncStatus
import com.ryda.app.core.utils.GpxExporter
import com.ryda.app.core.utils.RideAnalysisCalculator
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.components.*
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RideDetailScreen(
    ride: Ride,
    points: List<GpsPoint>,
    motorcycle: Motorcycle?,
    allMotorcycles: List<Motorcycle> = emptyList(),
    onBackClick: () -> Unit,
    onTitleUpdate: (String) -> Unit,
    onMotorcycleUpdate: (String?) -> Unit = {},
    onPrivacyUpdate: (Boolean) -> Unit = {},
    onDeleteClick: () -> Unit,
    onShareStudio: () -> Unit = {},
    onPhotoUpdate: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMotorcycleDialog by remember { mutableStateOf(false) }
    var showPublishConfirmDialog by remember { mutableStateOf(false) }
    var isRidePrivate by remember(ride.isPrivate) { mutableStateOf(ride.isPrivate) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onPhotoUpdate(it.toString()) }
    }

    val legacyPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onPhotoUpdate(it.toString()) }
    }

    fun pickPhoto() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            legacyPhotoLauncher.launch("image/*")
        }
    }
    var editedTitle by remember { mutableStateOf(ride.title) }

    var activePoints by remember(points) { mutableStateOf(points) }
    val repository = remember { RydaRepository.getInstance(context) }

    LaunchedEffect(ride.id) {
        if (activePoints.isEmpty()) {
            val loaded = repository.ensureGpsPointsLoaded(ride.id)
            if (loaded.isNotEmpty()) {
                activePoints = loaded
            }
        }
    }

    // Analysis View Tab State: 0 = Speed Graph, 1 = Distance Splits, 2 = Elevation Profile
    var selectedAnalysisTab by remember { mutableStateOf(0) }

    // Derive full ride analysis off main thread computation
    val analysis by produceState(
        initialValue = RideAnalysisCalculator.calculateAnalysis(ride, if (activePoints.size > 500) activePoints.take(500) else activePoints),
        key1 = ride,
        key2 = activePoints
    ) {
        withContext(Dispatchers.Default) {
            value = RideAnalysisCalculator.calculateAnalysis(ride, activePoints)
        }
    }

    val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
    val dateString = dateFormat.format(Date(ride.startTime))

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShareStudio) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share Ride", tint = SafetyAmber)
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonRed)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // SECTION 1: Ride Header (Title, Date, Start & Finish Time)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ride.title,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$dateString • ${analysis.timeAnalysis.formattedStartTime} – ${analysis.timeAnalysis.formattedFinishTime}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = { showEditDialog = true }) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Title", tint = SafetyAmber)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 2: Route Map & Photo
        RideRouteView(points = activePoints)

        if (!ride.photoUri.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Column {
                    RideImageThumbnail(
                        photoUri = ride.photoUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RIDE PHOTOGRAPH",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { pickPhoto() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change", color = MotoOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { onPhotoUpdate(null) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove", color = CrimsonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedCard(
                onClick = { pickPhoto() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                colors = CardDefaults.outlinedCardColors(containerColor = CarbonDark.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MotoOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MotoOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add Ride Photograph",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Attach scenic view, bike, or group picture to this ride",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "+ ADD",
                        color = MotoOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 3: Main Statistics Grid (6 Tiles)
        Text("MAIN RIDE STATISTICS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // Row 1: Distance & Moving Time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val dist = if (ride.totalDistanceKm > 0) ride.totalDistanceKm else (ride.totalDistanceMeters / 1000.0)
            RydaMetricCard(
                label = "Distance",
                value = String.format(Locale.US, "%.2f", dist),
                unit = "km",
                valueColor = SafetyAmber,
                modifier = Modifier.weight(1f)
            )
            val movingSec = if (analysis.timeAnalysis.movingTimeSeconds > 0) {
                analysis.timeAnalysis.movingTimeSeconds
            } else if (ride.movingTimeSeconds > 0) {
                ride.movingTimeSeconds
            } else {
                ride.elapsedTimeSeconds
            }
            RydaMetricCard(
                label = "Moving Time",
                value = ride.formattedDuration(movingSec),
                valueColor = HighVisLime,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2: Avg Speed & Max Speed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val avgSpd = if (analysis.speedAnalysis.averageSpeedKmh > 0) {
                analysis.speedAnalysis.averageSpeedKmh
            } else if (ride.avgSpeedKmh > 0) {
                ride.avgSpeedKmh
            } else if (ride.totalDistanceMeters > 0 && ride.movingTimeSeconds > 0) {
                (ride.totalDistanceMeters / ride.movingTimeSeconds) * 3.6
            } else 0.0

            RydaMetricCard(
                label = "Avg Speed",
                value = "${avgSpd.toInt()}",
                unit = "km/h",
                valueColor = ElectricCyan,
                modifier = Modifier.weight(1f)
            )
            val maxSpd = if (analysis.speedAnalysis.maxSpeedKmh > 0) {
                analysis.speedAnalysis.maxSpeedKmh
            } else {
                ride.maxSpeedKmh
            }
            RydaMetricCard(
                label = "Max Speed",
                value = "${maxSpd.toInt()}",
                unit = "km/h",
                valueColor = MotoOrange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 3: Elapsed Time & Stopped Time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RydaMetricCard(
                label = "Elapsed Time",
                value = ride.formattedDuration(analysis.timeAnalysis.elapsedTimeSeconds),
                unit = "total",
                modifier = Modifier.weight(1f)
            )
            RydaMetricCard(
                label = "Stopped Time",
                value = ride.formattedDuration(analysis.timeAnalysis.stoppedTimeSeconds),
                unit = "paused",
                valueColor = TextSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION 4: Elevation Summary
        Text("ELEVATION SUMMARY", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (analysis.elevationAnalysis.isElevationAvailable) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RydaMetricCard(
                    label = "Elevation Gain",
                    value = "+${analysis.elevationAnalysis.elevationGainMeters.toInt()}",
                    unit = "m",
                    valueColor = ForestGreen,
                    modifier = Modifier.weight(1f)
                )
                RydaMetricCard(
                    label = "Elevation Loss",
                    value = "-${analysis.elevationAnalysis.elevationLossMeters.toInt()}",
                    unit = "m",
                    valueColor = CrimsonRed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RydaMetricCard(
                    label = "Highest Alt",
                    value = "${analysis.elevationAnalysis.highestElevationMeters.toInt()}",
                    unit = "m",
                    valueColor = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                RydaMetricCard(
                    label = "Lowest Alt",
                    value = "${analysis.elevationAnalysis.lowestElevationMeters.toInt()}",
                    unit = "m",
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Text(
                    text = "GPS altitude quality was insufficient for elevation gain calculation.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 5: Ride Analysis Interactive Section
        Text("POST-RIDE ANALYSIS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // Segmented Switcher for Analysis View: Speed Graph | Distance Splits | Elevation Profile
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(3.dp)
        ) {
            val speedBg by animateColorAsState(if (selectedAnalysisTab == 0) MotoOrange else Color.Transparent)
            val splitsBg by animateColorAsState(if (selectedAnalysisTab == 1) MotoOrange else Color.Transparent)
            val elevBg by animateColorAsState(if (selectedAnalysisTab == 2) MotoOrange else Color.Transparent)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(speedBg)
                    .clickable { selectedAnalysisTab = 0 }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Speed Graph", color = if (selectedAnalysisTab == 0) Color.White else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(splitsBg)
                    .clickable { selectedAnalysisTab = 1 }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Splits (1 KM)", color = if (selectedAnalysisTab == 1) Color.White else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(elevBg)
                    .clickable { selectedAnalysisTab = 2 }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Elevation", color = if (selectedAnalysisTab == 2) Color.White else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedAnalysisTab) {
            0 -> SpeedOverTimeChart(analysis = analysis.speedAnalysis)
            1 -> DistanceSplitsView(splits = analysis.splits)
            2 -> ElevationProfileChart(analysis = analysis.elevationAnalysis, profilePoints = analysis.elevationProfile)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 6: Motorcycle & Sync Metadata Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "MOTORCYCLE", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showMotorcycleDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = motorcycle?.displayName ?: "Unassigned",
                            color = SafetyAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Change Motorcycle",
                            tint = SafetyAmber,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "SYNC STATUS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (ride.syncStatus == SyncStatus.SYNCED) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = if (ride.syncStatus == SyncStatus.SYNCED) ForestGreen else SafetyAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (ride.syncStatus == SyncStatus.SYNCED) "Synced to Cloud" else "Stored Offline",
                            color = if (ride.syncStatus == SyncStatus.SYNCED) ForestGreen else SafetyAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Ride Visibility & Privacy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "RIDE VISIBILITY", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isRidePrivate) "Private (Only You)" else "Public (Community)",
                            color = if (isRidePrivate) SafetyAmber else ElectricCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Switch(
                        checked = !isRidePrivate,
                        onCheckedChange = { makePublic ->
                            if (makePublic) {
                                showPublishConfirmDialog = true
                            } else {
                                isRidePrivate = true
                                onPrivacyUpdate(true)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ElectricCyan,
                            checkedTrackColor = ElectricCyan.copy(alpha = 0.3f),
                            uncheckedThumbColor = SafetyAmber,
                            uncheckedTrackColor = SafetyAmber.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // Publish Ride Confirmation Dialog
    if (showPublishConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPublishConfirmDialog = false },
            title = { Text("Make Ride Public?", color = ElectricCyan, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "By default, all RYDA rides are private. Making this ride public will allow other riders in the community to view your route and statistics. Your coordinates will be published to the cloud.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isRidePrivate = false
                        onPrivacyUpdate(false)
                        showPublishConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = AsphaltBlack)
                ) {
                    Text("Publish to Public", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPublishConfirmDialog = false }) {
                    Text("Keep Private", color = TextSecondary)
                }
            },
            containerColor = CarbonDark
        )
    }

    // Edit Title Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Ride Title", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditDialog = false
                        onTitleUpdate(editedTitle)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CarbonDark
        )
    }

    // Change Motorcycle Dialog
    if (showMotorcycleDialog) {
        AlertDialog(
            onDismissRequest = { showMotorcycleDialog = false },
            title = { Text("Assign Motorcycle", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onMotorcycleUpdate(null)
                                showMotorcycleDialog = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("None (Unassigned)", color = if (ride.motorcycleId == null) SafetyAmber else TextSecondary)
                    }

                    allMotorcycles.forEach { bike ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onMotorcycleUpdate(bike.id)
                                    showMotorcycleDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bike.displayName,
                                color = if (ride.motorcycleId == bike.id) SafetyAmber else TextPrimary,
                                fontWeight = if (ride.motorcycleId == bike.id) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMotorcycleDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CarbonDark
        )
    }

    // Delete Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Ride?", color = CrimsonRed) },
            text = {
                Text(
                    "Are you sure you want to permanently delete '${ride.title}' and all its GPS route points?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed, contentColor = Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CarbonDark
        )
    }
}
