package com.ryda.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.ui.theme.CarbonDark
import com.ryda.app.ui.theme.ElectricCyan
import com.ryda.app.ui.theme.SafetyAmber
import com.ryda.app.ui.theme.TextSecondary

/**
 * RideRouteView — Smart container that picks the best route visualizer.
 *
 * Default: Google Maps SDK (rich road tiles, satellite imagery, zoom/pan).
 * Fallback: Native Compose Canvas route visualizer (works fully offline).
 *
 * Automatically falls back to Canvas if Google Play Services is unavailable.
 * User can manually toggle between the two via the Layers button overlay.
 */
@Composable
fun RideRouteView(
    points: List<GpsPoint>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasPlayServices = remember { isGooglePlayServicesAvailable(context) }

    // Default to Google Maps when Play Services is available; Canvas otherwise
    var useGoogleMap by remember { mutableStateOf(hasPlayServices) }

    if (useGoogleMap && hasPlayServices) {
        GoogleRideMapView(
            points = points,
            modifier = modifier,
            onToggleFallback = { useGoogleMap = false }
        )
    } else {
        // Offline Canvas fallback with a toggle button to switch back to Google Maps
        Box(modifier = modifier) {
            RouteMapCanvas(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            )

            // Overlay: Switch-to-Google-Maps pill (only if Play Services is available)
            if (hasPlayServices) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CarbonDark.copy(alpha = 0.85f))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { useGoogleMap = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Switch to Google Maps",
                                tint = SafetyAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Check whether Google Play Services is installed and functional on this device.
 * Returns false on devices without GMS (Huawei, Amazon Fire, custom ROMs, etc.).
 */
private fun isGooglePlayServicesAvailable(context: Context): Boolean {
    return try {
        val availability = GoogleApiAvailability.getInstance()
        availability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    } catch (_: Exception) {
        false
    }
}
