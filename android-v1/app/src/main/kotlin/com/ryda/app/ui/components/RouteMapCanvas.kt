package com.ryda.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.ui.theme.AsphaltBlack
import com.ryda.app.ui.theme.BorderSubtle
import com.ryda.app.ui.theme.CarbonDark
import com.ryda.app.ui.theme.CrimsonRed
import com.ryda.app.ui.theme.ElectricCyan
import com.ryda.app.ui.theme.ForestGreen
import com.ryda.app.ui.theme.HighVisLime
import com.ryda.app.ui.theme.SafetyAmber
import com.ryda.app.ui.theme.TextPrimary
import com.ryda.app.ui.theme.TextSecondary
import kotlin.math.max
import kotlin.math.min

@Composable
fun RouteMapCanvas(
    points: List<GpsPoint>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(AsphaltBlack)
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var showElevationProfile by remember { mutableStateOf(false) }

    val displayPoints = remember(points) {
        if (points.size > 1000) {
            com.ryda.app.core.sharing.RouteSimplifier.simplifyRoute(points)
        } else {
            points
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 6.0f)
                        panOffset += pan
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (displayPoints.size < 2) {
                Text(
                    text = if (displayPoints.isEmpty()) "No GPS points recorded" else "Only 1 GPS point recorded",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    val width = size.width
                    val height = size.height

                    var minLat = Double.MAX_VALUE
                    var maxLat = -Double.MAX_VALUE
                    var minLon = Double.MAX_VALUE
                    var maxLon = -Double.MAX_VALUE

                    for (p in displayPoints) {
                        minLat = min(minLat, p.latitude)
                        maxLat = max(maxLat, p.latitude)
                        minLon = min(minLon, p.longitude)
                        maxLon = max(maxLon, p.longitude)
                    }

                    val latSpan = max(maxLat - minLat, 0.0001)
                    val lonSpan = max(maxLon - minLon, 0.0001)

                    // Draw subtle grid lines
                    val gridColor = BorderSubtle.copy(alpha = 0.35f)
                    val stepX = width / 4
                    for (i in 1..3) {
                        drawLine(
                            color = gridColor,
                            start = Offset(stepX * i, 0f),
                            end = Offset(stepX * i, height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    val stepY = height / 4
                    for (i in 1..3) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, stepY * i),
                            end = Offset(width, stepY * i),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    fun toScreenOffset(p: GpsPoint): Offset {
                        val normX = ((p.longitude - minLon) / lonSpan).toFloat()
                        val normY = (1f - ((p.latitude - minLat) / latSpan).toFloat())

                        val centerX = width / 2f
                        val centerY = height / 2f

                        // Apply pan and zoom around center
                        val scaledX = (normX * width - centerX) * scale + centerX + panOffset.x
                        val scaledY = (normY * height - centerY) * scale + centerY + panOffset.y

                        return Offset(scaledX, scaledY)
                    }

                    val routePath = Path()
                    val firstOffset = toScreenOffset(displayPoints.first())
                    routePath.moveTo(firstOffset.x, firstOffset.y)

                    for (i in 1 until displayPoints.size) {
                        val offset = toScreenOffset(displayPoints[i])
                        routePath.lineTo(offset.x, offset.y)
                    }

                    // Glow line underneath
                    drawPath(
                        path = routePath,
                        color = ElectricCyan.copy(alpha = 0.35f),
                        style = Stroke(
                            width = (10.dp.toPx() * scale.coerceIn(0.8f, 2.0f)),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Main Route Polyline
                    drawPath(
                        path = routePath,
                        color = SafetyAmber,
                        style = Stroke(
                            width = (4.dp.toPx() * scale.coerceIn(0.8f, 2.0f)),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Start Point (Green circle)
                    val startOffset = toScreenOffset(displayPoints.first())
                    drawCircle(color = Color.White, radius = 8.dp.toPx(), center = startOffset)
                    drawCircle(color = ForestGreen, radius = 5.dp.toPx(), center = startOffset)

                    // End Point (Red / Finish circle)
                    val endOffset = toScreenOffset(displayPoints.last())
                    drawCircle(color = Color.White, radius = 8.dp.toPx(), center = endOffset)
                    drawCircle(color = CrimsonRed, radius = 5.dp.toPx(), center = endOffset)
                }

                // Interactive Control Pill at Top Right (Reset Zoom & Elevation Toggle)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Elevation Toggle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CarbonDark.copy(alpha = 0.85f))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = { showElevationProfile = !showElevationProfile }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Terrain,
                                contentDescription = "Toggle Elevation Profile",
                                tint = if (showElevationProfile) HighVisLime else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Reset Center/Zoom Button
                    if (scale != 1f || panOffset != Offset.Zero) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CarbonDark.copy(alpha = 0.85f))
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = { scale = 1f; panOffset = Offset.Zero }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.CropFree,
                                    contentDescription = "Fit Route",
                                    tint = SafetyAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Legend overlay at bottom right
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(AsphaltBlack.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Pinch to zoom • ${points.size} fixes",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Optional Elevation Profile Strip
        if (showElevationProfile && points.size >= 2) {
            ElevationProfileView(points = points)
        }
    }
}

@Composable
fun ElevationProfileView(points: List<GpsPoint>) {
    val altitudes = points.map { it.altitude }
    val minAlt = altitudes.minOrNull() ?: 0.0
    val maxAlt = altitudes.maxOrNull() ?: 0.0
    val altRange = max(maxAlt - minAlt, 1.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonDark)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "ELEVATION PROFILE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(text = "Min ${minAlt.toInt()}m • Max ${maxAlt.toInt()}m", color = HighVisLime, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            val width = size.width
            val height = size.height

            val profilePath = Path()
            val fillPath = Path()

            val stepX = width / max(points.size - 1, 1).toFloat()

            points.forEachIndexed { index, point ->
                val x = index * stepX
                val normY = 1f - ((point.altitude - minAlt) / altRange).toFloat()
                val y = normY * (height - 8.dp.toPx()) + 4.dp.toPx()

                if (index == 0) {
                    profilePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    profilePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (index == points.size - 1) {
                    fillPath.lineTo(x, height)
                    fillPath.close()
                }
            }

            // Fill gradient under elevation curve
            drawPath(
                path = fillPath,
                color = ElectricCyan.copy(alpha = 0.2f)
            )

            // Elevation profile line
            drawPath(
                path = profilePath,
                color = ElectricCyan,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
