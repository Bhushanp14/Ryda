package com.ryda.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.Motorcycle
import com.ryda.app.core.models.Ride
import com.ryda.app.core.sharing.*
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RYDA_DARK_MAP_STYLE_NO_LABELS = """
[
  { "elementType": "geometry", "stylers": [{"color": "#181b20"}] },
  { "elementType": "labels", "stylers": [{"visibility": "off"}] },
  { "elementType": "labels.icon", "stylers": [{"visibility": "off"}] },
  { "elementType": "labels.text", "stylers": [{"visibility": "off"}] },
  { "featureType": "administrative", "elementType": "geometry", "stylers": [{"color": "#282d37"}] },
  { "featureType": "poi.park", "elementType": "geometry", "stylers": [{"color": "#13231a"}] },
  { "featureType": "road", "elementType": "geometry.fill", "stylers": [{"color": "#2c323f"}] },
  { "featureType": "road", "elementType": "geometry.stroke", "stylers": [{"color": "#181b20"}] },
  { "featureType": "road.highway", "elementType": "geometry.fill", "stylers": [{"color": "#3f4759"}] },
  { "featureType": "road.highway", "elementType": "geometry.stroke", "stylers": [{"color": "#21262d"}] },
  { "featureType": "transit", "elementType": "geometry", "stylers": [{"color": "#24292f"}] },
  { "featureType": "water", "elementType": "geometry", "stylers": [{"color": "#0d1b2a"}] }
]
"""

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

    val initialStartName = remember(ride) {
        if (ride.title.isNotBlank() && !ride.title.contains("Ride", ignoreCase = true)) {
            ride.title
        } else ""
    }

    var config by remember {
        mutableStateOf(
            ShareConfig(
                startLocationName = initialStartName,
                style = GraphicStyle.TRANSPARENT
            )
        )
    }

    var startLocationText by remember { mutableStateOf(config.startLocationName) }
    var destLocationText by remember { mutableStateOf(config.destinationName) }

    val repository = remember { com.ryda.app.data.repository.RydaRepository.getInstance(context) }
    var activePoints by remember(ride.id, points) {
        mutableStateOf(if (points.isNotEmpty()) points else repository.getPointsForRide(ride.id))
    }

    LaunchedEffect(ride.id) {
        if (activePoints.isEmpty()) {
            val loaded = withContext(Dispatchers.IO) {
                repository.ensureGpsPointsLoaded(ride.id)
            }
            if (loaded.isNotEmpty()) {
                activePoints = loaded
            }
        }
    }

    var mapSnapshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.PNG) }
    var exportedUri by remember { mutableStateOf<Uri?>(null) }

    // Accent color used inside the image graphic
    val graphicAccentColor = remember(config.colorTheme) {
        Color(android.graphics.Color.parseColor(config.colorTheme.primaryHex))
    }

    // Accent color used for UI buttons outside the image container (stays visible when PLAIN_BLACK is selected)
    val uiAccentColor = remember(config.colorTheme) {
        if (config.colorTheme == ColorTheme.PLAIN_BLACK) {
            SafetyAmber
        } else {
            Color(android.graphics.Color.parseColor(config.colorTheme.primaryHex))
        }
    }

    val latLngList = remember(activePoints) { activePoints.map { LatLng(it.latitude, it.longitude) } }

    val currentTemplate = remember(config.style, config.template) {
        if (config.style == GraphicStyle.MAP_BACKGROUND) SvgLayoutTemplate.HERO_DISTANCE else config.template
    }
    val imageAspectRatio = remember(currentTemplate) {
        currentTemplate.viewBoxWidth.toFloat() / currentTemplate.viewBoxHeight.toFloat()
    }

    LaunchedEffect(config.style) {
        if (config.style == GraphicStyle.MAP_BACKGROUND) {
            mapSnapshotBitmap = null
        }
    }

    val liveBitmap = remember(ride, activePoints, config, startLocationText, destLocationText, mapSnapshotBitmap) {
        val finalConfig = config.copy(
            startLocationName = startLocationText,
            destinationName = destLocationText
        )
        RideShareRenderer.renderShareGraphic(
            ride = ride,
            points = activePoints,
            config = finalConfig,
            mapBitmap = mapSnapshotBitmap
        )
    }

    LaunchedEffect(exportMessage) {
        if (exportMessage != null) {
            delay(3000)
            exportMessage = null
        }
    }

    fun doExport(format: ExportFormat, saveToGallery: Boolean) {
        scope.launch {
            isExporting = true
            try {
                val finalConfig = config.copy(
                    startLocationName = startLocationText,
                    destinationName = destLocationText
                )

                when (format) {
                    ExportFormat.SVG -> {
                        // GENUINE TRANSPARENT SVG VECTOR PIPELINE
                        // Direct Kotlin SVG XML generation with no rasterization
                        val svgContent = withContext(Dispatchers.Default) {
                            SvgExporter.exportToSvg(
                                ride = ride,
                                points = activePoints,
                                config = finalConfig
                            )
                        }

                        val filename = "ryda_${finalConfig.template.name.lowercase()}_${ride.id.take(8)}_${System.currentTimeMillis()}.svg"
                        val uri = if (saveToGallery) {
                            ShareHelper.saveSvgToDownloads(context, svgContent, filename)
                        } else {
                            ShareHelper.saveSvgToCache(context, svgContent, filename)
                        }

                        if (uri != null) {
                            exportedUri = uri
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (saveToGallery) {
                                exportMessage = "✓ Saved SVG to Downloads / RYDA"
                            } else {
                                showShareSheet = true
                            }
                        } else {
                            exportMessage = "SVG export failed. Check storage permissions."
                        }
                    }
                    ExportFormat.PNG -> {
                        // GENUINE TRANSPARENT PNG RASTER PIPELINE
                        val isTransparent = finalConfig.style == GraphicStyle.TRANSPARENT
                        val bitmap = withContext(Dispatchers.Default) {
                            RideShareRenderer.renderShareGraphic(
                                ride = ride,
                                points = activePoints,
                                config = finalConfig,
                                mapBitmap = if (isTransparent) null else mapSnapshotBitmap
                            )
                        }

                        val stylePrefix = if (isTransparent) "transparent" else "map"
                        val filename = "ryda_${stylePrefix}_${finalConfig.template.name.lowercase()}_${ride.id.take(8)}_${System.currentTimeMillis()}.png"
                        val uri = if (saveToGallery) {
                            ShareHelper.saveBitmapToGallery(context, bitmap, filename, isTransparent = isTransparent)
                        } else {
                            ShareHelper.saveBitmapToCache(context, bitmap, filename, isTransparent = isTransparent)
                        }

                        if (uri != null) {
                            exportedUri = uri
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (saveToGallery) {
                                exportMessage = if (isTransparent) "✓ Saved Transparent PNG to Photos / RYDA" else "✓ Saved to Photos / RYDA"
                            } else {
                                showShareSheet = true
                            }
                        } else {
                            exportMessage = "PNG export failed. Check storage permissions."
                        }
                    }
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
            }
        },
        bottomBar = {
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
                            color = uiAccentColor,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Rendering high-res graphic...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                selectedFormat = ExportFormat.PNG
                                showFormatDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, uiAccentColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = uiAccentColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Save to Phone",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { doExport(format = ExportFormat.PNG, saveToGallery = false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = uiAccentColor,
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
            // Live Preview Container (Dynamically sized to match the image aspect ratio)
            val liveBgColor = remember(config.colorTheme, config.style) {
                Color(0xFF12141A)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp)
                    .aspectRatio(imageAspectRatio)
                    .heightIn(max = 380.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(liveBgColor)
                    .border(1.5.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (config.style == GraphicStyle.MAP_BACKGROUND && latLngList.size >= 2) {
                    val cameraPositionState = rememberCameraPositionState()
                    LaunchedEffect(latLngList) {
                        if (latLngList.isNotEmpty()) {
                            try {
                                val builder = LatLngBounds.builder()
                                latLngList.forEach { builder.include(it) }
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 40))
                            } catch (_: Exception) {}
                        }
                    }

                    // Live Interactive GoogleMap with Plain Roads
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            mapStyleOptions = MapStyleOptions(RYDA_DARK_MAP_STYLE_NO_LABELS)
                        ),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            compassEnabled = false,
                            mapToolbarEnabled = false,
                            myLocationButtonEnabled = false,
                            rotationGesturesEnabled = false,
                            scrollGesturesEnabled = false,
                            zoomGesturesEnabled = false
                        )
                    ) {
                        Polyline(
                            points = latLngList,
                            color = graphicAccentColor,
                            width = 6f
                        )
                        if (latLngList.isNotEmpty()) {
                            val startMarkerState = remember(latLngList) { MarkerState(position = latLngList.first()) }
                            val finishMarkerState = remember(latLngList) { MarkerState(position = latLngList.last()) }
                            val finishHue = when (config.colorTheme) {
                                ColorTheme.RYDA_ORANGE -> BitmapDescriptorFactory.HUE_ORANGE
                                ColorTheme.RYDA_CYAN -> BitmapDescriptorFactory.HUE_CYAN
                                ColorTheme.FOREST_GREEN -> BitmapDescriptorFactory.HUE_GREEN
                                ColorTheme.HIGH_VIS_LIME -> BitmapDescriptorFactory.HUE_YELLOW
                                ColorTheme.PLAIN_WHITE -> BitmapDescriptorFactory.HUE_AZURE
                                ColorTheme.PLAIN_BLACK -> BitmapDescriptorFactory.HUE_VIOLET
                            }
                            Marker(
                                state = startMarkerState,
                                title = "Start",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                            )
                            Marker(
                                state = finishMarkerState,
                                title = "Finish",
                                icon = BitmapDescriptorFactory.defaultMarker(finishHue)
                            )
                        }

                        MapEffect(config.style, config.colorTheme) { googleMap ->
                            try {
                                googleMap.snapshot { bmp ->
                                    mapSnapshotBitmap = bmp
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    // Watermark Top Right
                    Text(
                        text = "RYDA",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 14.dp)
                    )

                    // Hero Distance Text Stats Overlay for Map Style
                    val startName = startLocationText.trim()
                    val destName = destLocationText.trim()
                    val headerText = when {
                        startName.isNotBlank() && destName.isNotBlank() -> "$startName ➔ $destName"
                        startName.isNotBlank() -> startName
                        destName.isNotBlank() -> destName
                        ride.title.isNotBlank() && !ride.title.contains("Ride", ignoreCase = true) -> ride.title
                        else -> "Recorded Ride"
                    }

                    val distStr = String.format(java.util.Locale.US, "%.1f", ride.totalDistanceKm)
                    val timeStr = ride.formattedDuration(ride.movingTimeSeconds)
                    val avgSpdVal = if (ride.avgSpeedKmh > 0) ride.avgSpeedKmh else if (ride.movingTimeSeconds > 0) (ride.totalDistanceMeters / ride.movingTimeSeconds) * 3.6 else 0.0
                    val avgStr = String.format(java.util.Locale.US, "%.1f", avgSpdVal)
                    val maxSpdVal = if (ride.maxSpeedKmh > 0) ride.maxSpeedKmh else points.maxOfOrNull { it.speedKmh } ?: 0.0
                    val topStr = String.format(java.util.Locale.US, "%.1f", maxSpdVal)

                    val mapSecondaryColor = Color(android.graphics.Color.parseColor(config.colorTheme.secondaryHex))

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = headerText,
                                color = mapSecondaryColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = distStr,
                                    color = graphicAccentColor,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "KM",
                                    color = graphicAccentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Column {
                                Text("TIME", color = graphicAccentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(timeStr, color = graphicAccentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("AVG", color = graphicAccentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(avgStr, color = graphicAccentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("TOP", color = graphicAccentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(topStr, color = graphicAccentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // TRANSPARENT SVG STYLE: Render liveBitmap
                    Image(
                        bitmap = liveBitmap.asImageBitmap(),
                        contentDescription = "Ride Graphic Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // --- 1. GRAPHIC STYLE SELECTION (Transparent, Map Route) ---
            SectionHeader("GRAPHIC STYLE")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OptionChip(
                    label = "1 - Transparent",
                    selected = config.style == GraphicStyle.TRANSPARENT,
                    accentColor = uiAccentColor,
                    onClick = {
                        config = config.copy(style = GraphicStyle.TRANSPARENT)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier.weight(1f)
                )
                OptionChip(
                    label = "2 - Map (Plain Roads)",
                    selected = config.style == GraphicStyle.MAP_BACKGROUND,
                    accentColor = uiAccentColor,
                    onClick = {
                        config = config.copy(style = GraphicStyle.MAP_BACKGROUND)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- 2. LAYOUT TEMPLATE SELECTION (Shown for Transparent PNG and SVG) ---
            if (config.style != GraphicStyle.MAP_BACKGROUND) {
                SectionHeader("LAYOUT TEMPLATE")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SvgLayoutTemplate.entries.toTypedArray()) { tmpl ->
                        OptionChip(
                            label = tmpl.title,
                            selected = config.template == tmpl,
                            accentColor = uiAccentColor,
                            onClick = {
                                config = config.copy(template = tmpl)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // --- 3. BRAND COLOR THEME (6 Color Swatches) ---
            SectionHeader("BRAND COLOR THEME")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorCircleSwatch(
                    color = Color(0xFFFF6B35),
                    selected = config.colorTheme == ColorTheme.RYDA_ORANGE,
                    onClick = {
                        config = config.copy(colorTheme = ColorTheme.RYDA_ORANGE)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
                ColorCircleSwatch(
                    color = Color(0xFF00E5FF),
                    selected = config.colorTheme == ColorTheme.RYDA_CYAN,
                    onClick = {
                        config = config.copy(colorTheme = ColorTheme.RYDA_CYAN)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
                ColorCircleSwatch(
                    color = Color(0xFF10B981),
                    selected = config.colorTheme == ColorTheme.FOREST_GREEN,
                    onClick = {
                        config = config.copy(colorTheme = ColorTheme.FOREST_GREEN)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
                ColorCircleSwatch(
                    color = Color(0xFFCCFF00),
                    selected = config.colorTheme == ColorTheme.HIGH_VIS_LIME,
                    onClick = {
                        config = config.copy(colorTheme = ColorTheme.HIGH_VIS_LIME)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
                ColorCircleSwatch(
                    color = Color.White,
                    selected = config.colorTheme == ColorTheme.PLAIN_WHITE,
                    onClick = {
                        config = config.copy(colorTheme = ColorTheme.PLAIN_WHITE)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
                ColorCircleSwatch(
                    color = Color(0xFF101114),
                    selected = config.colorTheme == ColorTheme.PLAIN_BLACK,
                    onClick = {
                        config = config.copy(colorTheme = ColorTheme.PLAIN_BLACK)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 4. START AND DESTINATION LOCATION CUSTOMIZATION ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Start & Destination Names (Optional)",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = startLocationText,
                        onValueChange = { startLocationText = it },
                        label = { Text("Starting Location (e.g., Bandra Bandstand)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = destLocationText,
                        onValueChange = { destLocationText = it },
                        label = { Text("Destination (e.g., Worli Sea Face)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Floating status toast
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
                    border = BorderStroke(1.dp, uiAccentColor)
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

    // Native Share Sheet Modal
    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = {
                Text(
                    text = "Save to Phone",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select export file format:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    // Option 1: PNG (Default)
                    Surface(
                        onClick = { selectedFormat = ExportFormat.PNG },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedFormat == ExportFormat.PNG) uiAccentColor.copy(alpha = 0.15f) else Color(0xFF1C202B),
                        border = BorderStroke(
                            width = if (selectedFormat == ExportFormat.PNG) 1.5.dp else 1.dp,
                            color = if (selectedFormat == ExportFormat.PNG) uiAccentColor else BorderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFormat == ExportFormat.PNG,
                                onClick = { selectedFormat = ExportFormat.PNG },
                                colors = RadioButtonDefaults.colors(selectedColor = uiAccentColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "PNG",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Transparent image saved to Photos / Gallery",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Option 2: SVG
                    Surface(
                        onClick = { selectedFormat = ExportFormat.SVG },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedFormat == ExportFormat.SVG) uiAccentColor.copy(alpha = 0.15f) else Color(0xFF1C202B),
                        border = BorderStroke(
                            width = if (selectedFormat == ExportFormat.SVG) 1.5.dp else 1.dp,
                            color = if (selectedFormat == ExportFormat.SVG) uiAccentColor else BorderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFormat == ExportFormat.SVG,
                                onClick = { selectedFormat = ExportFormat.SVG },
                                colors = RadioButtonDefaults.colors(selectedColor = uiAccentColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "SVG",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Vector graphic saved to Downloads / Files",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fmt = selectedFormat
                        showFormatDialog = false
                        doExport(format = fmt, saveToGallery = true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = uiAccentColor,
                        contentColor = AsphaltBlack
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFormatDialog = false }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF161920)
        )
    }

    if (showShareSheet && exportedUri != null) {
        val uri = exportedUri!!
        val isSvg = uri.toString().endsWith(".svg", ignoreCase = true)
        val shareMimeType = if (isSvg) "image/svg+xml" else if (config.style == GraphicStyle.TRANSPARENT) "image/png" else "image/jpeg"
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
                    text = if (isSvg) "Share SVG Vector" else if (config.style == GraphicStyle.TRANSPARENT) "Share Transparent Sticker" else "Share Ride Graphic",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (!isSvg && config.style == GraphicStyle.TRANSPARENT && ShareHelper.hasInstagramInstalled(context)) {
                    ShareTargetRow(
                        title = "Instagram Stories Sticker",
                        subtitle = "Adds transparent sticker directly to Instagram Stories",
                        icon = Icons.Default.CameraAlt,
                        accentColor = uiAccentColor,
                        onClick = {
                            showShareSheet = false
                            ShareHelper.shareToInstagramStory(context, uri)
                        }
                    )
                }

                if (!isSvg) {
                    ShareTargetRow(
                        title = "Instagram Feed / Direct",
                        subtitle = "Share image to Instagram Feed or Direct Message",
                        icon = Icons.Default.PhotoLibrary,
                        accentColor = uiAccentColor,
                        onClick = {
                            showShareSheet = false
                            ShareHelper.shareImage(context, uri, shareMimeType)
                        }
                    )
                }

                ShareTargetRow(
                    title = "WhatsApp",
                    subtitle = if (isSvg) "Send SVG vector to chats or groups" else if (config.style == GraphicStyle.TRANSPARENT) "Send transparent sticker to chats" else "Share with riding groups or status",
                    icon = Icons.Default.Chat,
                    accentColor = uiAccentColor,
                    onClick = {
                        showShareSheet = false
                        ShareHelper.shareImage(context, uri, shareMimeType)
                    }
                )

                ShareTargetRow(
                    title = "More Apps...",
                    subtitle = "Open system share chooser",
                    icon = Icons.Default.Share,
                    accentColor = uiAccentColor,
                    onClick = {
                        showShareSheet = false
                        ShareHelper.shareImage(context, uri, shareMimeType)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accentColor else SurfaceCard)
            .border(1.dp, if (selected) accentColor else BorderSubtle, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) AsphaltBlack else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ColorCircleSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) color.copy(alpha = 0.22f) else Color.Transparent)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) color else BorderSubtle,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun ShareTargetRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
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
                tint = accentColor,
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
