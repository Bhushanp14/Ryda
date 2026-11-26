package com.ryda.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
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
import com.ryda.app.core.models.getBadgeAssetUrl
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.theme.*

@Composable
fun AchievementsScreen(
    repository: RydaRepository,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var response by remember { mutableStateOf<AchievementsResponse?>(null) }
    var selectedCategory by remember { mutableStateOf("Riding Milestones") }
    var selectedAchievementForDetail by remember { mutableStateOf<Achievement?>(null) }

    LaunchedEffect(Unit) {
        response = repository.getAchievements()
    }

    val categories = listOf(
        "Riding Milestones",
        "Distance Milestones",
        "Community & Social Engagement",
        "Clubs & Rallies"
    )

    val allAchievements = response?.achievements ?: emptyList()
    val filteredAchievements = remember(allAchievements, selectedCategory) {
        allAchievements.filter { achievement ->
            when (selectedCategory) {
                "Riding Milestones" -> achievement.category.equals("Riding Milestones", ignoreCase = true)
                "Distance Milestones" -> achievement.category.equals("Distance Milestones", ignoreCase = true)
                "Community & Social Engagement" -> achievement.category.equals("Community", ignoreCase = true) || achievement.category.contains("Social", ignoreCase = true)
                "Clubs & Rallies" -> achievement.category.equals("Clubs & Rallies", ignoreCase = true) || achievement.category.contains("Club", ignoreCase = true)
                else -> true
            }
        }
    }

    val unlockedCount = response?.unlockedCount ?: allAchievements.count { it.isUnlocked }
    val totalCount = response?.totalCount ?: allAchievements.size.coerceAtLeast(1)
    val completionPercent = response?.completionRatePercent ?: if (totalCount > 0) (unlockedCount * 100) / totalCount else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // 1. Page Header Bar
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
                    text = "Achievements",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Your riding journey, milestones and accomplishments.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Achievement Summary Cards
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
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("UNLOCKED", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$unlockedCount", color = SafetyAmber, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("AVAILABLE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$totalCount", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("PROGRESS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$completionPercent%", color = ForestGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Achievement Category Navigation Tabs
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) SafetyAmber else CarbonDark)
                        .border(1.dp, if (isSelected) SafetyAmber else BorderSubtle, RoundedCornerShape(20.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) AsphaltBlack else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Achievement List
        if (filteredAchievements.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No achievements in this category yet.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredAchievements) { item ->
                    AchievementCardItem(
                        achievement = item,
                        onClick = { selectedAchievementForDetail = item }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    // 5. Achievement Detail Dialog
    selectedAchievementForDetail?.let { ach ->
        AlertDialog(
            onDismissRequest = { selectedAchievementForDetail = null },
            containerColor = CarbonDark,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(ach.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { selectedAchievementForDetail = null }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val assetUrl = getBadgeAssetUrl(ach.iconName)
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                            .border(2.dp, if (ach.isUnlocked) SafetyAmber else BorderSubtle, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (assetUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(assetUrl)
                                    .decoderFactory(SvgDecoder.Factory())
                                    .build(),
                                contentDescription = ach.title,
                                modifier = Modifier.size(60.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (ach.isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (ach.isUnlocked) SafetyAmber else TextSecondary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = ach.description,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (ach.isUnlocked) "Status: Unlocked ✓" else "Status: Locked",
                                    color = if (ach.isUnlocked) ForestGreen else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${ach.progressValue.toInt()} / ${ach.targetValue.toInt()} ${ach.unit}",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (ach.progressValue / ach.targetValue.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (ach.isUnlocked) ForestGreen else SafetyAmber,
                                trackColor = BorderSubtle
                            )
                            if (ach.isUnlocked && !ach.unlockedAt.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Unlocked: ${ach.unlockedAt}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun AchievementCardItem(
    achievement: Achievement,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val assetUrl = getBadgeAssetUrl(achievement.iconName)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark),
        border = BorderStroke(1.dp, if (achievement.isUnlocked) SafetyAmber.copy(alpha = 0.5f) else BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SurfaceCard)
                    .border(1.5.dp, if (achievement.isUnlocked) SafetyAmber else BorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (assetUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(assetUrl)
                            .decoderFactory(SvgDecoder.Factory())
                            .build(),
                        contentDescription = achievement.title,
                        modifier = Modifier.size(38.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (achievement.isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (achievement.isUnlocked) SafetyAmber else TextSecondary,
                        modifier = Modifier.size(28.dp)
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (achievement.isUnlocked) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Unlocked",
                            tint = ForestGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = achievement.description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { (achievement.progressValue / achievement.targetValue.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (achievement.isUnlocked) ForestGreen else SafetyAmber,
                        trackColor = BorderSubtle
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${achievement.progressValue.toInt()}/${achievement.targetValue.toInt()} ${achievement.unit}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
