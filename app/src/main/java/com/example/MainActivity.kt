package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.aeroshare.permission.PermissionManager
import com.example.aeroshare.ui.dialogs.IncomingRequestDialog
import com.example.aeroshare.ui.screens.DeviceDiscoveryScreen
import com.example.aeroshare.ui.screens.HistoryDetailScreen
import com.example.aeroshare.ui.screens.HistoryScreen
import com.example.aeroshare.ui.screens.HomeScreen
import com.example.aeroshare.ui.screens.PermissionsScreen
import com.example.aeroshare.ui.screens.ProfileEditScreen
import com.example.aeroshare.ui.screens.ProfileSetupScreen
import com.example.aeroshare.ui.screens.QrDisplayScreen
import com.example.aeroshare.ui.screens.QrScannerScreen
import com.example.aeroshare.ui.screens.ReceiveWaitingScreen
import com.example.aeroshare.ui.screens.RemoveAdsScreen
import com.example.aeroshare.ui.screens.SendPickerScreen
import com.example.aeroshare.ui.screens.SendReviewScreen
import com.example.aeroshare.ui.screens.SettingsScreen
import com.example.aeroshare.ui.screens.SplashScreen
import com.example.aeroshare.ui.screens.TransferCompleteScreen
import com.example.aeroshare.ui.screens.TransferProgressScreen
import com.example.aeroshare.viewmodel.AppScreen
import com.example.aeroshare.viewmodel.MainViewModel
import com.example.ui.theme.AeroShareTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themePreference by viewModel.themePreference.collectAsState()

            AeroShareTheme(themePreference = themePreference) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        AeroShareNavigationRoot(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun AeroShareNavigationRoot(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val avatarId by viewModel.avatarId.collectAsState()
    val themePreference by viewModel.themePreference.collectAsState()
    val isPro by viewModel.isPro.collectAsState()

    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val incomingRequest by viewModel.incomingRequest.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val allHistorySessions by viewModel.allHistorySessions.collectAsState()
    val selectedSessionId by viewModel.selectedHistorySessionId.collectAsState()
    val selectedSessionItems by viewModel.selectedSessionItems.collectAsState()

    // Handle system back navigation
    BackHandler(enabled = currentScreen != AppScreen.HOME && currentScreen != AppScreen.SPLASH) {
        if (!viewModel.navigateBack()) {
            // Reached root
        }
    }

    // Nearby permissions launcher
    val nearbyPermissions = getRequiredNearbyPermissions()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    val requestPermissionsIfNeeded: (() -> Unit) -> Unit = { onGranted ->
        permissionLauncher.launch(nearbyPermissions.toTypedArray())
        onGranted()
    }

    // Incoming Request Dialog
    if (incomingRequest != null) {
        IncomingRequestDialog(
            request = incomingRequest!!,
            onAccept = { viewModel.acceptIncomingRequest() },
            onDecline = { viewModel.declineIncomingRequest() }
        )
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(250))
        },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
        AppScreen.SPLASH -> {
            SplashScreen(
                onSplashComplete = { viewModel.onSplashFinished() }
            )
        }

        AppScreen.PROFILE_SETUP -> {
            ProfileSetupScreen(
                initialName = deviceName,
                initialAvatarId = avatarId,
                onSaveProfile = { name, avatar ->
                    viewModel.saveProfile(name, avatar)
                }
            )
        }

        AppScreen.HOME -> {
            HomeScreen(
                deviceName = deviceName,
                avatarId = avatarId,
                isPro = isPro,
                adsManager = viewModel.adsManager,
                onNavigateToSend = {
                    requestPermissionsIfNeeded {
                        viewModel.navigateTo(AppScreen.SEND_PICKER)
                    }
                },
                onNavigateToReceive = {
                    requestPermissionsIfNeeded {
                        viewModel.navigateTo(AppScreen.RECEIVE_WAITING)
                    }
                },
                onNavigateToHistory = { viewModel.navigateTo(AppScreen.HISTORY) },
                onNavigateToSettings = { viewModel.navigateTo(AppScreen.SETTINGS) },
                onNavigateToRemoveAds = { viewModel.navigateTo(AppScreen.REMOVE_ADS) },
                onNavigateToProfileEdit = { viewModel.navigateTo(AppScreen.PROFILE_EDIT) },
                onNavigateToPermissions = { viewModel.navigateTo(AppScreen.PERMISSIONS) }
            )
        }

        AppScreen.SEND_PICKER -> {
            SendPickerScreen(
                storageManager = viewModel.storageManager,
                onFilesSelected = { items ->
                    viewModel.setSelectedFiles(items)
                    viewModel.navigateTo(AppScreen.SEND_REVIEW)
                },
                onNavigateBack = { viewModel.navigateBack() }
            )
        }

        AppScreen.SEND_REVIEW -> {
            SendReviewScreen(
                initialItems = selectedFiles,
                storageManager = viewModel.storageManager,
                onContinueToDiscovery = { updatedItems ->
                    viewModel.setSelectedFiles(updatedItems)
                    viewModel.navigateTo(AppScreen.DEVICE_DISCOVERY)
                },
                onNavigateBack = { viewModel.navigateBack() }
            )
        }

        AppScreen.DEVICE_DISCOVERY -> {
            DeviceDiscoveryScreen(
                discoveredDevices = discoveredDevices,
                onDeviceSelected = { device ->
                    viewModel.connectToDevice(device)
                },
                onScanQr = { viewModel.navigateTo(AppScreen.QR_SCANNER) },
                onStartDiscovery = { viewModel.startDiscovery() },
                onStopDiscovery = { viewModel.stopDiscovery() },
                onNavigateBack = { viewModel.navigateBack() }
            )
        }

        AppScreen.TRANSFER_PROGRESS -> {
            if (activeSession != null) {
                TransferProgressScreen(
                    session = activeSession!!,
                    onCancelTransfer = { viewModel.cancelActiveTransfer() }
                )
            }
        }

        AppScreen.TRANSFER_COMPLETE -> {
            if (activeSession != null) {
                TransferCompleteScreen(
                    session = activeSession!!,
                    onDone = { viewModel.onTransferCompleteDone() }
                )
            }
        }

        AppScreen.RECEIVE_WAITING -> {
            ReceiveWaitingScreen(
                deviceName = deviceName,
                avatarId = avatarId,
                onStartAdvertising = { viewModel.startReceiving() },
                onStopAdvertising = { viewModel.stopReceiving() },
                onShowQr = { viewModel.navigateTo(AppScreen.QR_DISPLAY) },
                onNavigateBack = { viewModel.navigateBack() }
            )
        }

        AppScreen.HISTORY -> {
            HistoryScreen(
                sessions = allHistorySessions,
                onSessionSelected = { sessionId ->
                    viewModel.selectHistorySession(sessionId)
                },
                onClearAllHistory = { viewModel.clearAllHistory() },
                onNavigateBack = { viewModel.navigateBack() }
            )
        }

        AppScreen.HISTORY_DETAIL -> {
            val selectedSession = allHistorySessions.firstOrNull { it.sessionId == selectedSessionId }
            HistoryDetailScreen(
                session = selectedSession,
                items = selectedSessionItems,
                onNavigateBack = { viewModel.navigateBack() }
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                deviceName = deviceName,
                avatarId = avatarId,
                themePreference = themePreference,
                isPro = isPro,
                onThemeSelected = { viewModel.setThemePreference(it) },
                onClearHistory = { viewModel.clearAllHistory() },
                onNavigateToProfileEdit = { viewModel.navigateTo(AppScreen.PROFILE_EDIT) },
                onNavigateToRemoveAds = { viewModel.navigateTo(AppScreen.REMOVE_ADS) },
                onNavigateToPermissions = { viewModel.navigateTo(AppScreen.PERMISSIONS) },
                onNavigateBack = { viewModel.navigateBack() }
            )
        }

        AppScreen.PROFILE_EDIT -> {
            ProfileEditScreen(
                currentName = deviceName,
                currentAvatarId = avatarId,
                onSaveProfile = { name, avatar ->
                    viewModel.saveProfile(name, avatar)
                },
                onNavigateBack = { viewModel.navigateBack() }
            )
        }

        AppScreen.QR_DISPLAY -> {
            QrDisplayScreen(
                deviceName = deviceName,
                avatarId = avatarId,
                endpointId = "",
                onNavigateBack = { viewModel.navigateBack() }
            )
        }

        AppScreen.QR_SCANNER -> {
            QrScannerScreen(
                onQrPayloadScanned = { payload ->
                    viewModel.connectViaQr(payload)
                },
                onNavigateBack = { viewModel.navigateBack() }
            )
        }

        AppScreen.REMOVE_ADS -> {
            RemoveAdsScreen(
                premiumManager = viewModel.premiumManager,
                onNavigateBack = { viewModel.navigateBack() }
            )
        }

        AppScreen.PERMISSIONS -> {
            PermissionsScreen(
                onNavigateBack = { viewModel.navigateBack() }
            )
        }
    }
}
}

private fun getRequiredNearbyPermissions(): List<String> {
    return PermissionManager.getAllRequiredPermissions()
}
