package com.ryda.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff

@Composable
fun OfflineStateView(
    title: String = "You're Currently Offline",
    description: String = "Connect to the internet to view live community rides, stories, and discussions.",
    icon: ImageVector = Icons.Default.CloudOff,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    RoadRashMinigame(
        onRetryConnection = onRetry,
        modifier = modifier
    )
}
