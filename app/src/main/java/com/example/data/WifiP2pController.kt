package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.p2p.WifiDirectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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
 * Coroutine-friendly coordinator backed by [WifiDirectManager] (`com.example.p2p.WifiDirectManager`).
 * Bridges `WifiDirectManager`'s Android 13+ `startListening`, `requestDeviceInfo`, DNS-SD service
 * discovery, and `ContextCompat.RECEIVER_EXPORTED` receiver with [P2PTransferManager]'s serialized
 * transfer pipeline.
 */
class WifiP2pController(private val context: Context) {

    val wifiDirectManager = WifiDirectManager(context.applicationContext)

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commandMutex = Mutex()
    private var bridgeJob: Job? = null

    @Volatile
    private var onPeersUpdatedCallback: ((Collection<WifiP2pDevice>) -> Unit)? = null

    @Volatile
    private var onConnectionSnapshotCallback: ((P2pConnectionSnapshot?) -> Unit)? = null

    @Volatile
    private var lastTxtCallback: ((String, Map<String, String>, WifiP2pDevice) -> Unit)? = null

    @Volatile
    private var lastServiceCallback: ((String, String, WifiP2pDevice) -> Unit)? = null

    private val _connectionSnapshot = MutableStateFlow<P2pConnectionSnapshot?>(null)
    val connectionSnapshot: StateFlow<P2pConnectionSnapshot?> = _connectionSnapshot.asStateFlow()

    private val _thisDeviceMac = MutableStateFlow<String?>(null)
    val thisDeviceMac: StateFlow<String?> = _thisDeviceMac.asStateFlow()

    val isP2pEnabled: StateFlow<Boolean> = wifiDirectManager.isWifiP2pEnabled

    init {
        startStateBridge()
    }

