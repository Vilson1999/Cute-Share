package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.HardwareReadinessManager
import com.example.model.DiscoveredDevice
import com.example.model.SharedFile
import com.example.ui.components.HardwareReadinessDialog
import com.example.ui.components.PermissionsHandler
import com.example.ui.navigation.Screen
import com.example.ui.screens.CreateAccountScreen
import com.example.ui.screens.FilePickerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PeerSelectionScreen
import com.example.ui.screens.ReceiveRadarScreen
import com.example.ui.screens.TransferScreen

private sealed interface PendingHardwareAction {
    data class Navigate(val screen: Screen) : PendingHardwareAction
    data class SendFiles(val files: List<SharedFile>) : PendingHardwareAction
    data class SelectPeer(val peer: DiscoveredDevice) : PendingHardwareAction
    data object RescanPeers : PendingHardwareAction
    data object ExternalShare : PendingHardwareAction
}

@Composable
fun QuickShareApp(
    viewModel: MainViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        viewModel.attachContext(context)
    }

    var pendingHardwareAction by remember { mutableStateOf<PendingHardwareAction?>(null) }

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val transferHistory by viewModel.transferHistory.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val pendingSharedFiles by viewModel.pendingSharedFiles.collectAsStateWithLifecycle()
    val isAddingMore by viewModel.isAddingMoreToActiveSession.collectAsStateWithLifecycle()
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsStateWithLifecycle()
    val pendingExternalShareReadiness by viewModel.pendingExternalShareReadiness.collectAsStateWithLifecycle()
    val discoveredPeers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
    val incomingRequest by viewModel.incomingRequest.collectAsStateWithLifecycle()
    val deviceFiles by viewModel.deviceFiles.collectAsStateWithLifecycle()
    val isLoadingDeviceFiles by viewModel.isLoadingDeviceFiles.collectAsStateWithLifecycle()

    LaunchedEffect(pendingExternalShareReadiness) {
        if (pendingExternalShareReadiness) {
            if (HardwareReadinessManager.isAllHardwareReady(context)) {
                viewModel.completeExternalSharedFilesAfterReadiness()
            } else {
                pendingHardwareAction = PendingHardwareAction.ExternalShare
            }
        }
    }

    fun executeOrPrompt(action: PendingHardwareAction) {
        if (HardwareReadinessManager.isAllHardwareReady(context)) {
            when (action) {
                is PendingHardwareAction.Navigate -> viewModel.navigateTo(action.screen)
                is PendingHardwareAction.SendFiles -> viewModel.onFilesSelectedForSharing(action.files)
                is PendingHardwareAction.SelectPeer -> viewModel.onPeerSelectedForTransfer(action.peer)
                PendingHardwareAction.RescanPeers -> viewModel.rescanPeers()
                PendingHardwareAction.ExternalShare -> viewModel.completeExternalSharedFilesAfterReadiness()
            }
        } else {
            pendingHardwareAction = action
        }
    }

    BackHandler(enabled = currentScreen != Screen.HOME && userProfile.isCreated) {
        viewModel.onBack()
    }

    PermissionsHandler(
        showDialog = showPermissionDialog,
        onDismiss = { viewModel.dismissPermissionDialog() },
        onPermissionsResult = { _ -> viewModel.onPermissionsHandled() }
    )

    pendingHardwareAction?.let { action ->
        HardwareReadinessDialog(
            actionName = when (action) {
                is PendingHardwareAction.Navigate ->
                    if (action.screen == Screen.RECEIVE_RADAR) "Receiving" else "Sharing"
                PendingHardwareAction.RescanPeers,
                is PendingHardwareAction.SendFiles,
                is PendingHardwareAction.SelectPeer,
                PendingHardwareAction.ExternalShare -> "Sharing"
            },
            onAllReady = {
                pendingHardwareAction = null
                viewModel.refreshDeviceFilesIfPermissionChanged()
                when (action) {
                    is PendingHardwareAction.Navigate -> viewModel.navigateTo(action.screen)
                    is PendingHardwareAction.SendFiles -> viewModel.onFilesSelectedForSharing(action.files)
                    is PendingHardwareAction.SelectPeer -> viewModel.onPeerSelectedForTransfer(action.peer)
                    PendingHardwareAction.RescanPeers -> viewModel.rescanPeers()
                    PendingHardwareAction.ExternalShare -> viewModel.completeExternalSharedFilesAfterReadiness()
                }
            },
            onDismiss = {
                if (action is PendingHardwareAction.ExternalShare) {
                    viewModel.cancelExternalSharedFilesReadiness()
                }
                pendingHardwareAction = null
            }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            userProfile = userProfile,
                            themeMode = themeMode,
                            onToggleTheme = { viewModel.toggleTheme() },
                            onOpenProfile = { viewModel.openProfile() },
                            onShareClick = {
                                executeOrPrompt(PendingHardwareAction.Navigate(Screen.FILE_PICKER))
                            },
                            onReceiveClick = {
                                executeOrPrompt(PendingHardwareAction.Navigate(Screen.RECEIVE_RADAR))
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
                            deviceFiles = deviceFiles,
                            isLoadingDeviceFiles = isLoadingDeviceFiles,
                            onAddPickedFiles = { picked -> viewModel.addPickedFiles(picked) },
                            onRefreshFiles = { viewModel.loadAllDeviceFiles(forceRestart = true) },
                            onBackClick = { viewModel.onBack() },
                            isAddingMore = isAddingMore,
                            onSendFiles = { files ->
                                executeOrPrompt(PendingHardwareAction.SendFiles(files))
                            }
                        )

                        Screen.PEER_SELECTION -> PeerSelectionScreen(
                            userProfile = userProfile,
                            filesToShare = pendingSharedFiles,
                            discoveredPeers = discoveredPeers,
                            onBackClick = { viewModel.onBack() },
                            onSelectPeer = { peer ->
                                executeOrPrompt(PendingHardwareAction.SelectPeer(peer))
                            },
                            onRescan = {
                                executeOrPrompt(PendingHardwareAction.RescanPeers)
                            },
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
                            onShareMoreClick = { viewModel.openInbuildFileManagerForAddingMore() },
                            onRetryTransfer = { viewModel.retryOutgoingTransfer() }
                        )
                    }
                }
            }
        }
    }
}
