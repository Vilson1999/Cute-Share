package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.NetworkUtils
import com.example.data.P2PTransferManager
import com.example.data.TransferManager
import com.example.model.CartoonAvatars
import com.example.model.CuteAvatar
import com.example.model.DiscoveredDevice
import com.example.model.SharedFile
import com.example.model.TransferSession
import com.example.model.UserProfile
import com.example.ui.navigation.Screen
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val localTransferManager = TransferManager(viewModelScope)
    private var p2pTransferManager: P2PTransferManager? = null
    private var applicationContext: Context? = null

    // User Profile
    private val _userProfile = MutableStateFlow(
        UserProfile(
            name = "Cute Explorer",
            avatarRes = R.drawable.avatar_1_bunny,
            isCreated = false
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    // Screen navigation
    private val _currentScreen = MutableStateFlow(Screen.ONBOARDING)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _pendingSharedFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val pendingSharedFiles: StateFlow<List<SharedFile>> = _pendingSharedFiles.asStateFlow()

    // Active session
    private val _activeSession = MutableStateFlow<TransferSession?>(null)
    val currentSession: StateFlow<TransferSession?> = _activeSession.asStateFlow()

    private val _transferHistory = MutableStateFlow<List<TransferSession>>(emptyList())
    val transferHistory: StateFlow<List<TransferSession>> = _transferHistory.asStateFlow()

    // Real discovered peers (devices that currently have the Receive screen open)
    private val _discoveredPeers = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredDevice>> = _discoveredPeers.asStateFlow()

    // Real incoming transfer prompt
    private val _incomingRequest = MutableStateFlow<P2PTransferManager.IncomingTransferRequest?>(null)
    val incomingRequest: StateFlow<P2PTransferManager.IncomingTransferRequest?> = _incomingRequest.asStateFlow()

    // Theme mode
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    // Ask for permission only first time flag
    private val _hasAskedPermissions = MutableStateFlow(false)
    val hasAskedPermissions: StateFlow<Boolean> = _hasAskedPermissions.asStateFlow()

    private val _isAddingMoreToActiveSession = MutableStateFlow(false)
    val isAddingMoreToActiveSession: StateFlow<Boolean> = _isAddingMoreToActiveSession.asStateFlow()

    init {
        // Collect from local transfer manager for testing and fallback
        viewModelScope.launch {
            localTransferManager.currentSession.collect { session ->
                if (p2pTransferManager?.currentSession?.value == null) {
                    _activeSession.value = session
                }
            }
        }
        viewModelScope.launch {
            localTransferManager.transferHistory.collect { history ->
                if (p2pTransferManager?.transferHistory?.value?.isEmpty() != false) {
                    _transferHistory.value = history
                }
            }
        }
    }

    fun attachContext(context: Context) {
        applicationContext = context.applicationContext
        val prefs = context.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE)
        _hasAskedPermissions.value = prefs.getBoolean("has_asked_permissions", false)

        if (p2pTransferManager != null) return
        val manager = P2PTransferManager(context.applicationContext, viewModelScope)
        p2pTransferManager = manager

        viewModelScope.launch {
            manager.currentSession.collect { session ->
                if (session != null) {
                    _activeSession.value = session
                }
            }
        }
        viewModelScope.launch {
            manager.transferHistory.collect { history ->
                if (history.isNotEmpty()) {
                    _transferHistory.value = history
                }
            }
        }
        viewModelScope.launch {
            manager.discoveredPeers.collect { peers ->
                _discoveredPeers.value = peers
            }
        }
        viewModelScope.launch {
            manager.incomingRequest.collect { req ->
                _incomingRequest.value = req
            }
        }
    }

    fun triggerPermissionRequest() {
        if (!_hasAskedPermissions.value) {
            _showPermissionDialog.value = true
        }
    }

    fun dismissPermissionDialog() {
        _showPermissionDialog.value = false
        markPermissionsAsked()
    }

    fun onPermissionsHandled() {
        _showPermissionDialog.value = false
        markPermissionsAsked()
    }

    private fun markPermissionsAsked() {
        _hasAskedPermissions.value = true
        applicationContext?.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean("has_asked_permissions", true)
            ?.apply()
    }

    fun initProfile(name: String, avatarRes: Int, isCreated: Boolean) {
        _userProfile.value = UserProfile(
            name = name.ifEmpty { "Cute Explorer" },
            avatarRes = avatarRes,
            isCreated = isCreated
        )
        _currentScreen.value = if (isCreated) Screen.HOME else Screen.ONBOARDING
    }

    fun saveUserProfile(name: String, avatarRes: Int) {
        val wasCreated = _userProfile.value.isCreated
        _userProfile.value = UserProfile(
            name = name.ifEmpty { "Cute Explorer" },
            avatarRes = avatarRes,
            isCreated = true
        )
        _currentScreen.value = Screen.HOME

        // ASK FOR PERMISSION ONLY FIRST TIME:
        // Only prompt when user creates account for the very first time and has not been asked before
        if (!wasCreated && !_hasAskedPermissions.value) {
            _showPermissionDialog.value = true
        }
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
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
    }

    fun navigateTo(screen: Screen) {
        handleScreenLifecycle(from = _currentScreen.value, to = screen)
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

        val from = _currentScreen.value
        val to = when (from) {
            Screen.PEER_SELECTION -> Screen.FILE_PICKER
            Screen.FILE_PICKER, Screen.RECEIVE_RADAR, Screen.TRANSFER -> Screen.HOME
            Screen.ONBOARDING -> if (_userProfile.value.isCreated) Screen.HOME else Screen.ONBOARDING
            Screen.HOME -> Screen.HOME
        }
        handleScreenLifecycle(from = from, to = to)
        _currentScreen.value = to
    }

    private fun handleScreenLifecycle(from: Screen, to: Screen) {
        if (from == Screen.RECEIVE_RADAR && to != Screen.RECEIVE_RADAR) {
            p2pTransferManager?.stopReceiver()
        }
        if (from == Screen.PEER_SELECTION && to != Screen.PEER_SELECTION) {
            p2pTransferManager?.stopDiscovery()
        }
        if (to == Screen.RECEIVE_RADAR) {
            startReceiverMode()
        }
        if (to == Screen.PEER_SELECTION) {
            startDiscoveryMode()
        }
    }

    fun startReceiverMode() {
        p2pTransferManager?.startReceiver(
            userProfile = _userProfile.value,
            onSessionStarted = {
                _currentScreen.value = Screen.TRANSFER
            }
        )
    }

    fun stopReceiverMode() {
        p2pTransferManager?.stopReceiver()
    }

    fun startDiscoveryMode() {
        p2pTransferManager?.startDiscovery(_userProfile.value)
    }

    fun stopDiscoveryMode() {
        p2pTransferManager?.stopDiscovery()
    }

    fun rescanPeers() {
        startDiscoveryMode()
    }

    fun addManualPeer(ip: String) {
        p2pTransferManager?.addManualPeer(ip)
    }

    fun onFilesSelectedForSharing(files: List<SharedFile>) {
        if (files.isEmpty()) return
        if (_isAddingMoreToActiveSession.value) {
            _isAddingMoreToActiveSession.value = false
            addMoreFilesToTransfer(files)
            _currentScreen.value = Screen.TRANSFER
        } else {
            _pendingSharedFiles.value = files
            startDiscoveryMode()
            _currentScreen.value = Screen.PEER_SELECTION
        }
    }

    fun addMoreFilesToTransfer(files: List<SharedFile>) {
        if (files.isEmpty()) return
        localTransferManager.addFilesToActiveSession(files)
        p2pTransferManager?.addFilesToActiveSession(files)
    }

    fun handleExternalSharedFiles(files: List<SharedFile>) {
        if (files.isEmpty()) return
        _pendingSharedFiles.value = files
        startDiscoveryMode()
        _currentScreen.value = Screen.PEER_SELECTION
    }

    // Connect to real discovered peer over TCP socket
    fun onPeerSelectedForTransfer(peer: DiscoveredDevice) {
        val files = _pendingSharedFiles.value
        if (files.isEmpty()) return

        p2pTransferManager?.stopDiscovery()
        p2pTransferManager?.startOutgoingTransfer(
            targetPeer = peer,
            files = files,
            senderProfile = _userProfile.value
        )

        // Mirror in localTransferManager for consistent state
        localTransferManager.startTransfer(
            files = files,
            isOutgoing = true,
            peerName = peer.name,
            avatarRes = peer.avatarRes
        )

        _currentScreen.value = Screen.TRANSFER
    }

    // Backward-compatible for tests / avatars
    fun onPeerSelectedForTransfer(peer: CuteAvatar) {
        val files = _pendingSharedFiles.value
        if (files.isEmpty()) return

        localTransferManager.startTransfer(
            files = files,
            isOutgoing = true,
            peerName = peer.name,
            avatarRes = peer.drawableRes
        )
        _activeSession.value = localTransferManager.currentSession.value
        _currentScreen.value = Screen.TRANSFER
    }

    fun startReceiveTransfer(files: List<SharedFile>, senderAvatar: CuteAvatar) {
        localTransferManager.startTransfer(
            files = files,
            isOutgoing = false,
            peerName = senderAvatar.name,
            avatarRes = senderAvatar.drawableRes
        )
        _activeSession.value = localTransferManager.currentSession.value
        _currentScreen.value = Screen.TRANSFER
    }

    fun toggleSessionPause() {
        localTransferManager.toggleSessionPause()
        p2pTransferManager?.toggleSessionPause()
    }

    fun cancelSession() {
        localTransferManager.cancelSession()
        p2pTransferManager?.cancelSession()
    }

    fun toggleFilePause(fileId: String) {
        localTransferManager.toggleFilePause(fileId)
        p2pTransferManager?.toggleFilePause(fileId)
    }

    fun cancelFile(fileId: String) {
        localTransferManager.cancelFile(fileId)
        p2pTransferManager?.cancelFile(fileId)
    }
}
