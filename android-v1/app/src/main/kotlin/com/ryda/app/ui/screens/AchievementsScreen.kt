package com.ryda.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lock
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
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.ryda.app.core.models.Achievement
import com.ryda.app.core.models.AchievementsResponse
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.theme.*

@Composable
fun AchievementsScreen(
    repository: RydaRepository,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var response by remember { mutableStateOf<AchievementsResponse?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        response = repository.getAchievements()
    }

    val categories = listOf("All", "Riding Milestones", "Distance Milestones", "Riding Experiences", "Community", "Clubs & Rallies")

    val allAchievements = response?.achievements ?: emptyList()
    val filteredAchievements = remember(allAchievements, selectedCategory) {
        if (selectedCategory == "All") allAchievements
        else allAchievements.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    val unlockedCount = response?.unlockedCount ?: 0
    val totalCount = response?.totalCount ?: 30
    val completionPercent = response?.completionRatePercent ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Header Bar
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
                    text = "ACHIEVEMENTS CATALOGUE",
                    color = SafetyAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "All Milestones ($unlockedCount/$totalCount)",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Aggregate Progress Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, tint = MotoOrange, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("OVERALL MILESTONE PROGRESS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("$unlockedCount of $totalCount Unlocked", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("$completionPercent%", color = MotoOrange, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { (completionPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MotoOrange,
                    trackColor = SurfaceCard
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) MotoOrange else SurfaceCard)
                        .border(1.dp, if (isSelected) MotoOrange else BorderSubtle, RoundedCornerShape(20.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) AsphaltBlack else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Achievements List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredAchievements, key = { it.id }) { achievement ->
                AchievementDetailCard(
                    achievement = achievement,
                    onShare = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "RYDA Achievement: ${achievement.title}")
                            putExtra(Intent.EXTRA_TEXT, "I unlocked '${achievement.title}' on RYDA! ${achievement.description}. Track your rides on RYDA.")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Achievement"))
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun AchievementDetailCard(
    achievement: Achievement,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (achievement.isUnlocked) MotoOrange.copy(alpha = 0.6f) else BorderSubtle
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SVG Badge Asset
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (achievement.isUnlocked) MotoOrange.copy(alpha = 0.15f) else SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                val badgeUrl = com.ryda.app.core.models.getBadgeAssetUrl(achievement.iconName)
                if (badgeUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(badgeUrl)
                            .decoderFactory(coil.decode.SvgDecoder.Factory())
                            .crossfade(true)
                            .build(),
                        contentDescription = achievement.title,
                        modifier = Modifier.size(54.dp)
                    )
                } else {
                    androidx.compose.material3.Icon(
                        imageVector = when (achievement.category.uppercase()) {
                            "DISTANCE" -> androidx.compose.material.icons.Icons.Default.EmojiEvents
                            "COUNT"    -> androidx.compose.material.icons.Icons.Default.EmojiEvents
                            "EXPLORATION" -> androidx.compose.material.icons.Icons.Default.Explore
                            else -> androidx.compose.material.icons.Icons.Default.EmojiEvents
                        },
                        contentDescription = achievement.title,
                        tint = if (achievement.isUnlocked) com.ryda.app.ui.theme.MotoOrange
                               else com.ryda.app.ui.theme.TextSecondary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = achievement.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (achievement.isUnlocked) {
                        IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = MotoOrange, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Locked", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                Text(
                    text = achievement.description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (achievement.isUnlocked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "UNLOCKED", color = ForestGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { (achievement.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = SafetyAmber,
                            trackColor = SurfaceCard
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${achievement.progressPercent}%",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
