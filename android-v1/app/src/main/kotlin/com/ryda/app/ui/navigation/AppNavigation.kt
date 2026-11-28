package com.ryda.app.ui.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ryda.app.core.auth.AuthRepository
import com.ryda.app.core.auth.TokenDataStore
import com.ryda.app.core.models.Club
import com.ryda.app.core.models.Ride
import com.ryda.app.core.service.ClubRideShutdownCoordinator
import com.ryda.app.core.models.RideStatus
import com.ryda.app.core.service.LiveLocationService
import com.ryda.app.core.service.RideTrackingService
import com.ryda.app.data.repository.RydaRepository
import com.ryda.app.ui.components.NotificationDrawer
import com.ryda.app.ui.screens.*
import com.ryda.app.ui.screens.auth.LoginScreen
import com.ryda.app.ui.screens.auth.RegisterScreen
import com.ryda.app.ui.screens.onboarding.PermissionExplainerScreen
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.launch

enum class NavTab { HOME, COMMUNITY, RECORD, CLUBS, PROFILE }

/** Top-level app destination */
private enum class AppDest {
    LOADING,         // DataStore not yet read
    LOGIN,
    REGISTER,
    PERMISSION_EXPLAINER,
    MAIN
}

// Process-level flag tracking if the cold-start ignition splash has already run in this process session
private var isColdStartDone = false

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val repository = remember { RydaRepository.getInstance(context) }
    val tokenDataStore = remember { TokenDataStore.getInstance(context) }
    val authRepository = remember { AuthRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    // ── DataStore state ───────────────────────────────────────────────────────
    val tokenFlow = tokenDataStore.tokenFlow.collectAsState(initial = null)
    val permExplainedFlow = tokenDataStore.permissionsExplainedFlow.collectAsState(initial = null)

    // ── Destination state ─────────────────────────────────────────────────────
    var dest by remember { mutableStateOf(if (isColdStartDone) AppDest.MAIN else AppDest.LOADING) }

    // Once DataStore values are available, pick the right starting screen
    LaunchedEffect(tokenFlow.value, permExplainedFlow.value) {
        val permExplained = permExplainedFlow.value ?: return@LaunchedEffect // still loading
        val token = tokenFlow.value  // null = not logged in

        val targetDest = when {
            // Not seen permission explainer yet → show it first (once ever)
            !permExplained -> AppDest.PERMISSION_EXPLAINER
            // No token → go to login
            token == null -> AppDest.LOGIN
            // All good → main app
            else -> AppDest.MAIN
        }

        if (!isColdStartDone) {
            // Cold start: Show splash animation for 2 seconds
            kotlinx.coroutines.delay(2000)
            isColdStartDone = true
            dest = targetDest
        } else {
            // Warm start / Resume from background: Skip splash completely
            dest = targetDest
        }
    }

    // ── App-level repos ───────────────────────────────────────────────────────
    val rides by repository.completedRides.collectAsState()
    val motorcycles by repository.motorcycles.collectAsState()
    val profile by repository.riderProfile.collectAsState()
    val trackingState by RideTrackingService.trackingState.collectAsState()

    var currentTab by remember { mutableStateOf(NavTab.HOME) }
    var selectedRideId by remember { mutableStateOf<String?>(null) }
    var showShareStudio by remember { mutableStateOf(false) }
    var isHudOpen by remember { mutableStateOf(false) }
    var showAddPhotosForRideId by remember { mutableStateOf<String?>(null) }
    var showRideHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showGarage by remember { mutableStateOf(false) }
    var selectedGarageBikeId by remember { mutableStateOf<String?>(null) }
    var showAchievements by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }

    // Crash recovery state
    var incompleteRide by remember { mutableStateOf<Ride?>(null) }
    LaunchedEffect(trackingState.isTracking, rides) {
        if (!trackingState.isTracking) {
            incompleteRide = repository.getIncompleteRide()
        } else {
            incompleteRide = null
        }
    }

    // Auto-Sync & Account Data Restore Trigger on App Launch
    LaunchedEffect(dest) {
        if (dest == AppDest.MAIN) {
            repository.restoreAccountDataFromCloud()
            com.ryda.app.core.sync.SyncCoordinator.getInstance(context).triggerSync()
        }
    }

    // Battery optimization state
    var showBatteryOptimizationPrompt by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) == false
            } else false
        )
    }

    // Pre-flight check warning dialogs
    var showLowStorageDialog by remember { mutableStateOf(false) }
    var showGpsDisabledDialog by remember { mutableStateOf(false) }

    val primaryBike = remember(motorcycles) {
        motorcycles.firstOrNull { it.isPrimary } ?: motorcycles.firstOrNull()
    }

    // ── Notification state ─────────────────────────────────────────────────────
    var showNotificationDrawer by remember { mutableStateOf(false) }
    var unreadNotificationCount by remember { mutableStateOf(0) }

    // Poll unread count when on MAIN screen
    LaunchedEffect(dest) {
        if (dest == AppDest.MAIN) {
            try {
                val notifs = repository.getNotifications()
                unreadNotificationCount = notifs?.notifications?.count { !it.isRead } ?: 0
            } catch (_: Exception) {}
        }
    }

    // ── Club navigation state ──────────────────────────────────────────────────
    var selectedClub by remember { mutableStateOf<Club?>(null) }
    var selectedClubRideSessionId by remember { mutableStateOf<String?>(null) }
    var activeGroupMapSession by remember { mutableStateOf<com.ryda.app.core.models.ClubRideSession?>(null) }
    var showCreateClubRide by remember { mutableStateOf(false) }
    var showClubRideAdmin by remember { mutableStateOf(false) }

    // Permission launcher used in main app (for starting ride without explainer)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            RideTrackingService.startRide(
                context = context,
                motorcycleId = primaryBike?.id,
                title = "${primaryBike?.displayName ?: "Motorcycle"} Ride",
                isSimulation = false
            )
            isHudOpen = true
        }
    }

    fun startRideWithCheck(isSimulation: Boolean, motorcycleId: String? = null) {
        val targetBikeId = motorcycleId ?: primaryBike?.id
        val targetBike = motorcycles.firstOrNull { it.id == targetBikeId } ?: primaryBike
        val title = "${targetBike?.displayName ?: "Motorcycle"} Ride"

        if (isSimulation) {
            RideTrackingService.startRide(
                context = context,
                motorcycleId = targetBikeId,
                title = "$title (Simulated)",
                isSimulation = true
            )
            isHudOpen = true
            return
        }

        // Pre-flight check 1: Free storage (PRD §15)
        try {
            val stat = StatFs(context.filesDir.path)
            val freeMb = stat.availableBytes / (1024 * 1024)
            if (freeMb < 50) {
                showLowStorageDialog = true
                return
            }
        } catch (_: Exception) {}

        // Pre-flight check 2: GPS hardware enabled (PRD §15)
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isLocationActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm?.isLocationEnabled == true
        } else {
            lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        }
        if (!isLocationActive) {
            showGpsDisabledDialog = true
            return
        }

        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            RideTrackingService.startRide(
                context = context,
                motorcycleId = targetBikeId,
                title = title,
                isSimulation = false
            )
            isHudOpen = true
        } else {
            val perms = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    // ── Route ─────────────────────────────────────────────────────────────────
    when (dest) {

        AppDest.LOADING -> {
            com.ryda.app.ui.components.RiderLoadingScreen()
        }

        AppDest.PERMISSION_EXPLAINER -> {
            PermissionExplainerScreen(
                tokenDataStore = tokenDataStore,
                onPermissionsHandled = {
                    dest = if (tokenFlow.value != null) AppDest.MAIN else AppDest.LOGIN
                }
            )
        }

        AppDest.LOGIN -> {
            LoginScreen(
                authRepository = authRepository,
                onLoginSuccess = { dest = AppDest.MAIN },
                onNavigateToRegister = { dest = AppDest.REGISTER },
                onSkipOffline = { dest = AppDest.MAIN }
            )
        }

        AppDest.REGISTER -> {
            RegisterScreen(
                authRepository = authRepository,
                onRegisterSuccess = { dest = AppDest.MAIN },
                onNavigateBack = { dest = AppDest.LOGIN }
            )
        }

        AppDest.MAIN -> {
            // Handle system back gestures / button within main app
            BackHandler(
                enabled = showShareStudio || showRideHistory || showSettings || showGarage ||
                    showAchievements || showTags || selectedRideId != null || selectedClub != null ||
                    isHudOpen || showAddPhotosForRideId != null || showNotificationDrawer || selectedGarageBikeId != null
            ) {
                when {
                    showNotificationDrawer -> showNotificationDrawer = false
                    selectedGarageBikeId != null -> selectedGarageBikeId = null
                    showShareStudio -> showShareStudio = false
                    showAddPhotosForRideId != null -> {
                        // Skip photo for ride, go to ride detail
                        val rideId = showAddPhotosForRideId
                        showAddPhotosForRideId = null
                        if (rideId != null) selectedRideId = rideId
                    }
                    selectedRideId != null -> {
                        selectedRideId = null
                        showShareStudio = false
                    }
                    selectedClub != null -> selectedClub = null
                    showSettings -> showSettings = false
                    showGarage -> showGarage = false
                    showAchievements -> showAchievements = false
                    showTags -> showTags = false
                    showRideHistory -> showRideHistory = false
                    isHudOpen -> isHudOpen = false
                }
            }

            Scaffold(
                containerColor = AsphaltBlack,
                bottomBar = {
                    if (!isHudOpen && selectedRideId == null) {
                        RydaBottomBar(
                            currentTab = currentTab,
                            isTracking = trackingState.isTracking,
                            onTabSelected = { tab ->
                                showGarage = false
                                showSettings = false
                                showAchievements = false
                                showTags = false
                                selectedRideId = null
                                showRideHistory = false
                                showAddPhotosForRideId = null
                                if (tab == NavTab.RECORD) {
                                    isHudOpen = true
                                } else {
                                    currentTab = tab
                                    // Reset club detail when switching away from CLUBS
                                    if (tab != NavTab.CLUBS) selectedClub = null
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when {
                        isHudOpen -> {
                            ActiveRideScreen(
                                trackingState = trackingState,
                                primaryMotorcycle = primaryBike,
                                motorcycles = motorcycles,
                                onStartTracking = { isSimulation, bikeId -> startRideWithCheck(isSimulation, bikeId) },
                                onPauseTracking = { RideTrackingService.pauseRide(context) },
                                onResumeTracking = { RideTrackingService.resumeRide(context) },
                                onFinishTracking = {
                                    val finishedId = trackingState.currentRideId
                                    RideTrackingService.finishRide(context)
                                    isHudOpen = false
                                    com.ryda.app.core.sync.SyncCoordinator.getInstance(context).triggerSync()
                                    if (finishedId != null) {
                                        showAddPhotosForRideId = finishedId
                                    } else {
                                        currentTab = NavTab.HOME
                                    }
                                },
                                onDiscardTracking = {
                                    RideTrackingService.discardRide(context)
                                    isHudOpen = false
                                },
                                onCloseHud = { isHudOpen = false }
                            )
                        }

                        showAddPhotosForRideId != null -> {
                            val finishedRide = remember(showAddPhotosForRideId, rides) {
                                rides.firstOrNull { it.id == showAddPhotosForRideId }
                                    ?: repository.getRideById(showAddPhotosForRideId ?: "")
                            }
                            if (finishedRide != null) {
                                val ridePoints = remember(finishedRide.id) {
                                    repository.getPointsForRide(finishedRide.id)
                                }
                                val bike = motorcycles.firstOrNull { it.id == finishedRide.motorcycleId } ?: primaryBike
                                CompletedRideSummaryScreen(
                                    ride = finishedRide,
                                    points = ridePoints,
                                    motorcycle = bike,
                                    onClose = {
                                        showAddPhotosForRideId = null
                                    },
                                    onViewDetail = { rideId ->
                                        selectedRideId = rideId
                                        showAddPhotosForRideId = null
                                    },
                                    onOpenShareStudio = { rideId ->
                                        selectedRideId = rideId
                                        showShareStudio = true
                                        showAddPhotosForRideId = null
                                    },
                                    onPhotoUpdated = { rideId, photoUri ->
                                        repository.updateRidePhoto(rideId, photoUri)
                                    },
                                    onPrivacyToggled = { rideId, isPriv ->
                                        repository.updateRidePrivacy(rideId, isPriv)
                                    },
                                    onPublishToCommunity = { r ->
                                        scope.launch {
                                            repository.createCommunityPost(
                                                com.ryda.app.core.models.PostCreateRequest(
                                                    ride = r.id,
                                                    postType = "RIDE",
                                                    caption = "Completed: ${r.title} (${String.format(java.util.Locale.US, "%.1f", r.totalDistanceKm)} km)",
                                                    imageUrl = r.photoUri ?: ""
                                                )
                                            )
                                        }
                                    }
                                )
                            } else {
                                showAddPhotosForRideId = null
                            }
                        }

                        showSettings -> {
                            SettingsScreen(
                                profile = profile,
                                onUpdateProfile = { updated -> repository.updateProfile(updated) },
                                onLogout = {
                                    scope.launch {
                                        authRepository.logout()
                                        showSettings = false
                                        dest = AppDest.LOGIN
                                    }
                                },
                                onDeleteAccount = {
                                    scope.launch {
                                        authRepository.deleteAccount()
                                        repository.clearAllUserData()
                                        showSettings = false
                                        dest = AppDest.LOGIN
                                    }
                                },
                                onBackClick = { showSettings = false }
                            )
                        }

                        showRideHistory -> {
                            RideHistoryScreen(
                                rides = rides,
                                motorcycles = motorcycles,
                                onRideClick = { rideId ->
                                    selectedRideId = rideId
                                    showRideHistory = false
                                },
                                onBackClick = { showRideHistory = false }
                            )
                        }

                        selectedGarageBikeId != null -> {
                            val bike = motorcycles.firstOrNull { it.id == selectedGarageBikeId }
                            if (bike != null) {
                                MotorcycleDetailScreen(
                                    motorcycle = bike,
                                    repository = repository,
                                    onRideClick = { rideId ->
                                        selectedRideId = rideId
                                    },
                                    onBackClick = { selectedGarageBikeId = null }
                                )
                            } else {
                                selectedGarageBikeId = null
                            }
                        }

                        showGarage -> {
                            GarageScreen(
                                motorcycles = motorcycles,
                                onAddMotorcycle = { repository.addMotorcycle(it) },
                                onEditMotorcycle = { repository.updateMotorcycle(it) },
                                onSetPrimary = { repository.setPrimaryMotorcycle(it) },
                                onDeleteMotorcycle = { repository.deleteMotorcycle(it) },
                                onViewStats = { bikeId -> selectedGarageBikeId = bikeId },
                                onBackClick = { showGarage = false }
                            )
                        }

                        showAchievements -> {
                            AchievementsScreen(
                                repository = repository,
                                onBackClick = { showAchievements = false }
                            )
                        }

                        showTags -> {
                            TagSelectionScreen(
                                repository = repository,
                                onBackClick = { showTags = false }
                            )
                        }

                        selectedRideId != null -> {
                            val ride = remember(selectedRideId, rides) {
                                rides.firstOrNull { it.id == selectedRideId }
                                    ?: repository.getRideById(selectedRideId ?: "")
                            }
                            val points = remember(selectedRideId, showShareStudio) {
                                selectedRideId?.let { repository.getPointsForRide(it) } ?: emptyList()
                            }
                            val bike = remember(ride, motorcycles) {
                                ride?.motorcycleId?.let { id -> motorcycles.firstOrNull { it.id == id } }
                            }

                            if (ride != null) {
                                if (showShareStudio) {
                                    RideShareStudioScreen(
                                        ride = ride,
                                        points = points,
                                        motorcycle = bike,
                                        onBackClick = { showShareStudio = false }
                                    )
                                } else {
                                    RideDetailScreen(
                                        ride = ride,
                                        points = points,
                                        motorcycle = bike,
                                        allMotorcycles = motorcycles,
                                        onBackClick = {
                                            selectedRideId = null
                                            showShareStudio = false
                                        },
                                        onTitleUpdate = { newTitle -> repository.updateRideTitle(ride.id, newTitle) },
                                        onMotorcycleUpdate = { newBikeId -> repository.updateRideMotorcycle(ride.id, newBikeId) },
                                        onPrivacyUpdate = { isPrivate -> repository.updateRidePrivacy(ride.id, isPrivate) },
                                        onDeleteClick = {
                                            repository.deleteRide(ride.id)
                                            selectedRideId = null
                                            showShareStudio = false
                                        },
                                        onShareStudio = { showShareStudio = true },
                                        onPhotoUpdate = { newPhotoUri -> repository.updateRidePhoto(ride.id, newPhotoUri) }
                                    )
                                }
                            } else {
                                selectedRideId = null
                                showShareStudio = false
                            }
                        }

                        else -> {
                            when (currentTab) {
                                NavTab.HOME -> HomeScreen(
                                    profile = profile,
                                    primaryMotorcycle = primaryBike,
                                    recentRides = rides,
                                    trackingState = trackingState,
                                    incompleteRide = incompleteRide,
                                    showBatteryOptimizationPrompt = showBatteryOptimizationPrompt,
                                    unreadNotificationCount = unreadNotificationCount,
                                    onNotificationClick = { showNotificationDrawer = true },
                                    onStartRideClick = { isHudOpen = true },
                                    onRideClick = { rideId -> selectedRideId = rideId },
                                    onGarageClick = { showGarage = true },
                                    onActiveHudClick = { isHudOpen = true },
                                    onViewAllRidesClick = { showRideHistory = true },
                                    onResumeIncompleteRide = { interrupted ->
                                        RideTrackingService.recoverRide(context, interrupted.id)
                                        isHudOpen = true
                                    },
                                    onFinishIncompleteRide = { interrupted ->
                                        repository.finishIncompleteRide(interrupted.id)
                                        incompleteRide = null
                                    },
                                    onDiscardIncompleteRide = { interrupted ->
                                        repository.deleteRide(interrupted.id)
                                        incompleteRide = null
                                    },
                                    onRequestBatteryOptimization = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            try {
                                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                try {
                                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        showBatteryOptimizationPrompt = false
                                    }
                                )

                                NavTab.COMMUNITY -> CommunityScreen(
                                    repository = repository,
                                    completedRides = rides
                                )

                                NavTab.RECORD -> { /* handled by FAB → HUD */ }

                                NavTab.CLUBS -> {
                                    val activeClub = selectedClub
                                    when {
                                        activeClub != null && showCreateClubRide -> {
                                            CreateClubRideScreen(
                                                club = activeClub,
                                                repository = repository,
                                                onBackClick = { showCreateClubRide = false },
                                                onCreated = { created ->
                                                    showCreateClubRide = false
                                                    selectedClubRideSessionId = created.id
                                                }
                                            )
                                        }
                                        activeClub != null && activeGroupMapSession != null -> {
                                            ClubRideActiveScreen(
                                                session = activeGroupMapSession!!,
                                                isClubLeader = activeClub.myRole == "LEADER",
                                                repository = repository,
                                                tokenDataStore = tokenDataStore,
                                                onBackClick = { activeGroupMapSession = null },
                                                onOpenRideHud = { isHudOpen = true },
                                                onFinishMyRide = {
                                                    val finishedId = trackingState.currentRideId
                                                    ClubRideShutdownCoordinator.handleSessionEnd(
                                                        context = context,
                                                        repository = repository,
                                                        clubId = activeClub?.id,
                                                        sessionId = activeGroupMapSession?.id,
                                                        isAdminEnding = false,
                                                        onComplete = {
                                                            activeGroupMapSession = null
                                                            if (finishedId != null) {
                                                                showAddPhotosForRideId = finishedId
                                                            }
                                                        }
                                                    )
                                                },
                                                onClubRideEnded = {
                                                    ClubRideShutdownCoordinator.handleSessionEnd(
                                                        context = context,
                                                        repository = repository,
                                                        clubId = activeClub?.id,
                                                        sessionId = activeGroupMapSession?.id,
                                                        isAdminEnding = false,
                                                        onComplete = {
                                                            activeGroupMapSession = null
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                        activeClub != null && selectedClubRideSessionId != null && showClubRideAdmin -> {
                                            ClubRideAdminScreen(
                                                club = activeClub,
                                                sessionId = selectedClubRideSessionId!!,
                                                repository = repository,
                                                onBackClick = { showClubRideAdmin = false }
                                            )
                                        }
                                        activeClub != null && selectedClubRideSessionId != null -> {
                                            ClubRideSessionScreen(
                                                club = activeClub,
                                                sessionId = selectedClubRideSessionId!!,
                                                repository = repository,
                                                onBackClick = {
                                                    selectedClubRideSessionId = null
                                                    showClubRideAdmin = false
                                                },
                                                onOpenAdminRoster = { showClubRideAdmin = true },
                                                onOpenActiveRideHud = { isHudOpen = true },
                                                onOpenGroupMap = { s -> activeGroupMapSession = s }
                                            )
                                        }
                                        activeClub != null -> {
                                            ClubDetailScreen(
                                                club = activeClub,
                                                repository = repository,
                                                onBackClick = {
                                                    selectedClub = null
                                                    selectedClubRideSessionId = null
                                                    showCreateClubRide = false
                                                    showClubRideAdmin = false
                                                },
                                                onOpenClubRide = { refreshedClub, session ->
                                                    selectedClub = refreshedClub
                                                    selectedClubRideSessionId = session.id
                                                },
                                                onCreateClubRide = { refreshedClub ->
                                                    selectedClub = refreshedClub
                                                    showCreateClubRide = true
                                                }
                                            )
                                        }
                                        else -> {
                                            ClubsScreen(
                                                repository = repository,
                                                completedRidesCount = rides.size,
                                                onClubClick = { club -> selectedClub = club }
                                            )
                                        }
                                    }
                                }

                                NavTab.PROFILE -> ProfileScreen(
                                    profile = profile,
                                    repository = repository,
                                    onUpdateProfile = { updated -> repository.updateProfile(updated) },
                                    onOpenSettings = { showSettings = true },
                                    onOpenAchievements = { showAchievements = true },
                                    onOpenTags = { showTags = true }
                                )
                            }
                        }
                    }
                }
            }

            // ── Notification Drawer overlay ────────────────────────────────────
            if (showNotificationDrawer) {
                NotificationDrawer(
                    repository = repository,
                    onDismiss = {
                        showNotificationDrawer = false
                        // Refresh unread count after dismissal
                        scope.launch {
                            try {
                                val notifs = repository.getNotifications()
                                unreadNotificationCount = notifs?.notifications?.count { !it.isRead } ?: 0
                            } catch (_: Exception) {}
                        }
                    }
                )
            }
        }
    }

    // Pre-flight warning dialogs (PRD §15)
    if (showLowStorageDialog) {
        AlertDialog(
            onDismissRequest = { showLowStorageDialog = false },
            title = { Text("Low Storage Warning", color = SafetyAmber) },
            text = { Text("Device has less than 50MB free storage. Please free up space before tracking to avoid losing GPS points.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = { showLowStorageDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack)
                ) {
                    Text("OK")
                }
            },
            containerColor = CarbonDark
        )
    }

    if (showGpsDisabledDialog) {
        AlertDialog(
            onDismissRequest = { showGpsDisabledDialog = false },
            title = { Text("Location Services Disabled", color = SafetyAmber) },
            text = { Text("GPS is turned off on your device. Please turn on Location Services to accurately record your ride route.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showGpsDisabledDialog = false
                        try {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack)
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpsDisabledDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CarbonDark
        )
    }
}

@Composable
fun RydaBottomBar(
    currentTab: NavTab,
    isTracking: Boolean,
    onTabSelected: (NavTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Dark background bar for navigation items with top rounded corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(CarbonDark)
        )

        // Row of 4 items + central spacer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 1: Home
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentTab == NavTab.HOME
            ) { onTabSelected(NavTab.HOME) }

            // Tab 2: Explore (Community Feed)
            BottomNavItem(
                icon = Icons.Default.Explore,
                label = "Explore",
                isSelected = currentTab == NavTab.COMMUNITY
            ) { onTabSelected(NavTab.COMMUNITY) }

            // Tab 3: Central Record FAB Spacer for layout alignment
            Spacer(modifier = Modifier.width(60.dp))

            // Tab 4: Club (Motorcycle Clubs)
            BottomNavItem(
                icon = Icons.Default.Group,
                label = "Club",
                isSelected = currentTab == NavTab.CLUBS
            ) { onTabSelected(NavTab.CLUBS) }

            // Tab 5: Profile
            BottomNavItem(
                icon = Icons.Default.Person,
                label = "Profile",
                isSelected = currentTab == NavTab.PROFILE
            ) { onTabSelected(NavTab.PROFILE) }
        }

        // Central Ride FAB floating cleanly in front without any clipping
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
                .size(62.dp)
                .border(4.dp, AsphaltBlack, CircleShape)
                .clip(CircleShape)
                .background(if (isTracking) HighVisLime else MotoOrange)
                .clickable { onTabSelected(NavTab.RECORD) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.TwoWheeler,
                contentDescription = "Record Ride",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun BottomNavItem(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            label,
            tint = if (isSelected) MotoOrange else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (isSelected) MotoOrange else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
