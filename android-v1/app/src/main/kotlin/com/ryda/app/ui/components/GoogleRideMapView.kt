package com.ryda.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.ui.theme.AsphaltBlack
import com.ryda.app.ui.theme.CarbonDark
import com.ryda.app.ui.theme.ElectricCyan
import com.ryda.app.ui.theme.HighVisLime
import com.ryda.app.ui.theme.SafetyAmber
import com.ryda.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private const val RYDA_DARK_MAP_STYLE = """
[
  {
    "elementType": "geometry",
    "stylers": [{"color": "#181b20"}]
  },
  {
    "elementType": "labels.icon",
    "stylers": [{"visibility": "off"}]
  },
  {
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#8b949e"}]
  },
  {
    "elementType": "labels.text.stroke",
    "stylers": [{"color": "#0f1115"}]
  },
  {
    "featureType": "administrative",
    "elementType": "geometry",
    "stylers": [{"color": "#333b47"}]
  },
  {
    "featureType": "poi",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#8b949e"}]
  },
  {
    "featureType": "poi.park",
    "elementType": "geometry",
    "stylers": [{"color": "#13231a"}]
  },
  {
    "featureType": "road",
    "elementType": "geometry.fill",
    "stylers": [{"color": "#282d37"}]
  },
  {
    "featureType": "road",
    "elementType": "geometry.stroke",
    "stylers": [{"color": "#1b1e24"}]
  },
  {
    "featureType": "road",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#c9d1d9"}]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry.fill",
    "stylers": [{"color": "#3a414f"}]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry.stroke",
    "stylers": [{"color": "#21262d"}]
  },
  {
    "featureType": "transit",
    "elementType": "geometry",
    "stylers": [{"color": "#24292f"}]
  },
  {
    "featureType": "water",
    "elementType": "geometry",
    "stylers": [{"color": "#0d1b2a"}]
  },
  {
    "featureType": "water",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#58a6ff"}]
  }
]
"""

@Composable
fun GoogleRideMapView(
    points: List<GpsPoint>,
    modifier: Modifier = Modifier,
    onToggleFallback: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState()
    var isMapLoaded by remember { mutableStateOf(false) }
    var showElevationProfile by remember { mutableStateOf(false) }

    val displayPoints = remember(points) {
        if (points.size > 1000) {
            com.ryda.app.core.sharing.RouteSimplifier.simplifyRoute(points)
        } else {
            points
        }
    }

    val latLngs = remember(displayPoints) {
        displayPoints.map { LatLng(it.latitude, it.longitude) }
    }

    val routeBounds = remember(latLngs) {
        if (latLngs.size >= 2) {
            val builder = LatLngBounds.builder()
            for (latLng in latLngs) {
                builder.include(latLng)
            }
            builder.build()
        } else null
    }

    val mapProperties = remember {
        MapProperties(
            mapType = MapType.NORMAL,
            mapStyleOptions = MapStyleOptions(RYDA_DARK_MAP_STYLE),
            isMyLocationEnabled = false
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = true,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            rotationGesturesEnabled = true,
            scrollGesturesEnabled = true,
            tiltGesturesEnabled = true,
            zoomGesturesEnabled = true
        )
    }

    fun fitCameraToBounds(animate: Boolean = true) {
        if (routeBounds != null) {
            coroutineScope.launch {
                val update = CameraUpdateFactory.newLatLngBounds(routeBounds, 120)
                if (animate) {
                    cameraPositionState.animate(update, 800)
                } else {
                    cameraPositionState.move(update)
                }
            }
        } else if (latLngs.isNotEmpty()) {
            coroutineScope.launch {
                val update = CameraUpdateFactory.newLatLngZoom(latLngs.first(), 15f)
                if (animate) cameraPositionState.animate(update, 800) else cameraPositionState.move(update)
            }
        }
    }

    LaunchedEffect(isMapLoaded, routeBounds) {
        if (isMapLoaded) {
            fitCameraToBounds(animate = false)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AsphaltBlack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            if (points.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No GPS points recorded for this ride",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = mapProperties,
                    uiSettings = mapUiSettings,
                    onMapLoaded = {
                        isMapLoaded = true
                        fitCameraToBounds(animate = true)
                    }
                ) {
                    // Route Polyline Glow Layer
                    if (latLngs.size >= 2) {
                        Polyline(
                            points = latLngs,
                            color = ElectricCyan.copy(alpha = 0.45f),
                            width = 20f,
                            geodesic = true
                        )

                        // Main Safety Amber Polyline
                        Polyline(
                            points = latLngs,
                            color = SafetyAmber,
                            width = 10f,
                            geodesic = true
                        )

                        // Start Marker (Green Pin)
                        Marker(
                            state = MarkerState(position = latLngs.first()),
                            title = "Ride Start",
                            snippet = "First GPS fix",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                        )

                        // Finish Marker (Red Pin)
                        Marker(
                            state = MarkerState(position = latLngs.last()),
                            title = "Ride Finish",
                            snippet = "Final destination",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        )
                    } else if (latLngs.size == 1) {
                        Marker(
                            state = MarkerState(position = latLngs.first()),
                            title = "Single Point",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                        )
                    }
                }

                // Floating Action Controls (Top-End)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Elevation Toggle Pill
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CarbonDark.copy(alpha = 0.85f))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { showElevationProfile = !showElevationProfile },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terrain,
                                contentDescription = "Toggle Elevation Profile",
                                tint = if (showElevationProfile) HighVisLime else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Fit Route Camera Pill
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CarbonDark.copy(alpha = 0.85f))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { fitCameraToBounds(animate = true) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CropFree,
                                contentDescription = "Fit Route",
                                tint = SafetyAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Switch to Offline Canvas Fallback Pill
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CarbonDark.copy(alpha = 0.85f))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = onToggleFallback,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Switch to Canvas Visualizer",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Bottom Badge: Google Maps Active + Point Count
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .background(AsphaltBlack.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Google Maps • ${points.size} fixes",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Elevation Profile Strip (Preserved)
        if (showElevationProfile && points.size >= 2) {
            ElevationProfileView(points = points)
        }
    }
}
