package com.ryda.app.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.core.auth.TokenDataStore
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PermissionExplainerScreen(
    tokenDataStore: TokenDataStore,
    onPermissionsHandled: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Build the list of permissions we need
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Regardless of grant result — user has seen the explainer, move on
        scope.launch {
            tokenDataStore.setPermissionsExplained(true)
            onPermissionsHandled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(24.dp))

        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RYDA", fontSize = 36.sp, fontWeight = FontWeight.Black, color = SafetyAmber)
            Spacer(Modifier.height(8.dp))
            Text(
                "Needs a few permissions\nto track your rides",
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = OnSurfacePrimary, textAlign = TextAlign.Center
            )
        }

        // Permission cards
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PermissionCard(
                icon = Icons.Filled.LocationOn,
                iconTint = SafetyAmber,
                title = "Background Location",
                description = "Records your GPS route in the background even while you use Google Maps or other apps. RYDA never shares your location with other users."
            )
            PermissionCard(
                icon = Icons.Filled.Speed,
                iconTint = ElectricCyan,
                title = "Activity Recognition",
                description = "Detects when you're on a motorcycle to auto-pause when you stop, saving battery and reducing noise in your route data."
            )
            PermissionCard(
                icon = Icons.Filled.Notifications,
                iconTint = HighVisLime,
                title = "Notifications",
                description = "Shows a persistent notification while recording so you can see live speed and distance, and so Android doesn't kill the tracking service."
            )
        }

        // CTA
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { launcher.launch(permissions) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Grant Access", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            TextButton(onClick = {
                scope.launch {
                    tokenDataStore.setPermissionsExplained(true)
                    onPermissionsHandled()
                }
            }) {
                Text("Skip for now", color = OnSurfaceMuted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    description: String
) {
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, color = OnSurfacePrimary, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(description, color = OnSurfaceMuted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}
