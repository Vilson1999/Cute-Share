package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val transferHistory by viewModel.transferHistory.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val pendingSharedFiles by viewModel.pendingSharedFiles.collectAsStateWithLifecycle()
    val isAddingMore by viewModel.isAddingMoreToActiveSession.collectAsStateWithLifecycle()

    BackHandler(enabled = currentScreen != Screen.HOME) {
        viewModel.onBack()
    }

    // No bottom toolbar as requested! Clean edge-to-edge container
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
                        onShareClick = { viewModel.navigateTo(Screen.FILE_PICKER) },
                        onReceiveClick = { viewModel.navigateTo(Screen.RECEIVE_RADAR) }
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
                            // Open peer selection or add to active session
                            viewModel.onFilesSelectedForSharing(files)
                        }
                    )

                    Screen.PEER_SELECTION -> PeerSelectionScreen(
                        userProfile = userProfile,
                        filesToShare = pendingSharedFiles,
                        onBackClick = { viewModel.onBack() },
                        onSelectPeer = { peer ->
                            viewModel.onPeerSelectedForTransfer(peer)
                        }
                    )

                    Screen.RECEIVE_RADAR -> ReceiveRadarScreen(
                        onBackClick = { viewModel.onBack() },
                        onAcceptTransfer = { files, avatar ->
                            viewModel.startReceiveTransfer(files, avatar)
                        }
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
