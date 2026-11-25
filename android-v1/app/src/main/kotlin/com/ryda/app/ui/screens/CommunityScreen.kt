package com.ryda.app.ui.screens

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
import com.ryda.app.core.models.CommunityPost
import com.ryda.app.core.models.PostComment
import com.ryda.app.core.models.PostCreateRequest
import com.ryda.app.core.models.Ride
import com.ryda.app.core.models.RidingEvent
import com.ryda.app.core.network.NetworkMonitor
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.components.OfflineStateView
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    repository: RydaRepository,
    completedRides: List<Ride> = emptyList()
) {
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor.getInstance(context) }
    val isOnline by networkMonitor.isOnline.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("discover") }
    var posts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var rallies by remember { mutableStateOf<List<RidingEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Dialog & Sheet States
    var showCreatePostSheet by remember { mutableStateOf(false) }
    var selectedPostForComments by remember { mutableStateOf<CommunityPost?>(null) }
    var commentsList by remember { mutableStateOf<List<PostComment>>(emptyList()) }
    var commentInputText by remember { mutableStateOf("") }

    fun refreshData() {
        coroutineScope.launch {
            isLoading = true
            if (selectedTab == "rallies") {
                rallies = repository.getExploreEvents()
            } else {
                posts = repository.getCommunityFeed(selectedTab)
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedTab) {
        refreshData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Explore & Community",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { showCreatePostSheet = true },
                colors = ButtonDefaults.buttonColors(containerColor = MotoOrange),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Post", tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Post", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Segmented Feed Switcher (Discover vs Following vs Rallies)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val tabs = listOf("Discover" to "discover", "Following" to "following", "Rallies" to "rallies")
            tabs.forEach { (label, key) ->
                val isSelected = selectedTab == key
                val bg by animateColorAsState(if (isSelected) MotoOrange else Color.Transparent)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(bg)
                        .clickable { selectedTab = key }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Offline State vs Live Content
        if (!isOnline) {
            OfflineStateView(
                title = "You're Currently Offline",
                description = "Connect to the internet to view live community rides, rallies, and discussions.",
                onRetry = {
                    networkMonitor.forceCheckStatus()
                    refreshData()
                }
            )
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MotoOrange)
            }
        } else if (selectedTab == "rallies") {
            if (rallies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No upcoming rallies or expeditions", color = TextSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        Text(
                            text = "UPCOMING RALLIES & GROUP EXPEDITIONS",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(rallies, key = { it.id }) { rally ->
                        RallyEventCard(
                            event = rally,
                            onRsvpClick = { desiredStatus ->
                                coroutineScope.launch {
                                    val res = repository.setEventRsvp(rally.id, desiredStatus)
                                    if (res != null) {
                                        rallies = rallies.map {
                                            if (it.id == rally.id) it.copy(
                                                myRsvp = res.myRsvp,
                                                confirmedCount = res.confirmedCount,
                                                interestedCount = res.interestedCount
                                            ) else it
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        } else if (posts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.TwoWheeler, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "No community posts yet", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(posts, key = { it.id }) { post ->
                    CommunityPostCard(
                        post = post,
                        onLikeClick = {
                            coroutineScope.launch {
                                val res = repository.togglePostLike(post.id)
                                if (res != null) {
                                    posts = posts.map {
                                        if (it.id == post.id) it.copy(isLiked = res.isLiked, likesCount = res.likesCount) else it
                                    }
                                }
                            }
                        },
                        onFollowClick = {
                            coroutineScope.launch {
                                val res = repository.toggleRiderFollow(post.authorId)
                                if (res != null) {
                                    posts = posts.map {
                                        if (it.authorId == post.authorId) it.copy(isFollowingAuthor = res.isFollowing) else it
                                    }
                                }
                            }
                        },
                        onCommentsClick = {
                            selectedPostForComments = post
                            coroutineScope.launch {
                                commentsList = repository.getPostComments(post.id)
                            }
                        },
                        onShareClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Check out ${post.authorDisplayName}'s ride on RYDA: ${post.caption}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Ride"))
                        }
                    )
                }
            }
        }
    }

    // Create Post Bottom Sheet
    if (showCreatePostSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreatePostSheet = false },
            containerColor = CarbonDark,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
        ) {
            CreatePostSheetContent(
                completedRides = completedRides,
                onPublish = { req ->
                    coroutineScope.launch {
                        val newPost = repository.createCommunityPost(req)
                        if (newPost != null) {
                            refreshData()
                            showCreatePostSheet = false
                        }
                    }
                },
                onCancel = { showCreatePostSheet = false }
            )
        }
    }

    // Comments Sheet
    selectedPostForComments?.let { post ->
        ModalBottomSheet(
            onDismissRequest = { selectedPostForComments = null },
            containerColor = CarbonDark,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = "Rider Comments", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                if (commentsList.isEmpty()) {
                    Text(text = "No comments yet. Start the conversation!", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(commentsList) { comment ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceCard),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                                Column {
                                    Text(text = comment.displayName.ifEmpty { comment.username }, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(text = comment.text, color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = commentInputText,
                        onValueChange = { commentInputText = it },
                        placeholder = { Text("Write a comment...", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = MotoOrange,
                            unfocusedBorderColor = BorderSubtle
                        )
                    )
                    IconButton(
                        onClick = {
                            if (commentInputText.isNotBlank()) {
                                coroutineScope.launch {
                                    val added = repository.addPostComment(post.id, commentInputText)
                                    if (added != null) {
                                        commentsList = commentsList + added
                                        commentInputText = ""
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MotoOrange)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun CommunityPostCard(
    post: CommunityPost,
    onLikeClick: () -> Unit,
    onFollowClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Rider Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = post.authorDisplayName.ifEmpty { post.authorUsername },
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceCard)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (post.authorCity.isNotEmpty()) post.authorCity else "Verified Rider",
                                    color = if (post.authorCity.isNotEmpty()) MotoOrange else ForestGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = if (post.authorCity.isNotEmpty()) "${post.authorCity} • Verified Rider" else "Rider",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Follow Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (post.isFollowingAuthor) MotoOrange else SurfaceCard)
                        .clickable { onFollowClick() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (post.isFollowingAuthor) "Following" else "Follow",
                        color = if (post.isFollowingAuthor) Color.White else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Caption
            if (post.caption.isNotEmpty()) {
                Text(
                    text = post.caption,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Verified Ride Telemetry Block
            post.rideTelemetry?.let { telemetry ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AsphaltBlack)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MotoOrange.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "VERIFIED RIDE LOG",
                                        color = MotoOrange,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = telemetry.motorcycleName,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Distance", color = TextSecondary, fontSize = 10.sp)
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", telemetry.totalDistanceKm)} KM",
                                    color = Color.White,
                                    fontFamily = StatFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Moving", color = TextSecondary, fontSize = 10.sp)
                                val mins = telemetry.movingTimeSeconds / 60
                                val hrs = mins / 60
                                val remMins = mins % 60
                                val secs = telemetry.movingTimeSeconds % 60
                                val timeStr = if (hrs > 0) String.format(java.util.Locale.US, "%02d:%02d:%02d", hrs, remMins, secs) else String.format(java.util.Locale.US, "%02d:%02d", remMins, secs)
                                Text(
                                    text = timeStr,
                                    color = Color.White,
                                    fontFamily = StatFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Avg Speed", color = TextSecondary, fontSize = 10.sp)
                                Text(
                                    text = "${telemetry.avgSpeedKmh.toInt()} KM/H",
                                    color = ElectricCyan,
                                    fontFamily = StatFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Attached Image
            if (post.imageUrl.isNotBlank()) {
                RideImageThumbnail(
                    photoUri = post.imageUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Bottom Social Actions (Like, Comments, Share)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Like button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onLikeClick() }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (post.isLiked) CrimsonRed else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = post.likesCount.toString(),
                            color = if (post.isLiked) CrimsonRed else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Comments button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onCommentsClick() }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Comments",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = post.commentsCount.toString(),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Share
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onShareClick() }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Share", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CreatePostSheetContent(
    completedRides: List<Ride>,
    onPublish: (PostCreateRequest) -> Unit,
    onCancel: () -> Unit
) {
    var postType by remember { mutableStateOf("RIDE") }
    var selectedRideId by remember { mutableStateOf(completedRides.firstOrNull()?.id) }
    var captionText by remember { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selectedPhotoUri = it.toString() }
    }

    val legacyPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedPhotoUri = it.toString() }
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Create Community Post", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onCancel) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Post Type Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { postType = "RIDE" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (postType == "RIDE") MotoOrange else SurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Share Ride", color = if (postType == "RIDE") Color.White else TextSecondary, fontSize = 12.sp)
            }
            Button(
                onClick = { postType = "THOUGHT" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (postType == "THOUGHT") MotoOrange else SurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Photo / Thought", color = if (postType == "THOUGHT") Color.White else TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // If Sharing Ride: pick from completed rides
        if (postType == "RIDE" && completedRides.isNotEmpty()) {
            Text(text = "Select Completed Ride", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(completedRides, key = { it.id }) { ride ->
                    val isSelected = ride.id == selectedRideId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) SurfaceElevated else AsphaltBlack)
                            .border(1.dp, if (isSelected) MotoOrange else BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { selectedRideId = ride.id }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = ride.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = "${String.format("%.1f", ride.totalDistanceKm)} KM • ${ride.formattedDuration(ride.movingTimeSeconds)}", color = TextSecondary, fontSize = 11.sp)
                        }
                        if (isSelected) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            // Preview of selected ride
            val selectedRide = completedRides.firstOrNull { it.id == selectedRideId }
            if (selectedRide != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SELECTED RIDE TELEMETRY",
                                color = MotoOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Public Post",
                                color = ForestGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Distance", color = TextSecondary, fontSize = 10.sp)
                                Text("${String.format("%.1f", selectedRide.totalDistanceKm)} km", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Moving Time", color = TextSecondary, fontSize = 10.sp)
                                Text(selectedRide.formattedDuration(selectedRide.movingTimeSeconds), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Avg Speed", color = TextSecondary, fontSize = 10.sp)
                                Text("${String.format("%.1f", selectedRide.avgSpeedKmh)} km/h", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Top Speed", color = TextSecondary, fontSize = 10.sp)
                                Text("${String.format("%.0f", selectedRide.maxSpeedKmh)} km/h", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // If Photo / Thought: optional photo picker button and preview
        if (postType == "THOUGHT") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .clickable { pickPhoto() }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MotoOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedPhotoUri != null) "Photo attached ✓" else "Attach Photo (Optional)",
                        color = if (selectedPhotoUri != null) MotoOrange else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(text = if (selectedPhotoUri != null) "Change" else "Choose", color = TextSecondary, fontSize = 11.sp)
            }
            if (selectedPhotoUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                RideImageThumbnail(
                    photoUri = selectedPhotoUri!!,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Caption Input
        Text(text = "Caption", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = captionText,
            onValueChange = { captionText = it },
            placeholder = { Text("Share route highlights, tire grip, scenic stops...", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = MotoOrange,
                unfocusedBorderColor = BorderSubtle
            )
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                onPublish(
                    PostCreateRequest(
                        ride = if (postType == "RIDE") selectedRideId else null,
                        postType = postType,
                        caption = captionText,
                        imageUrl = selectedPhotoUri ?: ""
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MotoOrange),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("PUBLISH TO COMMUNITY", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun RallyEventCard(
    event: RidingEvent,
    onRsvpClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, MotoOrange.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MotoOrange.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (event.isOfficial) "OFFICIAL RALLY" else "CLUB RIDE",
                        color = MotoOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${event.distanceKm.toInt()} KM • ${event.pace}",
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = event.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            if (event.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AsphaltBlack)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = Icons.Default.Event, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(14.dp))
                    Text(text = "Rally Date: ${formatRallyDateTime(event.startTime)}", color = SafetyAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = Icons.Default.PinDrop, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(14.dp))
                    Text(text = "Meeting: ${event.meetingPoint}", color = TextPrimary, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = Icons.Default.Flag, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
                    Text(text = "Destination: ${event.destination}", color = TextPrimary, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = Icons.Default.Group, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(14.dp))
                    Text(text = "${event.confirmedCount} Confirmed (Limit: ${event.riderLimit})", color = ForestGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isGoing = event.myRsvp == "GOING"
                val isInterested = event.myRsvp == "INTERESTED"

                Button(
                    onClick = { onRsvpClick("GOING") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGoing) ForestGreen else MotoOrange
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isGoing) "✓ Going (RSVP'd)" else "RSVP Going",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { onRsvpClick("INTERESTED") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isInterested) SurfaceElevated else SurfaceCard
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isInterested) ForestGreen else BorderSubtle),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isInterested) "Interested ★" else "Interested",
                        color = if (isInterested) ForestGreen else TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

fun formatRallyDateTime(rawString: String): String {
    if (rawString.isBlank()) return "Date to be announced"
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val parsed = java.time.OffsetDateTime.parse(rawString)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
            parsed.format(formatter)
        } else rawString
    } catch (_: Exception) {
        rawString
    }
}
