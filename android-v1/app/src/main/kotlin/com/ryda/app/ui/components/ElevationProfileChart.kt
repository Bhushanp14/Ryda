package com.ryda.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.core.utils.ElevationDataPoint
import com.ryda.app.core.utils.RideElevationAnalysis
import com.ryda.app.ui.theme.*

@Composable
fun ElevationProfileChart(
    analysis: RideElevationAnalysis,
    profilePoints: List<ElevationDataPoint>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                Text(
                    text = "ELEVATION PROFILE",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (analysis.isElevationAvailable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Highest: ${analysis.highestElevationMeters.toInt()} m",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Lowest: ${analysis.lowestElevationMeters.toInt()} m",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!analysis.isElevationAvailable || profilePoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(AsphaltBlack, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Elevation data quality insufficient for profile graph",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                val pointsToDraw = remember(profilePoints) {
                    com.ryda.app.core.sharing.RouteSimplifier.downsampleForChart(profilePoints, 500) { it.altitudeMeters }
                }
                val minAlt = (analysis.lowestElevationMeters - 10.0).coerceAtLeast(0.0)
                val maxAlt = (analysis.highestElevationMeters + 10.0).coerceAtLeast(minAlt + 20.0)
                val altRange = (maxAlt - minAlt).coerceAtLeast(1.0)

                val maxDist = (pointsToDraw.lastOrNull()?.distanceKm ?: 1.0).coerceAtLeast(0.1)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(AsphaltBlack, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    val width = size.width
                    val height = size.height

                    // Grid Lines
                    val gridSteps = 4
                    for (i in 0..gridSteps) {
                        val y = height - (height * i / gridSteps)
                        drawLine(
                            color = Color(0xFF222831),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val path = Path()
                    val fillPath = Path()

                    pointsToDraw.forEachIndexed { idx, pt ->
                        val x = (pt.distanceKm / maxDist).toFloat() * width
                        val y = height - (height * ((pt.altitudeMeters - minAlt) / altRange)).toFloat().coerceIn(0f, height)

                        if (idx == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, height)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    if (pointsToDraw.isNotEmpty()) {
                        val lastX = (pointsToDraw.last().distanceKm / maxDist).toFloat() * width
                        fillPath.lineTo(lastX, height)
                        fillPath.close()

                        // Draw Gradient Fill
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF).copy(alpha = 0.35f),
                                    Color(0xFF00E5FF).copy(alpha = 0.02f)
                                )
                            )
                        )

                        // Draw Curve Line
                        drawPath(
                            path = path,
                            color = Color(0xFF00E5FF),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}
