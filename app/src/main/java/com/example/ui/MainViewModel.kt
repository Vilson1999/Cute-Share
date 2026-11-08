package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.TransferManager
import com.example.model.CuteAvatar
import com.example.model.SharedFile
import com.example.model.TransferSession
import com.example.model.UserProfile
import com.example.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {

    private val transferManager = TransferManager(viewModelScope)

    // User Profile with default avatar and name; starts as not created to show profile creation on first start
    private val _userProfile = MutableStateFlow(
        UserProfile(
            name = "Cute Explorer",
            avatarRes = R.drawable.avatar_1_bunny,
            isCreated = false
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    // ONBOARDING (Create Profile) is the first screen at first app start
    private val _currentScreen = MutableStateFlow(Screen.ONBOARDING)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _pendingSharedFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val pendingSharedFiles: StateFlow<List<SharedFile>> = _pendingSharedFiles.asStateFlow()

    val currentSession: StateFlow<TransferSession?> = transferManager.currentSession
    val transferHistory: StateFlow<List<TransferSession>> = transferManager.transferHistory

    private val _themeMode = MutableStateFlow(com.example.ui.theme.ThemeMode.SYSTEM)
    val themeMode: StateFlow<com.example.ui.theme.ThemeMode> = _themeMode.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    fun triggerPermissionRequest() {
        _showPermissionDialog.value = true
    }

    fun dismissPermissionDialog() {
        _showPermissionDialog.value = false
    }

    fun initProfile(name: String, avatarRes: Int, isCreated: Boolean) {
        _userProfile.value = UserProfile(
            name = name.ifEmpty { "Cute Explorer" },
            avatarRes = avatarRes,
            isCreated = isCreated
        )
        if (isCreated) {
            _currentScreen.value = Screen.HOME
        } else {
            _currentScreen.value = Screen.ONBOARDING
        }
    }

    fun saveUserProfile(name: String, avatarRes: Int) {
        _userProfile.value = UserProfile(
            name = name.ifEmpty { "Cute Explorer" },
            avatarRes = avatarRes,
            isCreated = true
        )
        _currentScreen.value = Screen.HOME
        _showPermissionDialog.value = true
    }

    fun resetUserProfile() {
        _userProfile.value = UserProfile(
            name = "Cute Explorer",
            avatarRes = R.drawable.avatar_1_bunny,
            isCreated = true
        )
        _currentScreen.value = Screen.HOME
    }

    fun openProfile() {
        _currentScreen.value = Screen.ONBOARDING
    }

    fun toggleTheme() {
        _themeMode.value = when (_themeMode.value) {
            com.example.ui.theme.ThemeMode.SYSTEM -> com.example.ui.theme.ThemeMode.DARK
            com.example.ui.theme.ThemeMode.DARK -> com.example.ui.theme.ThemeMode.LIGHT
            com.example.ui.theme.ThemeMode.LIGHT -> com.example.ui.theme.ThemeMode.SYSTEM
        }
    }

    private val _isAddingMoreToActiveSession = MutableStateFlow(false)
    val isAddingMoreToActiveSession: StateFlow<Boolean> = _isAddingMoreToActiveSession.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun openInbuildFileManagerForAddingMore() {
        _isAddingMoreToActiveSession.value = true
        _currentScreen.value = Screen.FILE_PICKER
    }

    fun onBack() {
        if (_isAddingMoreToActiveSession.value && _currentScreen.value == Screen.FILE_PICKER) {
            _isAddingMoreToActiveSession.value = false
            _currentScreen.value = Screen.TRANSFER
            return
        }
        when (_currentScreen.value) {
            Screen.PEER_SELECTION -> _currentScreen.value = Screen.FILE_PICKER
            Screen.FILE_PICKER, Screen.RECEIVE_RADAR, Screen.TRANSFER -> _currentScreen.value = Screen.HOME
            Screen.ONBOARDING -> {
                if (_userProfile.value.isCreated) {
                    _currentScreen.value = Screen.HOME
                }
            }
            Screen.HOME -> Unit
        }
    }

    // When files are selected in inbuild File Picker:
    // If adding more files, appends directly to bottom of active transfer without disconnecting!
    // Otherwise navigates to peer selection screen.
    fun onFilesSelectedForSharing(files: List<SharedFile>) {
        if (files.isEmpty()) return
        if (_isAddingMoreToActiveSession.value) {
            _isAddingMoreToActiveSession.value = false
            transferManager.addFilesToActiveSession(files)
            _currentScreen.value = Screen.TRANSFER
        } else {
            _pendingSharedFiles.value = files
            _currentScreen.value = Screen.PEER_SELECTION
        }
    }

    // Add more files to active transfer session with the same user without stopping or disconnecting
    fun addMoreFilesToTransfer(files: List<SharedFile>) {
        if (files.isEmpty()) return
        transferManager.addFilesToActiveSession(files)
    }

    // Handle files shared to Cute Share from external apps (e.g. Gallery, File Manager, Video Player)
    fun handleExternalSharedFiles(files: List<SharedFile>) {
        if (files.isEmpty()) return
        _pendingSharedFiles.value = files
        _currentScreen.value = Screen.PEER_SELECTION
    }

    // When person is selected in Peer Selection screen, start transfer and open monitor
    fun onPeerSelectedForTransfer(peer: CuteAvatar) {
        val files = _pendingSharedFiles.value
        if (files.isEmpty()) return
        transferManager.startTransfer(
            files = files,
            isOutgoing = true,
            peerName = peer.name,
            avatarRes = peer.drawableRes
        )
        _currentScreen.value = Screen.TRANSFER
    }

    fun startReceiveTransfer(files: List<SharedFile>, senderAvatar: CuteAvatar) {
        transferManager.startTransfer(
            files = files,
            isOutgoing = false,
            peerName = senderAvatar.name,
            avatarRes = senderAvatar.drawableRes
        )
        _currentScreen.value = Screen.TRANSFER
    }

    fun toggleSessionPause() {
        transferManager.toggleSessionPause()
    }

    fun cancelSession() {
        transferManager.cancelSession()
    }

    fun toggleFilePause(fileId: String) {
        transferManager.toggleFilePause(fileId)
    }

    fun cancelFile(fileId: String) {
        transferManager.cancelFile(fileId)
    }
}
