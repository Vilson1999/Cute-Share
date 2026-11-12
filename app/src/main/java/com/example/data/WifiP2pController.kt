package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class P2pConnectionSnapshot(
    val info: WifiP2pInfo,
    val group: WifiP2pGroup? = null
)

/**
 * Serialized, race-free wrapper around Android's asynchronous WifiP2pManager state machine.
 * Prevents BUSY/ERROR collisions between clearLocalServices/addLocalService/createGroup
 * and stopPeerDiscovery/removeServiceRequest/connect, while keeping BroadcastReceiver
 * active throughout the connection phase.
 */
class WifiP2pController(private val context: Context) {

    private val wifiP2pManager: WifiP2pManager? = try {
        context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    } catch (_: Exception) {
        null
    }

    private val commandMutex = Mutex()

    @Volatile
    private var p2pChannel: WifiP2pManager.Channel? = null

    @Volatile
    private var isReceiverRegistered = false

    private var activeServiceInfo: WifiP2pDnsSdServiceInfo? = null
    private var activeServiceRequest: WifiP2pDnsSdServiceRequest? = null

    @Volatile
    private var onPeersUpdatedCallback: ((Collection<WifiP2pDevice>) -> Unit)? = null

    @Volatile
    private var onConnectionSnapshotCallback: ((P2pConnectionSnapshot?) -> Unit)? = null

    private val _connectionSnapshot = MutableStateFlow<P2pConnectionSnapshot?>(null)
    val connectionSnapshot: StateFlow<P2pConnectionSnapshot?> = _connectionSnapshot.asStateFlow()

    private val _thisDeviceMac = MutableStateFlow<String?>(null)
    val thisDeviceMac: StateFlow<String?> = _thisDeviceMac.asStateFlow()

    private val _isP2pEnabled = MutableStateFlow(true)
    val isP2pEnabled: StateFlow<Boolean> = _isP2pEnabled.asStateFlow()

    init {
        ensureChannel()
    }

