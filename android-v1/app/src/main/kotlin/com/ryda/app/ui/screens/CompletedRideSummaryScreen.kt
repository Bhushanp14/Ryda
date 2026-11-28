package com.ryda.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.Motorcycle
import com.ryda.app.core.models.Ride
import com.ryda.app.core.utils.GpxExporter
import com.ryda.app.ui.components.RouteMapCanvas
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CompletedRideSummaryScreen(
    ride: Ride,
    points: List<GpsPoint> = emptyList(),
    motorcycle: Motorcycle? = null,
    onClose: () -> Unit,
    onViewDetail: (String) -> Unit,
    onOpenShareStudio: (String) -> Unit,
    onPhotoUpdated: (String, String) -> Unit = { _, _ -> },
    onPrivacyToggled: (String, Boolean) -> Unit = { _, _ -> },
    onPublishToCommunity: (Ride) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isPrivate by remember { mutableStateOf(ride.isPrivate) }
    var selectedPhotoUri by remember { mutableStateOf(ride.photoUri) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPublished by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(true) }
    val celebrationIndex = remember(ride.id) { (ride.id.hashCode() and 0x7FFFFFFF) % 3 }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val uriStr = it.toString()
            selectedPhotoUri = uriStr
            onPhotoUpdated(ride.id, uriStr)
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    } catch (_: Exception) { null }
                }
                previewBitmap = bmp
            }
        }
    }

    val legacyPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val uriStr = it.toString()
            selectedPhotoUri = uriStr
            onPhotoUpdated(ride.id, uriStr)
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    } catch (_: Exception) { null }
                }
                previewBitmap = bmp
            }
        }
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

    val dateFormatted = remember(ride.startTime) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        sdf.format(Date(ride.startTime))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
    ) {
        // Sticky Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsphaltBlack.copy(alpha = 0.95f))
                .border(BorderStroke(0.5.dp, BorderSubtle))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Ride Summary",
                        style = Typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isPrivate) "Saved to Private History • $dateFormatted" else "Public History • $dateFormatted",
                        style = Typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Share Ride button (redirects to Share Studio)
            Button(
                onClick = { onOpenShareStudio(ride.id) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = TextPrimary),
                border = BorderStroke(1.dp, BorderSubtle),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Ride",
                    tint = MotoOrange,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Share",
                    style = Typography.labelSmall,
                    color = TextPrimary
                )
            }
        }

        // Scrollable Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // High-Impact Route Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, BorderMedium, RoundedCornerShape(16.dp))
                    .background(Color(0xFF14171E))
            ) {
                if (points.isNotEmpty()) {
                    RouteMapCanvas(
                        points = points,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Route map recorded and archived",
                            style = Typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // GPS Confidence & Telemetry Pills Overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(ForestGreen))
                            Text(
                                text = "GPS FIX LOCKED (${points.size} pts)",
                                style = Typography.labelSmall.copy(fontFamily = StatFontFamily),
                                color = TextPrimary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "VERIFIED LOG",
                            style = Typography.labelSmall.copy(fontFamily = StatFontFamily),
                            color = ElectricCyan
                        )
                    }
                }
            }

            // Post-Ride Safety Reminder (Phase 8)
            if (ride.maxSpeedKmh >= 110.0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonDark),
                    border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Ride Safety Reminder", color = SafetyAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("High speed telemetry detected (Max: ${ride.maxSpeedKmh.toInt()} km/h). Always ride within your limits and obey road safety laws.", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Core Telemetry & Ride Identity Block
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ride.title.ifBlank { "Solo Ride" },
                                style = Typography.headlineMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Recorded via ${motorcycle?.displayName ?: "Motorcycle"}",
                                style = Typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ForestGreen.copy(alpha = 0.15f))
                                .border(1.dp, ForestGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(12.dp))
                                Text("Verified", style = Typography.labelSmall, color = ForestGreen)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4 Dominant Telemetry Stats (2x2 Grid)
                    val effectiveMoving = if (ride.movingTimeSeconds > 0) ride.movingTimeSeconds else ride.elapsedTimeSeconds
                    val effectiveAvgSpeed = if (ride.avgSpeedKmh > 0) ride.avgSpeedKmh else if (ride.totalDistanceMeters > 0 && effectiveMoving > 0) (ride.totalDistanceMeters / effectiveMoving) * 3.6 else 0.0

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Total Distance
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AsphaltBlack)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("TOTAL DISTANCE", style = Typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = String.format("%.1f", ride.totalDistanceKm),
                                        style = Typography.displayMedium.copy(fontFamily = StatFontFamily),
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("KM", style = Typography.labelSmall.copy(fontFamily = HeadlineFontFamily), color = MotoOrange)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (ride.elevationGainMeters > 0) "+${ride.elevationGainMeters.toInt()}m elev" else "Flat cruise",
                                    style = Typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Moving Duration
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AsphaltBlack)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("MOVING TIME", style = Typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatDuration(effectiveMoving),
                                    style = Typography.displayMedium.copy(fontFamily = StatFontFamily),
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Elapsed ${formatDuration(ride.elapsedTimeSeconds)}",
                                    style = Typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Average Speed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AsphaltBlack)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("AVERAGE PACE", style = Typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = String.format("%.1f", effectiveAvgSpeed),
                                        style = Typography.displayMedium.copy(fontFamily = StatFontFamily),
                                        color = ElectricCyan
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("KM/H", style = Typography.labelSmall.copy(fontFamily = HeadlineFontFamily), color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Optimal rhythm", style = Typography.bodySmall, color = ForestGreen)
                            }
                        }

                        // Max Speed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AsphaltBlack)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("MAX RECORDED", style = Typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = String.format("%.0f", ride.maxSpeedKmh),
                                        style = Typography.displayMedium.copy(fontFamily = StatFontFamily),
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("KM/H", style = Typography.labelSmall.copy(fontFamily = HeadlineFontFamily), color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Speed verified", style = Typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            // Photo Attachment Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RIDE MEMORY PHOTO",
                        style = Typography.labelSmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Ride Photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { pickPhoto() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Change Photo", style = Typography.labelMedium)
                        }
                    } else if (!selectedPhotoUri.isNullOrBlank()) {
                        AsyncImage(
                            model = selectedPhotoUri,
                            contentDescription = "Ride Photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { pickPhoto() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Change Photo", style = Typography.labelMedium)
                        }
                    } else {
                        Button(
                            onClick = { pickPhoto() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = TextPrimary),
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MotoOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Photo to Ride", style = Typography.labelMedium)
                        }
                    }
                }
            }

            // Privacy & Community Sharing Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Public,
                                contentDescription = null,
                                tint = if (isPrivate) TextSecondary else MotoOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = if (isPrivate) "Private Ride (Only You)" else "Public (Share with Riders)",
                                    style = Typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isPrivate) "Keep this ride visible only in your garage" else "Allow community feed discovery",
                                    style = Typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = !isPrivate,
                            onCheckedChange = { checked ->
                                isPrivate = !checked
                                onPrivacyToggled(ride.id, isPrivate)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MotoOrange,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = SurfaceCard
                            )
                        )
                    }

                    if (!isPrivate) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                isPublished = true
                                onPublishToCommunity(ride)
                                Toast.makeText(context, "Published to Community Feed!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isPublished) ForestGreen else MotoOrange),
                            enabled = !isPublished
                        ) {
                            Icon(
                                imageVector = if (isPublished) Icons.Default.Check else Icons.Default.Share,
                                contentDescription = null,
                                tint = AsphaltBlack
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPublished) "PUBLISHED TO FEED" else "PUBLISH TO COMMUNITY FEED",
                                style = Typography.labelLarge,
                                color = AsphaltBlack,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Share Studio Button
                OutlinedButton(
                    onClick = { onOpenShareStudio(ride.id) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MotoOrange.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MotoOrange)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OPEN IN SHARE STUDIO", style = Typography.labelLarge, fontWeight = FontWeight.Bold)
                }

                // Done & View Detail
                Button(
                    onClick = { onViewDetail(ride.id) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MotoOrange, contentColor = AsphaltBlack)
                ) {
                    Text("VIEW FULL RIDE DETAILS", style = Typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showCelebration) {
            RideCompletionCelebrationDialog(
                conceptIndex = celebrationIndex,
                onDismiss = { showCelebration = false }
            )
        }
    }
}

@Composable
private fun RideCompletionCelebrationDialog(
    conceptIndex: Int,
    onDismiss: () -> Unit
) {
    val (title, subtitle, badgeIcon) = when (conceptIndex) {
        0 -> Triple("FIST-PUMP VICTORY! 👊", "Awesome Ride! Fist pump for knocking out another epic route.", Icons.Default.Verified)
        1 -> Triple("THUMBS-UP CHAMPION! 👍", "Great Job! Keep pushing those miles and exploring.", Icons.Default.Check)
        else -> Triple("RIDE COMPLETE LEGEND! 🏆", "Victory! Ride completed and logged to your profile.", Icons.Default.TwoWheeler)
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonDark),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row with Close 'X' Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                            .border(1.dp, BorderSubtle, CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Glowing Victory Badge Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SafetyAmber.copy(alpha = 0.18f))
                        .border(2.dp, SafetyAmber, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = SafetyAmber,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = title,
                    color = SafetyAmber,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = subtitle,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Rounded Primary Action Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Awesome! View Telemetry",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
