package com.ryda.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.Motorcycle
import com.ryda.app.core.models.Ride
import com.ryda.app.core.sharing.*
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideShareStudioScreen(
    ride: Ride,
    points: List<GpsPoint>,
    motorcycle: Motorcycle?,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(ShareConfig()) }
    var selectedPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    var exportedUri by remember { mutableStateOf<Uri?>(null) }
    var showStatsOptions by remember { mutableStateOf(false) }

    // Auto-dismiss export message after 3 seconds
    LaunchedEffect(exportMessage) {
        if (exportMessage != null) {
            delay(3000)
            exportMessage = null
        }
    }

    // Photo picker launcher (single modern picker with fallback)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bmp != null) {
                    selectedPhotoBitmap = bmp
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
    }

    val legacyPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bmp != null) {
                    selectedPhotoBitmap = bmp
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
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

    fun doExport(saveToGallery: Boolean) {
        scope.launch {
            isExporting = true
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    when (config.format) {
                        ShareFormat.TRANSPARENT_STICKER ->
                            RideShareRenderer.renderTransparentSticker(ride, points, motorcycle, config)
                        ShareFormat.MAP_CARD ->
                            RideShareRenderer.renderMapCard(ride, points, motorcycle, config)
                        ShareFormat.PHOTO_CARD -> {
                            val photo = selectedPhotoBitmap
                            if (photo != null) {
                                RideShareRenderer.renderPhotoCard(ride, points, motorcycle, photo, config)
                            } else {
                                RideShareRenderer.renderMapCard(ride, points, motorcycle, config.copy(format = ShareFormat.MAP_CARD))
                            }
                        }
                    }
                }

                val isTransparent = config.format == ShareFormat.TRANSPARENT_STICKER
                val ext = if (isTransparent) "png" else "jpg"
                val filename = "ryda_ride_${ride.id.take(8)}_${System.currentTimeMillis()}.$ext"

                val uri = if (saveToGallery) {
                    ShareHelper.saveBitmapToGallery(context, bitmap, filename, isTransparent)
                } else {
                    ShareHelper.saveBitmapToCache(context, bitmap, filename, isTransparent)
                }

                if (uri != null) {
                    exportedUri = uri
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (saveToGallery) {
                        exportMessage = "✓ Saved to Photos / RYDA"
                    } else {
                        showShareSheet = true
                    }
                } else {
                    exportMessage = "Export failed. Please check permissions."
                }
            } catch (e: Exception) {
                exportMessage = "Export error: ${e.message}"
            } finally {
                isExporting = false
            }
        }
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
                    Text(
                        text = "Ride Sharing Studio",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Format tabs: Sticker | Map Card | Photo Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormatTabChip(
                        label = "Transparent Sticker",
                        selected = config.format == ShareFormat.TRANSPARENT_STICKER,
                        onClick = {
                            config = config.copy(format = ShareFormat.TRANSPARENT_STICKER)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FormatTabChip(
                        label = "Map Card",
                        selected = config.format == ShareFormat.MAP_CARD,
                        onClick = {
                            config = config.copy(format = ShareFormat.MAP_CARD)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FormatTabChip(
                        label = "Photo Card",
                        selected = config.format == ShareFormat.PHOTO_CARD,
                        onClick = {
                            config = config.copy(format = ShareFormat.PHOTO_CARD)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        bottomBar = {
            // Action bar: Save to Gallery & Share
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CarbonDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                if (isExporting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = MotoOrange,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Rendering high-res ride image...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { doExport(saveToGallery = true) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ElectricCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save to Gallery", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { doExport(saveToGallery = false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MotoOrange,
                                contentColor = AsphaltBlack
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Photo picker button if in Photo Card format
            if (config.format == ShareFormat.PHOTO_CARD) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedPhotoBitmap != null) "Ride photo selected" else "Choose a ride photo",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedButton(
                        onClick = { pickPhoto() },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MotoOrange),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MotoOrange),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedPhotoBitmap != null) "Change Photo" else "Pick Photo", fontSize = 12.sp)
                    }
                }
            }

            // Live Canvas Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
            ) {
                LiveStudioPreview(
                    ride = ride,
                    points = points,
                    motorcycle = motorcycle,
                    config = config,
                    photoBitmap = selectedPhotoBitmap,
                    onPickPhoto = { pickPhoto() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Format-specific templates
            Text(
                text = when (config.format) {
                    ShareFormat.TRANSPARENT_STICKER -> "STICKER LAYOUT"
                    ShareFormat.MAP_CARD -> "MAP STYLE"
                    ShareFormat.PHOTO_CARD -> "OVERLAY TEXT CONTRAST"
                },
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            when (config.format) {
                ShareFormat.TRANSPARENT_STICKER -> {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            TemplateThumbCard(
                                label = "Compact Corner",
                                selected = config.layout == StickerLayout.COMPACT_CORNER,
                                onClick = {
                                    config = config.copy(layout = StickerLayout.COMPACT_CORNER)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                        }
                        item {
                            TemplateThumbCard(
                                label = "Full Vertical",
                                selected = config.layout == StickerLayout.FULL_VERTICAL,
                                onClick = {
                                    config = config.copy(layout = StickerLayout.FULL_VERTICAL)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                        }
                        item {
                            TemplateThumbCard(
                                label = "Horizontal Strip",
                                selected = config.layout == StickerLayout.HORIZONTAL_STRIP,
                                onClick = {
                                    config = config.copy(layout = StickerLayout.HORIZONTAL_STRIP)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                        }
                    }

                    // Text contrast option for sticker (light on dark vs dark on light)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Sticker text tone:", color = TextSecondary, fontSize = 12.sp)
                        FilterChip(
                            selected = config.textStyle == TextStyle.LIGHT_ON_DARK,
                            onClick = { config = config.copy(textStyle = TextStyle.LIGHT_ON_DARK) },
                            label = { Text("White (for dark photos)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MotoOrange,
                                selectedLabelColor = AsphaltBlack
                            )
                        )
                        FilterChip(
                            selected = config.textStyle == TextStyle.DARK_ON_LIGHT,
                            onClick = { config = config.copy(textStyle = TextStyle.DARK_ON_LIGHT) },
                            label = { Text("Dark", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MotoOrange,
                                selectedLabelColor = AsphaltBlack
                            )
                        )
                    }
                }

                ShareFormat.MAP_CARD -> {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            TemplateThumbCard(
                                label = "RYDA Branded",
                                selected = config.mapCardStyle == MapCardStyle.RYDA_BRANDED,
                                onClick = {
                                    config = config.copy(mapCardStyle = MapCardStyle.RYDA_BRANDED)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                        }
                        item {
                            TemplateThumbCard(
                                label = "Dark Map",
                                selected = config.mapCardStyle == MapCardStyle.DARK,
                                onClick = {
                                    config = config.copy(mapCardStyle = MapCardStyle.DARK)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                        }
                        item {
                            TemplateThumbCard(
                                label = "Light Map",
                                selected = config.mapCardStyle == MapCardStyle.LIGHT,
                                onClick = {
                                    config = config.copy(mapCardStyle = MapCardStyle.LIGHT)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                        }
                        item {
                            TemplateThumbCard(
                                label = "Minimal Route",
                                selected = config.mapCardStyle == MapCardStyle.MINIMAL_ROUTE,
                                onClick = {
                                    config = config.copy(mapCardStyle = MapCardStyle.MINIMAL_ROUTE)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                        }
                    }
                }

                ShareFormat.PHOTO_CARD -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = config.textStyle == TextStyle.LIGHT_ON_DARK,
                            onClick = { config = config.copy(textStyle = TextStyle.LIGHT_ON_DARK) },
                            label = { Text("Light Text", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MotoOrange,
                                selectedLabelColor = AsphaltBlack
                            )
                        )
                        FilterChip(
                            selected = config.textStyle == TextStyle.DARK_ON_LIGHT,
                            onClick = { config = config.copy(textStyle = TextStyle.DARK_ON_LIGHT) },
                            label = { Text("Dark Text", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MotoOrange,
                                selectedLabelColor = AsphaltBlack
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Aspect Ratio / Export Size
            Text(
                text = "EXPORT SIZE",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExportSizeChip(
                    label = "Story 9:16",
                    sub = "1080×1920",
                    selected = config.exportSize == ExportSize.STORY,
                    onClick = { config = config.copy(exportSize = ExportSize.STORY) },
                    modifier = Modifier.weight(1f)
                )
                ExportSizeChip(
                    label = "Feed 4:5",
                    sub = "1080×1350",
                    selected = config.exportSize == ExportSize.FEED_PORTRAIT,
                    onClick = { config = config.copy(exportSize = ExportSize.FEED_PORTRAIT) },
                    modifier = Modifier.weight(1f)
                )
                ExportSizeChip(
                    label = "Square 1:1",
                    sub = "1080×1080",
                    selected = config.exportSize == ExportSize.SQUARE,
                    onClick = { config = config.copy(exportSize = ExportSize.SQUARE) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Toggles Section (Collapsible)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showStatsOptions = !showStatsOptions },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MotoOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Stats & Privacy Options",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = if (showStatsOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }

                    AnimatedVisibility(visible = showStatsOptions) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            StatToggleRow("Distance", config.showDistance) { config = config.copy(showDistance = it) }
                            StatToggleRow("Moving Time", config.showMovingTime) { config = config.copy(showMovingTime = it) }
                            StatToggleRow("Average Speed", config.showAvgSpeed) { config = config.copy(showAvgSpeed = it) }
                            StatToggleRow("Max Speed", config.showMaxSpeed) { config = config.copy(showMaxSpeed = it) }
                            StatToggleRow("Elevation Gain", config.showElevation) { config = config.copy(showElevation = it) }
                            StatToggleRow("Motorcycle Model", config.showMotorcycle) { config = config.copy(showMotorcycle = it) }
                            StatToggleRow("Ride Date", config.showDate) { config = config.copy(showDate = it) }

                            Divider(
                                color = BorderSubtle,
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            // Privacy toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Trim Route Ends (Privacy)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Hides the first & last ~200m of your route so home or work locations remain private.", color = TextSecondary, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = config.trimRouteEnds,
                                    onCheckedChange = { config = config.copy(trimRouteEnds = it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MotoOrange,
                                        checkedTrackColor = MotoOrange.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Floating export message snackbar
        if (exportMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonDark),
                    border = BorderStroke(1.dp, MotoOrange)
                ) {
                    Text(
                        text = exportMessage!!,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    // Share Options Bottom Sheet
    if (showShareSheet && exportedUri != null) {
        val uri = exportedUri!!
        ModalBottomSheet(
            onDismissRequest = { showShareSheet = false },
            containerColor = CarbonDark,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Share Ride Graphic",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (ShareHelper.hasInstagramInstalled(context)) {
                    ShareTargetRow(
                        title = "Instagram Stories",
                        subtitle = if (config.format == ShareFormat.TRANSPARENT_STICKER)
                            "Opens directly as sticker overlay in Instagram Stories"
                        else "Shares graphic directly to Instagram Stories",
                        icon = Icons.Default.CameraAlt,
                        onClick = {
                            showShareSheet = false
                            ShareHelper.shareToInstagramStory(context, uri)
                        }
                    )
                }

                ShareTargetRow(
                    title = "Instagram Feed / Post",
                    subtitle = "Share image to Instagram Feed or Direct Message",
                    icon = Icons.Default.PhotoLibrary,
                    onClick = {
                        showShareSheet = false
                        ShareHelper.shareImage(context, uri, if (config.format == ShareFormat.TRANSPARENT_STICKER) "image/png" else "image/jpeg")
                    }
                )

                ShareTargetRow(
                    title = "WhatsApp",
                    subtitle = "Share with your riding group or status",
                    icon = Icons.Default.Chat,
                    onClick = {
                        showShareSheet = false
                        ShareHelper.shareImage(context, uri, if (config.format == ShareFormat.TRANSPARENT_STICKER) "image/png" else "image/jpeg")
                    }
                )

                ShareTargetRow(
                    title = "More Apps...",
                    subtitle = "Open Android system share sheet",
                    icon = Icons.Default.Share,
                    onClick = {
                        showShareSheet = false
                        ShareHelper.shareImage(context, uri, if (config.format == ShareFormat.TRANSPARENT_STICKER) "image/png" else "image/jpeg")
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun LiveStudioPreview(
    ride: Ride,
    points: List<GpsPoint>,
    motorcycle: Motorcycle?,
    config: ShareConfig,
    photoBitmap: Bitmap?,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preparedPoints = remember(points, config.trimRouteEnds) {
        val pts = if (config.trimRouteEnds && points.size > 10) {
            RouteSimplifier.trimRouteEnds(points, config.trimMeters)
        } else points
        RouteSimplifier.simplifyRoute(pts, 0.0002)
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            when (config.format) {
                ShareFormat.TRANSPARENT_STICKER -> {
                    // Draw checkerboard background to emphasize transparency
                    val squareSize = 24f
                    var y = 0f
                    var row = 0
                    while (y < h) {
                        var x = 0f
                        var col = 0
                        while (x < w) {
                            val color = if ((row + col) % 2 == 0) Color(0xFF1E1E24) else Color(0xFF141418)
                            drawRect(color, Offset(x, y), Size(squareSize, squareSize))
                            x += squareSize
                            col++
                        }
                        y += squareSize
                        row++
                    }

                    // Route polyline in white/dark
                    if (preparedPoints.size >= 2) {
                        val box = RouteSimplifier.computeBoundingBox(preparedPoints)
                        val routeH = when (config.layout) {
                            StickerLayout.COMPACT_CORNER, StickerLayout.HORIZONTAL_STRIP -> h * 0.62f
                            StickerLayout.FULL_VERTICAL -> h * 0.88f
                        }
                        val offsets = RouteSimplifier.projectToCanvas(preparedPoints, box, w, routeH, w * 0.1f)
                        val routeColor = if (config.textStyle == TextStyle.LIGHT_ON_DARK) Color.White else Color(0xFF101114)
                        drawRoutePolyline(this, offsets, routeColor, 4.5f)
                    }
                }

                ShareFormat.MAP_CARD -> {
                    // Background
                    val bgColor = when (config.mapCardStyle) {
                        MapCardStyle.DARK -> Color(0xFF101114)
                        MapCardStyle.LIGHT -> Color(0xFFF2F2F7)
                        MapCardStyle.MINIMAL_ROUTE -> Color(0xFF0A0A0A)
                        MapCardStyle.RYDA_BRANDED -> Color(0xFF141424)
                    }
                    drawRect(bgColor)

                    // Subtle grid
                    if (config.mapCardStyle != MapCardStyle.MINIMAL_ROUTE) {
                        val gridColor = if (config.mapCardStyle == MapCardStyle.LIGHT) Color(0xFFDCDCE5) else Color(0xFF22222E)
                        var gx = 0f
                        while (gx < w) {
                            drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                            gx += 40f
                        }
                        var gy = 0f
                        while (gy < h) {
                            drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                            gy += 40f
                        }
                    }

                    // Route
                    if (preparedPoints.size >= 2) {
                        val box = RouteSimplifier.computeBoundingBox(preparedPoints)
                        val offsets = RouteSimplifier.projectToCanvas(preparedPoints, box, w, h * 0.7f, w * 0.12f)
                        val routeColor = when (config.mapCardStyle) {
                            MapCardStyle.DARK, MapCardStyle.RYDA_BRANDED -> MotoOrange
                            MapCardStyle.LIGHT -> Color(0xFF101114)
                            MapCardStyle.MINIMAL_ROUTE -> Color.White
                        }
                        if (config.mapCardStyle == MapCardStyle.RYDA_BRANDED) {
                            drawRoutePolyline(this, offsets, MotoOrange.copy(alpha = 0.35f), 10f)
                        }
                        drawRoutePolyline(this, offsets, routeColor, 4.5f)

                        // Start / Finish markers
                        if (offsets.isNotEmpty()) {
                            drawCircle(Color(0xFF4CAF50), radius = 7f, center = offsets.first())
                            drawCircle(Color.White, radius = 7f, center = offsets.first(), style = Stroke(2f))
                            drawCircle(MotoOrange, radius = 7f, center = offsets.last())
                            drawCircle(Color.White, radius = 7f, center = offsets.last(), style = Stroke(2f))
                        }
                    }
                }

                ShareFormat.PHOTO_CARD -> {
                    if (photoBitmap != null) {
                        // User's photo rendered in preview
                        val imageBitmap = photoBitmap.asImageBitmap()
                        drawImage(
                            image = imageBitmap,
                            dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt())
                        )
                        // Scrim
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC000000)),
                                startY = h * 0.5f,
                                endY = h
                            )
                        )
                        // Route in top right
                        if (preparedPoints.size >= 2) {
                            val box = RouteSimplifier.computeBoundingBox(preparedPoints)
                            val offsets = RouteSimplifier.projectToCanvas(preparedPoints, box, w * 0.45f, h * 0.35f, 20f)
                            val shiftedOffsets = offsets.map { Offset(it.x + w * 0.5f, it.y + 20f) }
                            drawRoutePolyline(this, shiftedOffsets, Color.White, 3.5f)
                        }
                    } else {
                        // Placeholder
                        drawRect(Color(0xFF1B1B22))
                    }
                }
            }
        }

        // Overlay text / stats widgets on top of Canvas
        if (config.format == ShareFormat.PHOTO_CARD && photoBitmap == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onPickPhoto() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MotoOrange,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to choose a ride photo",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Route & stats will overlay on your photo",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        } else {
            // Stats preview badge
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // RYDA watermark top-right
                Text(
                    text = "RYDA",
                    color = if (config.format == ShareFormat.MAP_CARD && config.mapCardStyle == MapCardStyle.LIGHT)
                        Color(0xFF101114).copy(alpha = 0.6f)
                    else Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.TopEnd)
                )

                // Stats summary container bottom
                Card(
                    modifier = Modifier.align(Alignment.BottomStart),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (config.format == ShareFormat.MAP_CARD && config.mapCardStyle == MapCardStyle.LIGHT)
                            Color(0xCCFFFFFF)
                        else Color(0xCC101114)
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (config.showDistance) {
                                Column {
                                    Text(
                                        text = "${String.format("%.1f", ride.totalDistanceKm)} km",
                                        color = MotoOrange,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("DIST", color = TextSecondary, fontSize = 9.sp)
                                }
                            }
                            if (config.showMovingTime) {
                                Column {
                                    Text(
                                        text = ride.formattedDuration(ride.movingTimeSeconds),
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("TIME", color = TextSecondary, fontSize = 9.sp)
                                }
                            }
                            if (config.showAvgSpeed) {
                                Column {
                                    val avgSpeed = if (ride.avgSpeedKmh > 0) ride.avgSpeedKmh else if (ride.movingTimeSeconds > 0) (ride.totalDistanceMeters / ride.movingTimeSeconds) * 3.6 else 0.0
                                    Text(
                                        text = "${String.format("%.0f", avgSpeed)} km/h",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("AVG", color = TextSecondary, fontSize = 9.sp)
                                }
                            }
                            if (config.showMaxSpeed) {
                                Column {
                                    val topSpeed = if (ride.maxSpeedKmh > 0) ride.maxSpeedKmh else points.maxOfOrNull { it.speedKmh } ?: 0.0
                                    Text(
                                        text = "${String.format("%.0f", topSpeed)} km/h",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("TOP", color = TextSecondary, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun drawRoutePolyline(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    offsets: List<Offset>,
    color: Color,
    strokeWidth: Float
) {
    if (offsets.size < 2) return
    val path = Path().apply {
        moveTo(offsets[0].x, offsets[0].y)
        offsets.drop(1).forEach { lineTo(it.x, it.y) }
    }
    scope.drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    )
}

@Composable
private fun FormatTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MotoOrange else SurfaceCard)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) AsphaltBlack else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun TemplateThumbCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(100.dp)
            .height(54.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark),
        border = BorderStroke(1.5.dp, if (selected) MotoOrange else BorderSubtle)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) MotoOrange else TextPrimary,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ExportSizeChip(
    label: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MotoOrange.copy(alpha = 0.15f) else CarbonDark
        ),
        border = BorderStroke(1.5.dp, if (selected) MotoOrange else BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = if (selected) MotoOrange else TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = sub,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun StatToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextPrimary, fontSize = 13.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MotoOrange,
                checkedTrackColor = MotoOrange.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun ShareTargetRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MotoOrange,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
