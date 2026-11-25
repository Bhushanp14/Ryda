package com.ryda.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.ui.theme.*

@Composable
fun BatteryGuidanceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = Build.BRAND.lowercase()
    val model = Build.MODEL

    val (oemName, steps) = when {
        manufacturer.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> {
            "Xiaomi / Redmi / POCO (MIUI / HyperOS)" to listOf(
                "1. Go to App Info & toggle ON 'Autostart'.",
                "2. Tap 'Battery saver' & change from 'Recommended' to 'No restrictions'.",
                "3. In Recent Apps overview, press and hold RYDA to lock the app."
            )
        }
        manufacturer.contains("samsung") -> {
            "Samsung (One UI)" to listOf(
                "1. Go to Settings &rarr; Apps &rarr; RYDA &rarr; Battery &rarr; Select 'Unrestricted'.",
                "2. Settings &rarr; Device Care &rarr; Battery &rarr; Background usage limits.",
                "3. Add RYDA to the 'Never sleeping apps' list."
            )
        }
        manufacturer.contains("oneplus") || manufacturer.contains("oppo") || brand.contains("realme") -> {
            "OnePlus / Oppo / Realme (OxygenOS / ColorOS)" to listOf(
                "1. Settings &rarr; Battery &rarr; App battery management &rarr; RYDA.",
                "2. Turn ON both 'Allow foreground activity' and 'Allow background activity'.",
                "3. Advanced Settings &rarr; Optimize battery use &rarr; Set to 'Don't optimize'."
            )
        }
        manufacturer.contains("vivo") || brand.contains("iqoo") -> {
            "Vivo / iQOO (Funtouch OS)" to listOf(
                "1. Settings &rarr; Battery &rarr; High background power consumption &rarr; Allow RYDA.",
                "2. Settings &rarr; Permission management &rarr; Autostart &rarr; Enable RYDA."
            )
        }
        else -> {
            "Android (${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }})" to listOf(
                "1. Open App Info &rarr; App battery usage.",
                "2. Change setting from 'Optimized' to 'Unrestricted'."
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = SafetyAmber,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "OEM Battery Guidance",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Detected: $oemName",
                        color = ElectricCyan,
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Recommended Steps for $model:",
                            color = SafetyAmber,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        steps.forEach { step ->
                            Text(
                                text = step,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                // PRD §14 & Phase 1 Required Notice
                Surface(
                    color = AsphaltBlack,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Note: Battery optimization varies by device model and OS update. No whitelist can 100% guarantee uninterrupted recording if the OS enters critical memory pressure, but these settings prevent premature background closure on long rides.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Quick Action Buttons
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = TextPrimary)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open App Settings (Autostart / Permissions)", fontSize = 12.sp)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack)
                    ) {
                        Text("Request Unrestricted Battery", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = TextPrimary)
            ) {
                Text("Got It")
            }
        }
    )
}