    private fun startStateBridge() {
        if (bridgeJob?.isActive == true) return
        bridgeJob = controllerScope.launch {
            launch {
                wifiDirectManager.peers.collect { list ->
                    if (list.isNotEmpty()) {
                        onPeersUpdatedCallback?.invoke(list)
                    }
                }
            }
            launch {
                wifiDirectManager.thisDevice.collect { device ->
                    val addr = device?.deviceAddress
                    if (!addr.isNullOrBlank() && addr != "02:00:00:00:00:00") {
                        _thisDeviceMac.value = addr
                    }
                }
            }
            launch {
                combine(wifiDirectManager.connectionInfo, wifiDirectManager.groupInfo) { info, group ->
                    if (info != null && info.groupFormed) {
                        P2pConnectionSnapshot(info = info, group = group)
                    } else {
                        null
                    }
                }.collect { snap ->
                    _connectionSnapshot.value = snap
                    onConnectionSnapshotCallback?.invoke(snap)
                }
            }
        }
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

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNearby || hasLocation
        } else {
            hasLocation
        }
    }

    fun isAvailable(): Boolean = wifiDirectManager.isAvailable()

    @Synchronized
    fun ensureReceiverRegistered(
        onPeersUpdated: ((Collection<WifiP2pDevice>) -> Unit)? = null,
        onConnectionChanged: ((P2pConnectionSnapshot?) -> Unit)? = null
    ) {
        if (onPeersUpdated != null) onPeersUpdatedCallback = onPeersUpdated
        if (onConnectionChanged != null) onConnectionSnapshotCallback = onConnectionChanged
        wifiDirectManager.ensureRegistered()
        startStateBridge()
    }

    @Synchronized
    fun unregisterReceiver() {
        wifiDirectManager.unregister()
    }

    fun requestPeersNonBlocking() {
        if (!hasWifiDirectPermission()) return
        wifiDirectManager.requestPeers()
    }

    fun refreshConnectionSnapshotNonBlocking() {
        wifiDirectManager.requestConnectionInfo { info ->
            if (hasWifiDirectPermission()) {
                wifiDirectManager.requestGroupInfo { group ->
                    val snap = P2pConnectionSnapshot(info = info, group = group)
                    _connectionSnapshot.value = snap
                    onConnectionSnapshotCallback?.invoke(snap)
                }
            } else {
                val snap = P2pConnectionSnapshot(info = info, group = null)
                _connectionSnapshot.value = snap
                onConnectionSnapshotCallback?.invoke(snap)
            }
        }
    }

    suspend fun queryConnectionSnapshot(): P2pConnectionSnapshot? {
        if (!isAvailable()) return null
        val existingInfo = wifiDirectManager.connectionInfo.value
        val info = if (existingInfo != null && existingInfo.groupFormed) {
            existingInfo
        } else {
            withTimeoutOrNull(2500L) {
                suspendCancellableCoroutine<WifiP2pInfo?> { cont ->
                    wifiDirectManager.requestConnectionInfo { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                }
            }
        }
        if (info == null || !info.groupFormed) {
            _connectionSnapshot.value = null
            return null
        }

        val group = wifiDirectManager.groupInfo.value ?: if (hasWifiDirectPermission()) {
            withTimeoutOrNull(2000L) {
                suspendCancellableCoroutine<WifiP2pGroup?> { cont ->
                    wifiDirectManager.requestGroupInfo { result ->
                        if (cont.isActive) cont.resume(result)
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

    suspend fun startReceiverAdvertising(
        serviceName: String,
        serviceType: String,
        txtRecord: Map<String, String>
    ) = commandMutex.withLock {
        if (!isAvailable() || !hasWifiDirectPermission()) return@withLock

        val displayUserName = txtRecord["name"] ?: serviceName.removePrefix("CuteShare_")
        withTimeoutOrNull(3000L) {
            suspendCancellableCoroutine<Unit> { cont ->
                wifiDirectManager.registerLocalReceiveService(
                    userName = displayUserName,
                    serviceType = serviceType,
                    extraRecord = txtRecord,
                    instanceName = serviceName
                ) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }

        withTimeoutOrNull(3000L) {
            suspendCancellableCoroutine<Unit> { cont ->
                wifiDirectManager.startReceiveListening(
                    onSuccess = { if (cont.isActive) cont.resume(Unit) },
                    onFailure = { if (cont.isActive) cont.resume(Unit) }
                )
            }
        }
        refreshConnectionSnapshotNonBlocking()
    }

    suspend fun keepReceiverDiscoverableIfNoGroup() = commandMutex.withLock {
        if (!isAvailable() || !hasWifiDirectPermission()) return@withLock
        if (!wifiDirectManager.isConnected.value) {
            withTimeoutOrNull(2000L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    wifiDirectManager.startReceiveListening(
                        onSuccess = { if (cont.isActive) cont.resume(Unit) },
                        onFailure = { if (cont.isActive) cont.resume(Unit) }
                    )
                }
            }
        }
    }

    suspend fun stopReceiverAdvertising() = commandMutex.withLock {
        if (!isAvailable()) return@withLock
        wifiDirectManager.stopReceiveListening()
        if (wifiDirectManager.isConnected.value) {
            wifiDirectManager.disconnect()
        }
        _connectionSnapshot.value = null
    }

    suspend fun startServiceAndPeerDiscovery(
        onTxtRecord: (fullDomain: String, record: Map<String, String>, device: WifiP2pDevice) -> Unit,
        onServiceResponse: (instanceName: String, registrationType: String, device: WifiP2pDevice) -> Unit
    ) = commandMutex.withLock {
        if (!isAvailable() || !hasWifiDirectPermission()) return@withLock
        lastTxtCallback = onTxtRecord
        lastServiceCallback = onServiceResponse

        wifiDirectManager.discoverMiniShareServices(
            onTxtRecord = onTxtRecord,
            onServiceResponse = onServiceResponse
        )
        withTimeoutOrNull(2500L) {
            suspendCancellableCoroutine<Unit> { cont ->
                wifiDirectManager.discoverPeers(
                    onSuccess = { if (cont.isActive) cont.resume(Unit) },
                    onFailure = { if (cont.isActive) cont.resume(Unit) }
                )
            }
        }
    }

    suspend fun refreshDiscovery() = commandMutex.withLock {
        if (!isAvailable() || !hasWifiDirectPermission()) return@withLock
        wifiDirectManager.discoverMiniShareServices(
            onTxtRecord = lastTxtCallback,
            onServiceResponse = lastServiceCallback
        )
        withTimeoutOrNull(2000L) {
            suspendCancellableCoroutine<Unit> { cont ->
                wifiDirectManager.discoverPeers(
                    onSuccess = { if (cont.isActive) cont.resume(Unit) },
                    onFailure = { if (cont.isActive) cont.resume(Unit) }
                )
            }
        }
    }

    suspend fun stopScanningOnly() = commandMutex.withLock {
        if (!isAvailable()) return@withLock
        wifiDirectManager.stopDiscovery()
    }

    suspend fun initiatePeerConnection(
        targetMac: String,
        onStatusUpdate: (String) -> Unit
    ): Boolean = commandMutex.withLock {
        if (!isAvailable() || !hasWifiDirectPermission()) return@withLock false

        wifiDirectManager.stopDiscovery()

        val existingSnap = queryConnectionSnapshot()
        if (existingSnap?.info?.groupFormed == true) {
            val matchesTarget = groupContainsMac(existingSnap.group, targetMac)
            if (matchesTarget) {
                return@withLock true
            }
            wifiDirectManager.disconnect()
            _connectionSnapshot.value = null
            delay(350L)
        }

        onStatusUpdate("Connecting via Wi-Fi Direct...")
        for (attempt in 0..2) {
            val code = withTimeoutOrNull(4000L) {
                suspendCancellableCoroutine<Int> { cont ->
                    wifiDirectManager.connectToMac(
                        deviceAddress = targetMac,
                        groupOwnerIntent = 0,
                        onSuccess = { if (cont.isActive) cont.resume(0) },
                        onFailure = { reason -> if (cont.isActive) cont.resume(if (reason == 0) -1 else reason) }
                    )
                }
            } ?: -3
            if (code == 0) return@withLock true
            if (code == WifiP2pManager.P2P_UNSUPPORTED) break
            if (attempt < 2) delay(450L)
        }
        false
    }

    suspend fun removeGroupIfAny() = commandMutex.withLock {
        if (!isAvailable()) return@withLock
        wifiDirectManager.disconnect()
        _connectionSnapshot.value = null
    }

    fun groupContainsMac(group: WifiP2pGroup?, targetMac: String?): Boolean {
        if (targetMac.isNullOrBlank()) return false
        if (group == null) return false
        if (group.owner?.deviceAddress.equals(targetMac, ignoreCase = true)) return true
        return group.clientList.any { it.deviceAddress.equals(targetMac, ignoreCase = true) }
    }

    companion object {
        fun isPhoneOrTabletDeviceType(primaryDeviceType: String?): Boolean {
            if (primaryDeviceType.isNullOrBlank()) return true
            val major = primaryDeviceType.substringBefore('-').trim()
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
