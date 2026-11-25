package com.ryda.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalOffer
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
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.ryda.app.core.models.RiderTag
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.theme.*

@Composable
fun TagSelectionScreen(
    repository: RydaRepository,
    onBackClick: () -> Unit
) {
    var tags by remember { mutableStateOf<List<RiderTag>>(emptyList()) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        tags = repository.getRiderTags()
    }

    val activeTag = remember(tags) { tags.firstOrNull { it.isActive } }
    val availableTags = remember(tags) { tags.filter { it.isUnlocked } }
    val lockedTags = remember(tags) { tags.filter { !it.isUnlocked } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
            .padding(horizontal = 20.dp)
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
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "RIDER IDENTITY",
                    color = SafetyAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Select Username Tag",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Tag Summary Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CURRENT ACTIVE TAG", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (activeTag != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SurfaceCard)
                                            .border(1.dp, MotoOrange, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(text = "• ${activeTag.name}", color = MotoOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Text("No active tag selected", color = TextPrimary, fontSize = 14.sp)
                            }
                        }

                        if (activeTag != null) {
                            TextButton(
                                onClick = {
                                    repository.setActiveTag(null)
                                    refreshTrigger++
                                }
                            ) {
                                Text("Remove Tag", color = CrimsonRed, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Unlocked / Available Tags Section
            item {
                Text("AVAILABLE TAGS (${availableTags.size})", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }

            items(availableTags) { tag ->
                RiderTagCard(
                    tag = tag,
                    onSelect = {
                        repository.setActiveTag(tag.id)
                        refreshTrigger++
                    }
                )
            }

            // Locked Tags Section
            if (lockedTags.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("LOCKED TAGS (${lockedTags.size})", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }

                items(lockedTags) { tag ->
                    RiderTagCard(
                        tag = tag,
                        onSelect = {}
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun RiderTagCard(
    tag: RiderTag,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tag.isUnlocked) { onSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tag.isActive) SurfaceElevated else CarbonDark
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (tag.isActive) MotoOrange else BorderSubtle
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(com.ryda.app.core.models.getBadgeAssetUrl(tag.iconName))
                        .decoderFactory(SvgDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = tag.name,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tag.name,
                            color = if (tag.isUnlocked) TextPrimary else TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (tag.unlockType == "free") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("FREE", color = HighVisLime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (tag.unlockRequirement != null) {
                        Text(
                            text = tag.unlockRequirement,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (tag.isActive) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Active", tint = MotoOrange, modifier = Modifier.size(20.dp))
                } else if (!tag.isUnlocked) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = "Locked", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
