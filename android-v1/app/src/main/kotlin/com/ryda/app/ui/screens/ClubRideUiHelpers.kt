package com.ryda.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val displayFmt = DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm")
    .withZone(ZoneId.systemDefault())

fun formatClubRideTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "—"
    return try {
        displayFmt.format(Instant.parse(isoString))
    } catch (_: Exception) {
        isoString.take(16).replace("T", " ")
    }
}

fun sessionStatusColor(status: String): Color = when (status.uppercase()) {
    "SCHEDULED" -> ElectricCyan
    "ACTIVE" -> HighVisLime
    "COMPLETED" -> ForestGreen
    "EXPIRED" -> WarningGold
    "CANCELLED" -> CrimsonRed
    else -> TextSecondary
}

fun participantStatusColor(status: String): Color = when (status.uppercase()) {
    "JOINED" -> ElectricCyan
    "READY" -> WarningGold
    "VERIFIED" -> ForestGreen
    "NOT_ATTENDING" -> CrimsonRed
    "REMOVED" -> TextTertiary
    else -> TextSecondary
}

@Composable
fun StatusPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
