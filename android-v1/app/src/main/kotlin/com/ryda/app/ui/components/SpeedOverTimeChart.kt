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
import com.ryda.app.core.utils.SpeedOverTimeAnalysis
import com.ryda.app.ui.theme.*

@Composable
fun SpeedOverTimeChart(
    analysis: SpeedOverTimeAnalysis,
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
                    text = "SPEED OVER TIME",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MotoOrange)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Peak: ${analysis.maxSpeedKmh.toInt()} km/h",
                            color = MotoOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ElectricCyan)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Avg: ${analysis.averageSpeedKmh.toInt()} km/h",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (analysis.dataPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(AsphaltBlack, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No speed graph points recorded", color = TextSecondary, fontSize = 12.sp)
                }
            } else {
                val points = remember(analysis.dataPoints) {
                    com.ryda.app.core.sharing.RouteSimplifier.downsampleForChart(analysis.dataPoints, 500) { it.speedKmh }
                }
                val maxSpeed = (analysis.maxSpeedKmh * 1.15).coerceAtLeast(40.0)
                val maxTime = (points.lastOrNull()?.elapsedSeconds ?: 1L).coerceAtLeast(1L)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(AsphaltBlack, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    val width = size.width
                    val height = size.height

                    // Background Grid Lines
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

                    // Average speed reference line
                    val avgY = height - (height * (analysis.averageSpeedKmh / maxSpeed)).toFloat().coerceIn(0f, height)
                    drawLine(
                        color = Color(0xFF00E5FF).copy(alpha = 0.6f),
                        start = Offset(0f, avgY),
                        end = Offset(width, avgY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    // Curve & Gradient Fill
                    val path = Path()
                    val fillPath = Path()

                    points.forEachIndexed { idx, pt ->
                        val x = (pt.elapsedSeconds.toFloat() / maxTime) * width
                        val y = height - (height * (pt.speedKmh / maxSpeed)).toFloat().coerceIn(0f, height)

                        if (idx == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, height)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    if (points.isNotEmpty()) {
                        val lastX = (points.last().elapsedSeconds.toFloat() / maxTime) * width
                        fillPath.lineTo(lastX, height)
                        fillPath.close()

                        // Draw Area Gradient Fill
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFF5722).copy(alpha = 0.35f),
                                    Color(0xFFFF5722).copy(alpha = 0.02f)
                                )
                            )
                        )

                        // Draw Speed Curve Line
                        drawPath(
                            path = path,
                            color = Color(0xFFFF5722),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Draw Peak Max Speed Dot
                        val maxPt = points.maxByOrNull { it.speedKmh }
                        if (maxPt != null && maxPt.speedKmh > 0) {
                            val peakX = (maxPt.elapsedSeconds.toFloat() / maxTime) * width
                            val peakY = height - (height * (maxPt.speedKmh / maxSpeed)).toFloat().coerceIn(0f, height)

                            drawCircle(
                                color = Color(0xFFFF5722),
                                radius = 6.dp.toPx(),
                                center = Offset(peakX, peakY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = Offset(peakX, peakY)
                            )
                        }
                    }
                }
            }
        }
    }
}
