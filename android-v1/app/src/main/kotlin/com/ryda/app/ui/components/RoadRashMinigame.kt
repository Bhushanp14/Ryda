package com.ryda.app.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.ui.theme.AsphaltBlack
import com.ryda.app.ui.theme.BorderSubtle
import com.ryda.app.ui.theme.CarbonDark
import com.ryda.app.ui.theme.CrimsonRed
import com.ryda.app.ui.theme.HighVisLime
import com.ryda.app.ui.theme.SafetyAmber
import com.ryda.app.ui.theme.StatFontFamily
import com.ryda.app.ui.theme.TextPrimary
import com.ryda.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class GameEntity(
    val id: Long,
    val lane: Int, // 0 = Left, 1 = Center, 2 = Right
    var yNormalized: Float, // 0.0 (top) to 1.0 (bottom)
    val type: EntityType,
)

private enum class EntityType {
    CAR, OIL_SLICK, FUEL_CANISTER, NITRO
}

@Composable
fun RoadRashMinigame(
    onRetryConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("road_rash_game", Context.MODE_PRIVATE) }
    var highScore by remember { mutableIntStateOf(prefs.getInt("high_score", 0)) }

    var playerLane by remember { mutableIntStateOf(1) } // 0, 1, 2
    var scoreMeters by remember { mutableIntStateOf(0) }
    var isCrashed by remember { mutableStateOf(false) }
    var roadOffset by remember { mutableStateOf(0f) }

    val entities = remember { mutableStateListOf<GameEntity>() }
    var nextEntityId by remember { mutableStateOf(1L) }

    // Game Loop
    LaunchedEffect(isCrashed) {
        if (!isCrashed) {
            scoreMeters = 0
            entities.clear()
            playerLane = 1

            while (!isCrashed) {
                delay(30)
                scoreMeters += 2
                roadOffset = (roadOffset + 0.08f) % 1.0f

                // Spawn new obstacles & collectibles
                if (Random.nextFloat() < 0.08f) {
                    val lane = Random.nextInt(3)
                    val type = when (Random.nextFloat()) {
                        in 0.0f..0.5f -> EntityType.CAR
                        in 0.5f..0.75f -> EntityType.OIL_SLICK
                        in 0.75f..0.9f -> EntityType.FUEL_CANISTER
                        else -> EntityType.NITRO
                    }
                    entities.add(GameEntity(nextEntityId++, lane, 0.0f, type))
                }

                // Update existing entity positions & check collisions
                val iterator = entities.iterator()
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    item.yNormalized += 0.035f

                    // Collision check around player position (yNormalized >= 0.8f and <= 0.95f)
                    if (item.yNormalized in 0.78f..0.92f && item.lane == playerLane) {
                        when (item.type) {
                            EntityType.CAR, EntityType.OIL_SLICK -> {
                                isCrashed = true
                                if (scoreMeters > highScore) {
                                    highScore = scoreMeters
                                    prefs.edit().putInt("high_score", highScore).apply()
                                }
                            }
                            EntityType.FUEL_CANISTER -> {
                                scoreMeters += 250
                                iterator.remove()
                            }
                            EntityType.NITRO -> {
                                scoreMeters += 500
                                iterator.remove()
                            }
                        }
                    } else if (item.yNormalized > 1.1f) {
                        iterator.remove()
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsphaltBlack)
    ) {
        // Sticky Top Offline Reconnect Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CarbonDark)
                .border(1.dp, BorderSubtle)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = SafetyAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "YOU'RE OFFLINE",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Play Road Rash while reconnecting",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = onRetryConnection,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafetyAmber,
                        contentColor = AsphaltBlack
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Retry",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Telemetry Score Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CarbonDark.copy(alpha = 0.6f))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DISTANCE",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "$scoreMeters m",
                    color = SafetyAmber,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = StatFontFamily
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "BEST DISTANCE",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "$highScore m",
                    color = HighVisLime,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = StatFontFamily
                )
            }
        }

        // Game Canvas View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(isCrashed) {
                    if (!isCrashed) {
                        detectTapGestures { offset ->
                            val width = size.width
                            if (offset.x < width / 2f) {
                                if (playerLane > 0) playerLane--
                            } else {
                                if (playerLane < 2) playerLane++
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Road Borders & 3 Lanes
                val laneWidth = canvasWidth / 3f

                // Draw Dark Asphalt Road
                drawRect(
                    color = Color(0xFF14161C),
                    size = Size(canvasWidth, canvasHeight)
                )

                // Animated Yellow Lane Dividers
                val dashHeight = 30.dp.toPx()
                val dashGap = 20.dp.toPx()
                var currentY = (roadOffset * (dashHeight + dashGap)) - dashHeight

                while (currentY < canvasHeight) {
                    // Lane 1 divider
                    drawRect(
                        color = SafetyAmber.copy(alpha = 0.5f),
                        topLeft = Offset(laneWidth - 2.dp.toPx(), currentY),
                        size = Size(4.dp.toPx(), dashHeight)
                    )
                    // Lane 2 divider
                    drawRect(
                        color = SafetyAmber.copy(alpha = 0.5f),
                        topLeft = Offset(laneWidth * 2 - 2.dp.toPx(), currentY),
                        size = Size(4.dp.toPx(), dashHeight)
                    )
                    currentY += dashHeight + dashGap
                }

                // Draw Entities (Cars, Oil, Fuel)
                for (item in entities) {
                    val itemX = laneWidth * item.lane + (laneWidth / 2f)
                    val itemY = canvasHeight * item.yNormalized

                    when (item.type) {
                        EntityType.CAR -> {
                            // Oncoming Vehicle Body
                            drawRect(
                                color = CrimsonRed,
                                topLeft = Offset(itemX - 18.dp.toPx(), itemY - 24.dp.toPx()),
                                size = Size(36.dp.toPx(), 48.dp.toPx())
                            )
                            // Windshield
                            drawRect(
                                color = Color.Cyan.copy(alpha = 0.8f),
                                topLeft = Offset(itemX - 14.dp.toPx(), itemY - 10.dp.toPx()),
                                size = Size(28.dp.toPx(), 12.dp.toPx())
                            )
                        }
                        EntityType.OIL_SLICK -> {
                            // Black Oval Oil Slick
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.8f),
                                radius = 22.dp.toPx(),
                                center = Offset(itemX, itemY)
                            )
                        }
                        EntityType.FUEL_CANISTER -> {
                            // Green Fuel Canister
                            drawRect(
                                color = HighVisLime,
                                topLeft = Offset(itemX - 12.dp.toPx(), itemY - 16.dp.toPx()),
                                size = Size(24.dp.toPx(), 32.dp.toPx())
                            )
                        }
                        EntityType.NITRO -> {
                            // Amber Nitro Tank
                            drawRect(
                                color = SafetyAmber,
                                topLeft = Offset(itemX - 10.dp.toPx(), itemY - 18.dp.toPx()),
                                size = Size(20.dp.toPx(), 36.dp.toPx())
                            )
                        }
                    }
                }

                // Draw Player Motorcycle Sprite
                val playerX = laneWidth * playerLane + (laneWidth / 2f)
                val playerY = canvasHeight * 0.85f

                // Bike Chassis
                drawRect(
                    color = SafetyAmber,
                    topLeft = Offset(playerX - 10.dp.toPx(), playerY - 25.dp.toPx()),
                    size = Size(20.dp.toPx(), 50.dp.toPx())
                )
                // Front Tire
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(playerX - 5.dp.toPx(), playerY - 35.dp.toPx()),
                    size = Size(10.dp.toPx(), 14.dp.toPx())
                )
                // Rider Helmet
                drawCircle(
                    color = Color.White,
                    radius = 9.dp.toPx(),
                    center = Offset(playerX, playerY)
                )
            }

            // Touch Guidance Overlays
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "◀ TAP LEFT",
                        color = TextSecondary.copy(alpha = 0.3f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "TAP RIGHT ▶",
                        color = TextSecondary.copy(alpha = 0.3f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }

            // Crash Overlay Dialog
            if (isCrashed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AsphaltBlack.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CarbonDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonRed)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = CrimsonRed,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "SLIPPED ON OIL!",
                                color = CrimsonRed,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Distance Ridden: $scoreMeters m",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { isCrashed = false },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafetyAmber,
                                    contentColor = AsphaltBlack
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reboot Engine",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
