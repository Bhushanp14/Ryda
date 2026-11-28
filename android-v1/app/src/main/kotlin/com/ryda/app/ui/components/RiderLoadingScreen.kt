package com.ryda.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.ui.theme.AsphaltBlack
import com.ryda.app.ui.theme.CarbonDark
import com.ryda.app.ui.theme.CrimsonRed
import com.ryda.app.ui.theme.HeadlineFontFamily
import com.ryda.app.ui.theme.HighVisLime
import com.ryda.app.ui.theme.MotoOrange
import com.ryda.app.ui.theme.SafetyAmber
import com.ryda.app.ui.theme.StatFontFamily
import com.ryda.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Tachometer Rev-Sweep Splash Screen for Ryda launch.
 * Features 0 -> 12,000 RPM sweep, shift light flash, Ryda brand logo hub, and engine status text.
 */
@Composable
fun RiderLoadingScreen(
    modifier: Modifier = Modifier,
    statusTextOverride: String? = null,
) {
    val rpmAnim = remember { Animatable(0f) }
    var shiftLightFlashing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("INITIALIZING TELEMETRY ENGINE...") }

    LaunchedEffect(Unit) {
        // 1. Initial Sweep 0 -> 12,000 RPM Redline
        statusText = "INITIALIZING TELEMETRY ENGINE..."
        rpmAnim.animateTo(
            targetValue = 12000f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )

        // 2. Shift Light Redline Flash
        shiftLightFlashing = true
        delay(250)
        statusText = "CHECKING GPS SENSORS..."
        delay(250)
        shiftLightFlashing = false

        // 3. Settle to Engine Idle (~1,200 RPM)
        rpmAnim.animateTo(
            targetValue = 1200f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
        statusText = "READY TO RIDE"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AsphaltBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Shift Light LED Bar at Top of Gauge
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { index ->
                    val isRedline = index >= 3
                    val activeColor = if (isRedline) CrimsonRed else SafetyAmber
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (shiftLightFlashing || (rpmAnim.value > (4000 * index))) activeColor
                                else activeColor.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Analog-Digital Tachometer Dial Gauge
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = (size.width / 2) - 20.dp.toPx()

                    // Background Ring Arc (135° to 405° = 270° span)
                    drawArc(
                        color = CarbonDark,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Active RPM Arc
                    val currentRpmFraction = (rpmAnim.value / 12000f).coerceIn(0f, 1f)
                    val sweep = 270f * currentRpmFraction
                    val arcBrush = Brush.sweepGradient(
                        0f to SafetyAmber,
                        0.8f to SafetyAmber,
                        1f to CrimsonRed
                    )

                    drawArc(
                        brush = arcBrush,
                        startAngle = 135f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Tick Marks
                    for (i in 0..12) {
                        val angleDeg = 135f + (i / 12f) * 270f
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val isRed = i >= 10
                        val tickColor = if (isRed) CrimsonRed else TextSecondary.copy(alpha = 0.6f)
                        val innerR = radius - (if (i % 2 == 0) 14.dp.toPx() else 8.dp.toPx())
                        val outerR = radius - 2.dp.toPx()

                        val startX = center.x + innerR * cos(angleRad).toFloat()
                        val startY = center.y + innerR * sin(angleRad).toFloat()
                        val endX = center.x + outerR * cos(angleRad).toFloat()
                        val endY = center.y + outerR * sin(angleRad).toFloat()

                        drawLine(
                            color = tickColor,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = if (i % 2 == 0) 3.dp.toPx() else 1.5f.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Needle
                    val needleAngleDeg = 135f + (rpmAnim.value / 12000f) * 270f
                    val needleRad = Math.toRadians(needleAngleDeg.toDouble())
                    val needleLength = radius - 18.dp.toPx()
                    val needleEnd = Offset(
                        center.x + needleLength * cos(needleRad).toFloat(),
                        center.y + needleLength * sin(needleRad).toFloat()
                    )

                    drawLine(
                        color = CrimsonRed,
                        start = center,
                        end = needleEnd,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Center Hub with Ryda "R" Brand Logo
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(AsphaltBlack)
                        .border(2.dp, SafetyAmber, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MotoOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "R",
                            color = AsphaltBlack,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = HeadlineFontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Digital RPM Counter Readout
            Text(
                text = rpmAnim.value.toInt().toString(),
                color = SafetyAmber,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = StatFontFamily
            )
            Text(
                text = "ENGINE RPM",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Ignition Status Microcopy
            Text(
                text = statusTextOverride ?: statusText,
                color = HighVisLime,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = StatFontFamily,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/**
 * Reusable Spinning Brake Rotor Loader for sub-loading states.
 */
@Composable
fun SpinningRotorLoader(
    modifier: Modifier = Modifier,
    sizeDp: Int = 40,
    color: Color = SafetyAmber,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotor_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(
        modifier = modifier
            .size(sizeDp.dp)
            .rotate(rotation)
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (size.width / 2) - 4.dp.toPx()

        // Outer Vented Rotor Ring
        drawCircle(
            color = color,
            radius = radius,
            style = Stroke(width = 4.dp.toPx())
        )

        // 5 Rotor Vents
        repeat(5) { i ->
            val angleRad = Math.toRadians((i * 72.0))
            val innerOffset = Offset(
                center.x + (radius * 0.35f) * cos(angleRad).toFloat(),
                center.y + (radius * 0.35f) * sin(angleRad).toFloat()
            )
            val outerOffset = Offset(
                center.x + radius * cos(angleRad).toFloat(),
                center.y + radius * sin(angleRad).toFloat()
            )
            drawLine(
                color = color,
                start = innerOffset,
                end = outerOffset,
                strokeWidth = 3.dp.toPx()
            )
        }
    }
}
