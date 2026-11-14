package com.example.ui

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.DeviceFileScanner
import com.example.data.HardwareReadinessManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val _pendingExternalShareReadiness = MutableStateFlow(false)
    val pendingExternalShareReadiness: StateFlow<Boolean> = _pendingExternalShareReadiness.asStateFlow()

    // App-lifetime device file cache: starts loading at startup and stays loaded until app closes
    private val _deviceFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val deviceFiles: StateFlow<List<SharedFile>> = _deviceFiles.asStateFlow()

    private val _isLoadingDeviceFiles = MutableStateFlow(true)
    val isLoadingDeviceFiles: StateFlow<Boolean> = _isLoadingDeviceFiles.asStateFlow()

    private val userPickedFiles = mutableListOf<SharedFile>()
    private var fileScanJob: Job? = null
    private var hasStartedInitialFileLoad = false
    private var lastKnownAllFilesPerm: Boolean? = null

    private var lastSelectedPeer: DiscoveredDevice? = null

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
        val appCtx = context.applicationContext
        applicationContext = appCtx
        val prefs = context.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE)
        _hasAskedPermissions.value = prefs.getBoolean("has_asked_permissions", false)

        if (!hasStartedInitialFileLoad) {
            hasStartedInitialFileLoad = true
            lastKnownAllFilesPerm = HardwareReadinessManager.hasAllFilesPermission(appCtx)
            loadAllDeviceFiles(forceRestart = false)
        }

        if (p2pTransferManager != null) return
        val manager = P2PTransferManager(appCtx, viewModelScope)
        p2pTransferManager = manager

        viewModelScope.launch {
            manager.currentSession.collect { session ->
                if (session != null) {
                    _activeSession.value = session
                    // Automatically transition receiver/sender to TRANSFER screen when active session begins
                    if (_currentScreen.value == Screen.RECEIVE_RADAR || _currentScreen.value == Screen.PEER_SELECTION) {
                        _currentScreen.value = Screen.TRANSFER
                    }
                } else if (localTransferManager.currentSession.value == null) {
                    _activeSession.value = null
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
        loadAllDeviceFiles(forceRestart = true)
    }

    fun refreshDeviceFilesIfPermissionChanged() {
        val ctx = applicationContext ?: return
        val currentPerm = HardwareReadinessManager.hasAllFilesPermission(ctx)
        val prevPerm = lastKnownAllFilesPerm
        lastKnownAllFilesPerm = currentPerm
        if (prevPerm == false && currentPerm) {
            loadAllDeviceFiles(forceRestart = true)
        }
    }

    fun loadAllDeviceFiles(forceRestart: Boolean = false) {
        val ctx = applicationContext ?: return
        if (!forceRestart && fileScanJob?.isActive == true) return
        fileScanJob?.cancel()
        _isLoadingDeviceFiles.value = true
        lastKnownAllFilesPerm = HardwareReadinessManager.hasAllFilesPermission(ctx)
        fileScanJob = viewModelScope.launch {
            try {
                val scanned = DeviceFileScanner.queryDeviceFiles(ctx) { batch ->
                    _deviceFiles.value = mergePickedWithScanned(batch)
                }
                _deviceFiles.value = mergePickedWithScanned(scanned)
            } finally {
                _isLoadingDeviceFiles.value = false
            }
        }
    }

    fun addPickedFiles(picked: List<SharedFile>) {
        if (picked.isEmpty()) return
        synchronized(userPickedFiles) {
            picked.forEach { file ->
                userPickedFiles.removeAll { it.name.equals(file.name, ignoreCase = true) && it.sizeBytes == file.sizeBytes }
                userPickedFiles.add(0, file)
            }
        }
        _deviceFiles.value = mergePickedWithScanned(_deviceFiles.value)
    }

    private fun mergePickedWithScanned(scanned: List<SharedFile>): List<SharedFile> {
        val pickedSnapshot = synchronized(userPickedFiles) { userPickedFiles.toList() }
        if (pickedSnapshot.isEmpty()) return scanned
        val remaining = scanned.filterNot { item ->
            pickedSnapshot.any { picked ->
                picked.uriString == item.uriString ||
                    (picked.name.equals(item.name, ignoreCase = true) && picked.sizeBytes == item.sizeBytes)
            }
        }
        return pickedSnapshot + remaining
    }

    var hasInitializedProfile: Boolean = false
        private set

    private fun markPermissionsAsked() {
        _hasAskedPermissions.value = true
        applicationContext?.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE)
            ?.edit {
                putBoolean("has_asked_permissions", true)
            }
    }

    fun initProfile(name: String, avatarRes: Int, isCreated: Boolean) {
        hasInitializedProfile = true
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

        // Only prompt when user creates account for the very first time, has not been asked before,
        // and some permissions are still missing.
        val missingPerms = applicationContext?.let { !HardwareReadinessManager.hasAllPermissions(it) } ?: true
        if (!wasCreated && !_hasAskedPermissions.value && missingPerms) {
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
        if (from == Screen.TRANSFER && to != Screen.TRANSFER && to != Screen.FILE_PICKER && !isTransferActive()) {
            p2pTransferManager?.cleanupAfterFinishedTransfer()
        }
        if (from == Screen.PEER_SELECTION && to != Screen.PEER_SELECTION && to != Screen.TRANSFER) {
            p2pTransferManager?.stopDiscovery()
        }
        if (to == Screen.RECEIVE_RADAR) {
            if (!isTransferActive()) {
                p2pTransferManager?.clearSession()
            }
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
                viewModelScope.launch(Dispatchers.Main) {
                    _currentScreen.value = Screen.TRANSFER
                }
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
        val ctx = applicationContext
        if (ctx == null || HardwareReadinessManager.isAllHardwareReady(ctx)) {
            _pendingExternalShareReadiness.value = false
            startDiscoveryMode()
            _currentScreen.value = Screen.PEER_SELECTION
        } else {
            _pendingExternalShareReadiness.value = true
        }
    }

    fun completeExternalSharedFilesAfterReadiness() {
        _pendingExternalShareReadiness.value = false
        if (_pendingSharedFiles.value.isNotEmpty()) {
            startDiscoveryMode()
            _currentScreen.value = Screen.PEER_SELECTION
        }
    }

    fun cancelExternalSharedFilesReadiness() {
        _pendingExternalShareReadiness.value = false
    }

    // Connect to real discovered peer over Wi-Fi Direct / LAN socket
    fun onPeerSelectedForTransfer(peer: DiscoveredDevice) {
        val files = _pendingSharedFiles.value
        if (files.isEmpty()) return

        lastSelectedPeer = peer
        p2pTransferManager?.startOutgoingTransfer(
            targetPeer = peer,
            files = files,
            senderProfile = _userProfile.value
        )
        _activeSession.value = p2pTransferManager?.currentSession?.value

        viewModelScope.launch(Dispatchers.Main) {
            _currentScreen.value = Screen.TRANSFER
        }
    }

    fun retryOutgoingTransfer() {
        val peer = lastSelectedPeer ?: return
        val files = _pendingSharedFiles.value
        if (files.isEmpty()) return
        p2pTransferManager?.startOutgoingTransfer(
            targetPeer = peer,
            files = files,
            senderProfile = _userProfile.value
        )
        _activeSession.value = p2pTransferManager?.currentSession?.value
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

    fun isTransferActive(): Boolean {
        val p2pActive = p2pTransferManager?.isTransferActive() == true
        val localActive = localTransferManager.currentSession.value?.let { !it.isCompleted && !it.isCancelled } == true
        return p2pActive || localActive
    }

    fun onAppBackgrounded() {
        if (!isTransferActive()) {
            p2pTransferManager?.shutdownIdleBackgroundWork()
        }
    }

    fun cleanupAllBackgroundWork() {
        p2pTransferManager?.shutdownAll()
    }

    override fun onCleared() {
        super.onCleared()
        fileScanJob?.cancel()
        cleanupAllBackgroundWork()
    }
}
