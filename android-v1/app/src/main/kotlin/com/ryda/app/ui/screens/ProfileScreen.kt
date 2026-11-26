package com.ryda.app.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.ryda.app.core.models.Achievement
import com.ryda.app.core.models.Club
import com.ryda.app.core.models.Motorcycle
import com.ryda.app.core.models.Ride
import com.ryda.app.core.models.RiderProfile
import com.ryda.app.core.models.RiderTag
import com.ryda.app.core.models.getBadgeAssetUrl
import com.ryda.app.core.network.RetrofitBuilder
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: RiderProfile,
    onUpdateProfile: (RiderProfile) -> Unit,
    onLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    repository: RydaRepository? = null,
    onOpenSettings: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
    onOpenTags: () -> Unit = {},
    onOpenGarage: () -> Unit = {},
    onRideClick: (String) -> Unit = {}
) {
    val context = LocalContext.current

    var achievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    var motorcycles by remember { mutableStateOf<List<Motorcycle>>(emptyList()) }
    var clubs by remember { mutableStateOf<List<Club>>(emptyList()) }
    var recentRides by remember { mutableStateOf<List<Ride>>(emptyList()) }

    var showEditDialog by remember { mutableStateOf(false) }
    var showShowcaseDialog by remember { mutableStateOf(false) }

    var editName by remember(profile) { mutableStateOf(profile.displayName) }
    var editCity by remember(profile) { mutableStateOf(profile.city) }
    var editBio by remember(profile) { mutableStateOf(profile.bio) }
    var editAvatarUrl by remember(profile) { mutableStateOf(profile.avatarUrl) }

    LaunchedEffect(Unit) {
        if (repository != null) {
            val res = repository.getAchievements()
            if (res != null) {
                achievements = res.achievements
            }
            motorcycles = repository.motorcycles.value
            clubs = repository.getUserClubs()
            recentRides = repository.completedRides.value.take(4)
        }
    }

    val primaryBike = remember(motorcycles) {
        motorcycles.firstOrNull { it.isPrimary } ?: motorcycles.firstOrNull()
    }

    val unlockedAchievements = remember(achievements) {
        achievements.filter { it.isUnlocked }
    }

    val showcasedAchievements = remember(achievements, profile.showcasedAchievementIds) {
        if (profile.showcasedAchievementIds.isNotEmpty()) {
            achievements.filter { it.id in profile.showcasedAchievementIds || it.key in profile.showcasedAchievementIds }
        } else {
            unlockedAchievements.take(4)
        }
    }

    // Up to 3 rider tags displayed on the profile
    val profileTags = remember(profile.activeTagIds) {
        profile.activeTagIds.take(3).mapNotNull { tagId ->
            RiderTag.ALL_TAGS.firstOrNull { it.id == tagId }
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { editAvatarUrl = it.toString() }
    }
    val legacyAvatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { editAvatarUrl = it.toString() }
    }

    fun pickAvatar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            legacyAvatarPicker.launch("image/*")
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── 1. PROFILE HEADER ────────────────────────────────────────────────
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
                    verticalAlignment = Alignment.Top
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(SurfaceCard)
                                .border(2.dp, SafetyAmber, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val resolvedAvatar = remember(profile.avatarUrl) {
                                RetrofitBuilder.resolveMediaUrl(profile.avatarUrl)
                            }
                            if (resolvedAvatar.isNotBlank()) {
                                RideImageThumbnail(
                                    photoUri = resolvedAvatar,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = SafetyAmber,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = profile.displayName,
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@${profile.username.ifBlank { "rider" }}",
                                color = SafetyAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Joined ${profile.dateJoined}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Row {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Profile", tint = SafetyAmber)
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                        }
                    }
                }

                if (profile.bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = profile.bio,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Up to 3 Rider Tags Display
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        profileTags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SafetyAmber.copy(alpha = 0.15f))
                                    .border(1.dp, SafetyAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tag.name,
                                    color = SafetyAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    TextButton(onClick = onOpenTags) {
                        Text("Edit Tags", color = SafetyAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── 2. SOCIAL STATISTICS (3-Column Row) ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("FOLLOWERS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${profile.followersCount}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("FOLLOWING", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${profile.followingCount}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("TOTAL RIDES", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${profile.totalRides}", color = SafetyAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── 3. RIDING STATISTICS SUMMARY (3-Column Row) ─────────────────────
        Text(
            text = "RIDING STATISTICS SUMMARY",
            color = SafetyAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Distance",
                value = String.format(Locale.US, "%.1f km", profile.totalDistanceKm),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Time",
                value = String.format(Locale.US, "%.1f hrs", profile.totalTimeHours),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Rides",
                value = "${profile.totalRides}",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 4. PERSONAL RECORDS (PR SYSTEM) ──────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonDark),
            border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PERSONAL RECORDS",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text("PERSONAL BESTS", color = SafetyAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LONGEST RIDE", color = TextSecondary, fontSize = 10.sp)
                        Text(String.format(Locale.US, "%.1f km", profile.longestRideKm), color = SafetyAmber, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("HIGHEST MONTHLY", color = TextSecondary, fontSize = 10.sp)
                        Text(String.format(Locale.US, "%.1f km", profile.highestMonthlyDistanceKm), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MOST MONTHLY RIDES", color = TextSecondary, fontSize = 10.sp)
                        Text("${profile.mostRidesMonthly} rides", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LONGEST DURATION", color = TextSecondary, fontSize = 10.sp)
                        Text(formatDuration(profile.longestDurationSeconds), color = SafetyAmber, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 5. DIGITAL GARAGE (Primary Motorcycle) ────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PRIMARY MOTORCYCLE",
                color = SafetyAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            TextButton(onClick = onOpenGarage) {
                Text("View Full Garage", color = SafetyAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (primaryBike != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGarage() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, SafetyAmber)
            ) {
                Column {
                    if (!primaryBike.imageUri.isNullOrBlank()) {
                        RideImageThumbnail(
                            photoUri = primaryBike.imageUri,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "${primaryBike.brand} ${primaryBike.model}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(text = "${primaryBike.year} • ${primaryBike.engineCc} cc", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DEFAULT", color = ForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGarage() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("No motorcycle added", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Add your bike to Digital Garage", color = TextSecondary, fontSize = 12.sp)
                    }
                    Text("Add Bike", color = SafetyAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 6. ACHIEVEMENT SHOWCASE ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ACHIEVEMENT SHOWCASE",
                color = SafetyAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Row {
                TextButton(onClick = { showShowcaseDialog = true }) {
                    Text("Customize", color = TextSecondary, fontSize = 11.sp)
                }
                TextButton(onClick = onOpenAchievements) {
                    Text("View All", color = SafetyAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showcasedAchievements.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text(
                    text = "Complete rides to unlock and showcase achievements.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(showcasedAchievements) { ach ->
                    val assetUrl = getBadgeAssetUrl(ach.iconName)
                    Card(
                        modifier = Modifier
                            .width(130.dp)
                            .clickable { onOpenAchievements() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CarbonDark),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceCard)
                                    .border(1.5.dp, SafetyAmber, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (assetUrl != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(assetUrl)
                                            .decoderFactory(SvgDecoder.Factory())
                                            .build(),
                                        contentDescription = ach.title,
                                        modifier = Modifier.size(32.dp)
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = ach.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 7. RIDER CLUBS SECTION ───────────────────────────────────────────
        if (clubs.isNotEmpty()) {
            Text(
                text = "MY CLUBS",
                color = SafetyAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(clubs) { club ->
                    Card(
                        modifier = Modifier.width(160.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CarbonDark),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = club.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "${club.membersCount} members • ${club.myRole ?: "Member"}", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── 8. RECENT ACTIVITY SECTION ───────────────────────────────────────
        Text(
            text = "RECENT RIDING ACTIVITY",
            color = SafetyAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (recentRides.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text(
                    text = "No rides recorded yet. Tap Record to log your first ride!",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentRides.forEach { ride ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRideClick(ride.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CarbonDark),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = ride.title.ifBlank { "Motorcycle Ride" }, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(text = String.format(Locale.US, "%.1f km • %d mins", ride.totalDistanceKm, ride.movingTimeSeconds / 60), color = TextSecondary, fontSize = 12.sp)
                            }
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // Edit Profile Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = CarbonDark,
            title = { Text("Edit Rider Profile", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .clickable { pickAvatar() }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (editAvatarUrl.isNotBlank()) "Avatar photo selected ✓" else "Choose Profile Avatar", color = TextPrimary, fontSize = 12.sp)
                    }

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCity,
                        onValueChange = { editCity = it },
                        label = { Text("City / Region") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Short Bio") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            onUpdateProfile(
                                profile.copy(
                                    displayName = editName.trim(),
                                    city = editCity.trim(),
                                    bio = editBio.trim(),
                                    avatarUrl = editAvatarUrl
                                )
                            )
                            showEditDialog = false
                        }
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
            }
        )
    }

    // Showcase Customization Dialog
    if (showShowcaseDialog) {
        var selectedIds by remember { mutableStateOf(showcasedAchievements.map { it.id }.toSet()) }

        AlertDialog(
            onDismissRequest = { showShowcaseDialog = false },
            containerColor = CarbonDark,
            title = { Text("Select Showcase Badges", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose up to 4 unlocked achievements to showcase on your profile:", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))

                    if (unlockedAchievements.isEmpty()) {
                        Text("No unlocked achievements yet.", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            unlockedAchievements.forEach { ach ->
                                val isChecked = ach.id in selectedIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isChecked) SafetyAmber.copy(alpha = 0.15f) else SurfaceCard)
                                        .clickable {
                                            selectedIds = if (isChecked) {
                                                selectedIds - ach.id
                                            } else {
                                                if (selectedIds.size < 4) selectedIds + ach.id else selectedIds
                                            }
                                        }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = ach.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    if (isChecked) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateProfile(profile.copy(showcasedAchievementIds = selectedIds.toList()))
                        showShowcaseDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack)
                ) {
                    Text("Save Showcase")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShowcaseDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label.uppercase(), color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
}
