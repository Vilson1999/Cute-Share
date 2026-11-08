package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.PermissionsHandler
import com.example.ui.navigation.Screen
import com.example.ui.screens.CreateAccountScreen
import com.example.ui.screens.FilePickerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PeerSelectionScreen
import com.example.ui.screens.ReceiveRadarScreen
import com.example.ui.screens.TransferScreen

@Composable
fun QuickShareApp(
    viewModel: MainViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        viewModel.attachContext(context)
    }

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val transferHistory by viewModel.transferHistory.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val pendingSharedFiles by viewModel.pendingSharedFiles.collectAsStateWithLifecycle()
    val isAddingMore by viewModel.isAddingMoreToActiveSession.collectAsStateWithLifecycle()
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsStateWithLifecycle()
    val discoveredPeers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
    val incomingRequest by viewModel.incomingRequest.collectAsStateWithLifecycle()

    BackHandler(enabled = currentScreen != Screen.HOME && userProfile.isCreated) {
        viewModel.onBack()
    }

    PermissionsHandler(
        showDialog = showPermissionDialog,
        onDismiss = { viewModel.dismissPermissionDialog() },
        onPermissionsResult = { _ -> viewModel.onPermissionsHandled() }
    )

    Surface(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        userProfile = userProfile,
                        themeMode = themeMode,
                        onToggleTheme = { viewModel.toggleTheme() },
                        onOpenProfile = { viewModel.openProfile() },
                        onShareClick = {
                            viewModel.navigateTo(Screen.FILE_PICKER)
                        },
                        onReceiveClick = {
                            viewModel.navigateTo(Screen.RECEIVE_RADAR)
                        }
                    )

                    Screen.ONBOARDING -> CreateAccountScreen(
                        currentProfile = userProfile,
                        onSaveProfile = { name, avatarRes ->
                            viewModel.saveUserProfile(name, avatarRes)
                        },
                        onResetProfile = {
                            viewModel.resetUserProfile()
                        },
                        onBackClick = {
                            viewModel.onBack()
                        }
                    )

                    Screen.FILE_PICKER -> FilePickerScreen(
                        onBackClick = { viewModel.onBack() },
                        isAddingMore = isAddingMore,
                        onSendFiles = { files ->
                            viewModel.onFilesSelectedForSharing(files)
                        }
                    )

                    Screen.PEER_SELECTION -> PeerSelectionScreen(
                        userProfile = userProfile,
                        filesToShare = pendingSharedFiles,
                        discoveredPeers = discoveredPeers,
                        onBackClick = { viewModel.onBack() },
                        onSelectPeer = { peer ->
                            viewModel.onPeerSelectedForTransfer(peer)
                        },
                        onRescan = { viewModel.rescanPeers() },
                        onAddManualPeer = { ip -> viewModel.addManualPeer(ip) }
                    )

                    Screen.RECEIVE_RADAR -> ReceiveRadarScreen(
                        userProfile = userProfile,
                        incomingRequest = incomingRequest,
                        onBackClick = { viewModel.onBack() }
                    )

                    Screen.TRANSFER -> TransferScreen(
                        session = currentSession,
                        transferHistory = transferHistory,
                        onBackClick = { viewModel.onBack() },
                        onToggleSessionPause = { viewModel.toggleSessionPause() },
                        onCancelSession = { viewModel.cancelSession() },
                        onToggleFilePause = { id -> viewModel.toggleFilePause(id) },
                        onCancelFile = { id -> viewModel.cancelFile(id) },
                        onShareMoreClick = { viewModel.openInbuildFileManagerForAddingMore() }
                    )
                }
            }
        }
    }
}