    fun hasWifiDirectPermission(): Boolean {
        val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // On Android 13+, NEARBY_WIFI_DEVICES is primary, with FINE_LOCATION as complementary fallback for legacy P2P stacks
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNearby || hasLocation
        } else {
            hasLocation
        }
    }

    fun isAvailable(): Boolean = wifiP2pManager != null

    @Synchronized
    private fun ensureChannel(): WifiP2pManager.Channel? {
        val manager = wifiP2pManager ?: return null
        p2pChannel?.let { return it }
        return try {
            manager.initialize(context, context.mainLooper) {
                p2pChannel = null
            }.also { p2pChannel = it }
        } catch (_: Exception) {
            null
        }
    }

    private val wifiP2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _isP2pEnabled.value = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    val peerList = IntentCompat.getParcelableExtra(
                        intent,
                        WifiP2pManager.EXTRA_P2P_DEVICE_LIST,
                        WifiP2pDeviceList::class.java
                    )
                    if (peerList != null) {
                        onPeersUpdatedCallback?.invoke(peerList.deviceList)
                    } else {
                        requestPeersNonBlocking()
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val p2pInfo = IntentCompat.getParcelableExtra(
                        intent,
                        WifiP2pManager.EXTRA_WIFI_P2P_INFO,
                        WifiP2pInfo::class.java
                    )
                    val p2pGroup = IntentCompat.getParcelableExtra(
                        intent,
                        WifiP2pManager.EXTRA_WIFI_P2P_GROUP,
                        WifiP2pGroup::class.java
                    )
                    if (p2pInfo != null) {
                        if (p2pInfo.groupFormed) {
                            val snap = P2pConnectionSnapshot(info = p2pInfo, group = p2pGroup)
                            _connectionSnapshot.value = snap
                            onConnectionSnapshotCallback?.invoke(snap)
                            if (p2pGroup == null || p2pInfo.groupOwnerAddress == null) {
                                refreshConnectionSnapshotNonBlocking()
                            }
                        } else {
                            _connectionSnapshot.value = null
                            onConnectionSnapshotCallback?.invoke(null)
                        }
                    } else {
                        refreshConnectionSnapshotNonBlocking()
                    }
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = IntentCompat.getParcelableExtra(
                        intent,
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE,
                        WifiP2pDevice::class.java
                    )
                    val addr = device?.deviceAddress
                    if (!addr.isNullOrBlank() && addr != "02:00:00:00:00:00") {
                        _thisDeviceMac.value = addr
                    }
                }
            }
        }
    }

    @Synchronized
    fun ensureReceiverRegistered(
        onPeersUpdated: ((Collection<WifiP2pDevice>) -> Unit)? = null,
        onConnectionChanged: ((P2pConnectionSnapshot?) -> Unit)? = null
    ) {
        if (onPeersUpdated != null) onPeersUpdatedCallback = onPeersUpdated
        if (onConnectionChanged != null) onConnectionSnapshotCallback = onConnectionChanged
        if (isReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(wifiP2pReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(wifiP2pReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (_: Exception) {}
    }

    @Synchronized
    fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(wifiP2pReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }

    fun requestPeersNonBlocking() {
        val manager = wifiP2pManager ?: return
        val channel = ensureChannel() ?: return
        if (!hasWifiDirectPermission()) return
        try {
            @SuppressLint("MissingPermission")
            manager.requestPeers(channel) { list: WifiP2pDeviceList? ->
                if (list != null) {
                    onPeersUpdatedCallback?.invoke(list.deviceList)
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
    }

    fun refreshConnectionSnapshotNonBlocking() {
        val manager = wifiP2pManager ?: return
        val channel = ensureChannel() ?: return
        try {
            manager.requestConnectionInfo(channel) { info: WifiP2pInfo? ->
                if (info == null || !info.groupFormed) {
                    _connectionSnapshot.value = null
                    onConnectionSnapshotCallback?.invoke(null)
                    return@requestConnectionInfo
                }
                if (hasWifiDirectPermission()) {
                    try {
                        @SuppressLint("MissingPermission")
                        manager.requestGroupInfo(channel) { group: WifiP2pGroup? ->
                            val snap = P2pConnectionSnapshot(info = info, group = group)
                            _connectionSnapshot.value = snap
                            onConnectionSnapshotCallback?.invoke(snap)
                        }
                    } catch (_: Exception) {
                        val snap = P2pConnectionSnapshot(info = info, group = null)
                        _connectionSnapshot.value = snap
                        onConnectionSnapshotCallback?.invoke(snap)
                    }
                } else {
                    val snap = P2pConnectionSnapshot(info = info, group = null)
                    _connectionSnapshot.value = snap
                    onConnectionSnapshotCallback?.invoke(snap)
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun queryConnectionSnapshot(): P2pConnectionSnapshot? {
        val manager = wifiP2pManager ?: return null
        val channel = ensureChannel() ?: return null
        val info = withTimeoutOrNull(2500L) {
            suspendCancellableCoroutine<WifiP2pInfo?> { cont ->
                try {
                    manager.requestConnectionInfo(channel) { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        if (info == null || !info.groupFormed) {
            _connectionSnapshot.value = null
            return null
        }
        val group = if (hasWifiDirectPermission()) {
            withTimeoutOrNull(2000L) {
                suspendCancellableCoroutine<WifiP2pGroup?> { cont ->
                    try {
                        @SuppressLint("MissingPermission")
                        manager.requestGroupInfo(channel) { result ->
                            if (cont.isActive) cont.resume(result)
                        }
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } else {
            null
        }
        val snap = P2pConnectionSnapshot(info = info, group = group)
        _connectionSnapshot.value = snap
        return snap
    }

    /**
     * Configures Receiver mode sequentially:
     * stop scanning -> reconcile stale group -> clear old services -> add CuteShare DNS-SD service -> create GO group (or fallback discoverPeers).
     */
    suspend fun startReceiverAdvertising(
        serviceName: String,
        serviceType: String,
        txtRecord: Map<String, String>
    ) = commandMutex.withLock {
        val manager = wifiP2pManager ?: return@withLock
        val channel = ensureChannel() ?: return@withLock
        if (!hasWifiDirectPermission()) return@withLock

        awaitAction(timeoutMs = 2000L) { listener ->
            manager.stopPeerDiscovery(channel, listener)
        }

        // Reconcile pre-existing P2P group cleanly so Receiver isn't stuck in a dead client role
        val currentSnap = queryConnectionSnapshot()
        if (currentSnap?.info?.groupFormed == true && !currentSnap.info.isGroupOwner) {
            awaitAction(timeoutMs = 2500L) { listener ->
                manager.removeGroup(channel, listener)
            }
            _connectionSnapshot.value = null
            delay(300L)
        }

        // Clear any leftover local services FIRST, await completion, THEN register our service
        awaitAction(retries = 2, timeoutMs = 2500L) { listener ->
            manager.clearLocalServices(channel, listener)
        }

        val info = WifiP2pDnsSdServiceInfo.newInstance(serviceName, serviceType, txtRecord)
        activeServiceInfo = info
        @SuppressLint("MissingPermission")
        awaitAction(retries = 3, timeoutMs = 2500L) { listener ->
            manager.addLocalService(channel, info, listener)
        }

        // Refresh connection snapshot in case group isn't formed yet, then start autonomous GO
        val updatedSnap = queryConnectionSnapshot()
        if (updatedSnap?.info?.groupFormed != true) {
            @SuppressLint("MissingPermission")
            val goResult = awaitAction(retries = 2, timeoutMs = 3000L) { listener ->
                manager.createGroup(channel, listener)
            }
            if (goResult != 0) {
                // Fallback on OEMs whose STA band rejects autonomous GO: remain actively discoverable
                @SuppressLint("MissingPermission")
                awaitAction(retries = 2, timeoutMs = 2500L) { listener ->
                    manager.discoverPeers(channel, listener)
                }
            }
        }
        refreshConnectionSnapshotNonBlocking()
    }

    suspend fun keepReceiverDiscoverableIfNoGroup() = commandMutex.withLock {
        val manager = wifiP2pManager ?: return@withLock
        val channel = ensureChannel() ?: return@withLock
        if (!hasWifiDirectPermission()) return@withLock
        val snap = queryConnectionSnapshot()
        if (snap?.info?.groupFormed != true) {
            @SuppressLint("MissingPermission")
            awaitAction(retries = 1, timeoutMs = 2000L) { listener ->
                manager.discoverPeers(channel, listener)
            }
        }
    }

    suspend fun stopReceiverAdvertising() = commandMutex.withLock {
        val manager = wifiP2pManager ?: return@withLock
        val channel = ensureChannel() ?: return@withLock

        activeServiceInfo?.let { info ->
            awaitAction(timeoutMs = 2000L) { listener ->
                manager.removeLocalService(channel, info, listener)
            }
            activeServiceInfo = null
        } ?: run {
            awaitAction(timeoutMs = 2000L) { listener ->
                manager.clearLocalServices(channel, listener)
            }
        }

        val snap = queryConnectionSnapshot()
        if (snap?.info?.groupFormed == true) {
            awaitAction(timeoutMs = 2500L) { listener ->
                manager.removeGroup(channel, listener)
            }
        }
        _connectionSnapshot.value = null
    }

    /**
     * Configures Sender DNS-SD & Peer Discovery sequentially.
     */
    suspend fun startServiceAndPeerDiscovery(
        onTxtRecord: (fullDomain: String, record: Map<String, String>, device: WifiP2pDevice) -> Unit,
        onServiceResponse: (instanceName: String, registrationType: String, device: WifiP2pDevice) -> Unit
    ) = commandMutex.withLock {
        val manager = wifiP2pManager ?: return@withLock
        val channel = ensureChannel() ?: return@withLock
        if (!hasWifiDirectPermission()) return@withLock

        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomain, record, device ->
            onTxtRecord(fullDomain, record ?: emptyMap(), device)
        }
        val servListener = WifiP2pManager.DnsSdServiceResponseListener { instanceName, registrationType, device ->
            onServiceResponse(instanceName ?: "", registrationType ?: "", device)
        }
        try {
            manager.setDnsSdResponseListeners(channel, servListener, txtListener)
        } catch (_: Exception) {}

        awaitAction(retries = 2, timeoutMs = 2000L) { listener ->
            manager.clearServiceRequests(channel, listener)
        }

        val req = WifiP2pDnsSdServiceRequest.newInstance()
        activeServiceRequest = req
        awaitAction(retries = 3, timeoutMs = 2500L) { listener ->
            manager.addServiceRequest(channel, req, listener)
        }

        @SuppressLint("MissingPermission")
        awaitAction(retries = 2, timeoutMs = 2500L) { listener ->
            manager.discoverServices(channel, listener)
        }
        @SuppressLint("MissingPermission")
        awaitAction(retries = 2, timeoutMs = 2500L) { listener ->
            manager.discoverPeers(channel, listener)
        }
        requestPeersNonBlocking()
    }

    suspend fun refreshDiscovery() = commandMutex.withLock {
        val manager = wifiP2pManager ?: return@withLock
        val channel = ensureChannel() ?: return@withLock
        if (!hasWifiDirectPermission()) return@withLock

        if (activeServiceRequest != null) {
            @SuppressLint("MissingPermission")
            awaitAction(timeoutMs = 2000L) { listener ->
                manager.discoverServices(channel, listener)
            }
        }
        @SuppressLint("MissingPermission")
        awaitAction(timeoutMs = 2000L) { listener ->
            manager.discoverPeers(channel, listener)
        }
        requestPeersNonBlocking()
    }

    suspend fun stopScanningOnly() = commandMutex.withLock {
        val manager = wifiP2pManager ?: return@withLock
        val channel = ensureChannel() ?: return@withLock
        awaitAction(timeoutMs = 2000L) { listener ->
            manager.stopPeerDiscovery(channel, listener)
        }
        activeServiceRequest?.let { req ->
            awaitAction(timeoutMs = 2000L) { listener ->
                manager.removeServiceRequest(channel, req, listener)
            }
            activeServiceRequest = null
        }
    }

    /**
     * Initiates a serialized Wi-Fi Direct connection to [targetMac], first stopping peer
     * discovery & clearing stale mismatched groups so `connect()` never collides with BUSY.
     */
    suspend fun initiatePeerConnection(
        targetMac: String,
        onStatusUpdate: (String) -> Unit
    ): Boolean = commandMutex.withLock {
        val manager = wifiP2pManager ?: return@withLock false
        val channel = ensureChannel() ?: return@withLock false
        if (!hasWifiDirectPermission()) return@withLock false

        // Halt discovery before touching connect() state machine
        awaitAction(timeoutMs = 2000L) { listener ->
            manager.stopPeerDiscovery(channel, listener)
        }
        activeServiceRequest?.let { req ->
            awaitAction(timeoutMs = 2000L) { listener ->
                manager.removeServiceRequest(channel, req, listener)
            }
            activeServiceRequest = null
        }

        // Check whether we are ALREADY connected to the desired peer
        val existingSnap = queryConnectionSnapshot()
        if (existingSnap?.info?.groupFormed == true) {
            val matchesTarget = groupContainsMac(existingSnap.group, targetMac)
            if (matchesTarget) {
                return@withLock true
            }
            // Different stale group -> tear down cleanly first
            awaitAction(timeoutMs = 2500L) { listener ->
                manager.removeGroup(channel, listener)
            }
            _connectionSnapshot.value = null
            delay(350L)
        }

        onStatusUpdate("Connecting via Wi-Fi Direct...")
        val config = WifiP2pConfig().apply {
            deviceAddress = targetMac
            wps = WpsInfo().apply { setup = WpsInfo.PBC }
            groupOwnerIntent = 0 // Prefer Receiver as Group Owner
        }

        @SuppressLint("MissingPermission")
        val code = awaitAction(retries = 3, retryDelayMs = 450L, timeoutMs = 4000L) { listener ->
            manager.connect(channel, config, listener)
        }
        code == 0
    }

    suspend fun removeGroupIfAny() = commandMutex.withLock {
        val manager = wifiP2pManager ?: return@withLock
        val channel = ensureChannel() ?: return@withLock
        val snap = queryConnectionSnapshot()
        if (snap?.info?.groupFormed == true) {
            awaitAction(timeoutMs = 2500L) { listener ->
                manager.removeGroup(channel, listener)
            }
        }
        _connectionSnapshot.value = null
    }

    fun groupContainsMac(group: WifiP2pGroup?, targetMac: String?): Boolean {
        if (targetMac.isNullOrBlank()) return false
        if (group == null) return false
        if (group.owner?.deviceAddress.equals(targetMac, ignoreCase = true)) return true
        return group.clientList.any { it.deviceAddress.equals(targetMac, ignoreCase = true) }
    }

    private suspend fun awaitAction(
        retries: Int = 0,
        retryDelayMs: Long = 350L,
        timeoutMs: Long = 3000L,
        invoke: (WifiP2pManager.ActionListener) -> Unit
    ): Int {
        var lastResult = WifiP2pManager.ERROR
        for (attempt in 0..retries) {
            val result = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Int> { cont ->
                    val listener = object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            if (cont.isActive) cont.resume(0)
                        }

                        override fun onFailure(reason: Int) {
                            if (cont.isActive) {
                                // Map Android error code 0 (`ERROR`) to -1 so 0 strictly means SUCCESS
                                cont.resume(if (reason == 0) -1 else reason)
                            }
                        }
                    }
                    try {
                        invoke(listener)
                    } catch (_: SecurityException) {
                        if (cont.isActive) cont.resume(-2)
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(-1)
                    }
                }
            } ?: -3

            if (result == 0) return 0
            lastResult = result
            if (result == WifiP2pManager.P2P_UNSUPPORTED || result == -2) break
            if (attempt < retries) {
                delay(retryDelayMs)
            }
        }
        return lastResult
    }

    companion object {
        fun isPhoneOrTabletDeviceType(primaryDeviceType: String?): Boolean {
            if (primaryDeviceType.isNullOrBlank()) return true
            val major = primaryDeviceType.substringBefore('-').trim()
            // WSC categories: 10 = Telephone (Phones), 1 = Computer (Tablets/Laptops),
            // exclude TVs (7), Printers (3), Audio (8), Projectors (11), Cameras (6)
            return major == "10" || major == "1"
        }

        fun extractCuteShareNameFromInstance(instanceName: String?): String? {
            if (instanceName.isNullOrBlank()) return null
            if (!instanceName.startsWith("CuteShare_", ignoreCase = true)) return null
            val stripped = instanceName.substring(10).trim()
            return stripped.ifBlank { null }
        }
    }
}
