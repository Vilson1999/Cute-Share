package com.example.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.example.model.CartoonAvatars
import com.example.model.DiscoveredDevice
import com.example.model.FileCategory
import com.example.model.SharedFile
import com.example.model.TransferFileItem
import com.example.model.TransferSession
import com.example.model.TransferStatus
import com.example.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * P2PTransferManager handles direct device-to-device file discovery and high-speed offline
 * transfers using Android's Wi-Fi Direct (WifiP2pManager) API, DNS Service Discovery (DNS-SD),
 * and TCP byte streaming.
 */
class P2PTransferManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    // Current transfer session
    private val _currentSession = MutableStateFlow<TransferSession?>(null)
    val currentSession: StateFlow<TransferSession?> = _currentSession.asStateFlow()

    // Transfer history
    private val _transferHistory = MutableStateFlow<List<TransferSession>>(emptyList())
    val transferHistory: StateFlow<List<TransferSession>> = _transferHistory.asStateFlow()

    // Discovered Wi-Fi Direct peers (mapped with CuteShare profile names and cute avatars)
    private val _discoveredPeers = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredDevice>> = _discoveredPeers.asStateFlow()

    // Incoming transfer request prompt
    data class IncomingTransferRequest(
        val sender: DiscoveredDevice,
        val files: List<SharedFile>,
        val totalSize: Long,
        val acceptAction: () -> Unit,
        val declineAction: () -> Unit
    )
    private val _incomingRequest = MutableStateFlow<IncomingTransferRequest?>(null)
    val incomingRequest: StateFlow<IncomingTransferRequest?> = _incomingRequest.asStateFlow()

    // Wi-Fi Direct System APIs
    private val wifiP2pManager: WifiP2pManager? = try {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    } catch (_: Exception) {
        null
    }
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var isP2pReceiverRegistered = false

    private val discoveredDevicesMap = ConcurrentHashMap<String, DiscoveredDevice>()
    private val macToCuteProfileMap = ConcurrentHashMap<String, Pair<String, Int>>()
    @Volatile
    private var latestP2pGroupOwnerIp: String? = null

    // Sockets and Jobs
    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var receiverServerJob: Job? = null
    private var receiverBeaconJob: Job? = null
    private var activeTransferJob: Job? = null
    private var peerDiscoveryPollingJob: Job? = null
    private var udpDiscoveryJob: Job? = null
    private var dnsSdServiceInfo: WifiP2pDnsSdServiceInfo? = null
    private var dnsSdServiceRequest: WifiP2pDnsSdServiceRequest? = null

    companion object {
        const val UDP_DISCOVERY_PORT = 8889
        const val TCP_TRANSFER_PORT = NetworkUtils.WIFI_DIRECT_DEFAULT_PORT
        const val SERVICE_TYPE = "_cuteshare._tcp"
        const val DOWNLOAD_SUBFOLDER = "Cute Share"

        fun sanitizeDownloadFileName(rawName: String, timestamp: Long = System.currentTimeMillis()): String {
            val baseLeaf = rawName.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[:*?\"<>|\\u0000-\\u001F]"), "_")
                .trim()
                .trimStart('.', ' ')
            if (baseLeaf.isBlank() || baseLeaf == "." || baseLeaf == "..") {
                return "CuteShare_$timestamp"
            }
            val dotIdx = baseLeaf.lastIndexOf('.')
            return if (dotIdx in 1 until baseLeaf.length - 1) {
                val stem = baseLeaf.substring(0, dotIdx).take(150).ifBlank { "CuteShare_$timestamp" }
                val ext = baseLeaf.substring(dotIdx + 1).take(16)
                "$stem.$ext"
            } else {
                baseLeaf.take(160)
            }
        }
    }

    // Wi-Fi P2P BroadcastReceiver
    private val wifiP2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val isP2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    if (!isP2pEnabled) {
                        // Wi-Fi Direct disabled
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestP2pPeers()
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val p2pInfo = IntentCompat.getParcelableExtra(
                        intent,
                        WifiP2pManager.EXTRA_WIFI_P2P_INFO,
                        WifiP2pInfo::class.java
                    )
                    if (p2pInfo?.groupFormed == true) {
                        requestConnectionInfo()
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Local device information updated
                }
            }
        }
    }

    private fun hasWifiDirectPermission(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED || hasFineLocation
        } else {
            hasFineLocation
        }
    }

    init {
        try {
            if (wifiP2pManager != null) {
                p2pChannel = wifiP2pManager.initialize(context, context.mainLooper, null)
            }
        } catch (_: Exception) {}
    }

    private fun registerP2pReceiver() {
        if (!isP2pReceiverRegistered) {
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
                isP2pReceiverRegistered = true
            } catch (_: Exception) {}
        }
    }

    private fun unregisterP2pReceiver() {
        if (isP2pReceiverRegistered) {
            try {
                context.unregisterReceiver(wifiP2pReceiver)
            } catch (_: Exception) {}
            isP2pReceiverRegistered = false
        }
    }

    // =========================================================================
    // WI-FI DIRECT SENDER: DISCOVER PEERS VIA DNS-SD, UDP BEACON, & P2P
    // =========================================================================

    fun startDiscovery(senderProfile: UserProfile) {
        stopDiscovery()
        discoveredDevicesMap.clear()
        _discoveredPeers.value = emptyList()

        registerP2pReceiver()

        val channel = p2pChannel
        val manager = wifiP2pManager

        // 1. Setup Wi-Fi Direct DNS-SD Service Discovery
        if (manager != null && channel != null && hasWifiDirectPermission()) {
            try {
                manager.clearServiceRequests(channel, null)
                val txtListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomain, record, device ->
                    val cuteName = record["name"]
                    val avatarStr = record["avatar"]
                    val avatarIdxStr = record["avatarIdx"]
                    val portStr = record["port"]
                    val resolvedName = if (!cuteName.isNullOrBlank()) cuteName else "Cute Friend"
                    val avatarRes = CartoonAvatars.resolveAvatarRes(avatarStr, avatarIdxStr, resolvedName)
                    val port = portStr?.toIntOrNull() ?: TCP_TRANSFER_PORT

                    val id = device.deviceAddress ?: fullDomain
                    macToCuteProfileMap[id] = Pair(resolvedName, avatarRes)

                    val directPeer = DiscoveredDevice(
                        id = id,
                        name = resolvedName,
                        avatarRes = avatarRes,
                        ip = NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP,
                        port = port,
                        p2pMacAddress = device.deviceAddress,
                        supports5Ghz = true,
                        is5GhzActive = true,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                    discoveredDevicesMap[id] = directPeer
                    publishDiscoveredPeers()
                }

                val servListener = WifiP2pManager.DnsSdServiceResponseListener { _, _, _ -> }
                manager.setDnsSdResponseListeners(channel, servListener, txtListener)

                val req = WifiP2pDnsSdServiceRequest.newInstance()
                dnsSdServiceRequest = req
                manager.addServiceRequest(channel, req, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        if (!hasWifiDirectPermission()) return
                        try {
                            manager.discoverServices(channel, null)
                        } catch (_: SecurityException) {
                        } catch (_: Exception) {}
                    }
                    override fun onFailure(code: Int) {}
                })
            } catch (_: SecurityException) {
            } catch (_: Exception) {}

            try {
                manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        requestP2pPeers()
                    }
                    override fun onFailure(reason: Int) {}
                })
            } catch (_: SecurityException) {
            } catch (_: Exception) {}
        }

        // 2. Start UDP Discovery Listener for CuteShare Beacons
        udpDiscoveryJob = scope.launch(Dispatchers.IO) {
            var udpSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(UDP_DISCOVERY_PORT))
                    soTimeout = 3000
                }
                val buffer = ByteArray(1024)
                while (isActive && !udpSocket.isClosed) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (text.startsWith("CUTESHARE_BEACON|")) {
                            val parts = text.split("|")
                            if (parts.size >= 4) {
                                val cuteName = parts[1].ifBlank { "Cute Friend" }
                                val avatarRes = CartoonAvatars.resolveAvatarRes(
                                    rawValue = parts[2],
                                    indexHint = parts.getOrNull(5),
                                    fallbackSeed = cuteName
                                )
                                val port = parts[3].toIntOrNull() ?: TCP_TRANSFER_PORT
                                val senderIp = packet.address.hostAddress ?: ""
                                if (senderIp.isNotEmpty() && senderIp != NetworkUtils.getLocalIpAddress()) {
                                    val id = "udp_${senderIp.replace('.', '_')}"
                                    val peer = DiscoveredDevice(
                                        id = id,
                                        name = cuteName,
                                        avatarRes = avatarRes,
                                        ip = senderIp,
                                        port = port,
                                        supports5Ghz = true,
                                        is5GhzActive = true,
                                        lastSeenTimestamp = System.currentTimeMillis()
                                    )
                                    discoveredDevicesMap[id] = peer
                                    publishDiscoveredPeers()
                                }
                            }
                        }
                    } catch (_: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { udpSocket?.close() } catch (_: Exception) {}
            }
        }

        // 3. Periodic polling and TCP Subnet / Wi-Fi Direct GO ping
        peerDiscoveryPollingJob = scope.launch(Dispatchers.IO) {
            var cycle = 0
            while (isActive) {
                requestP2pPeers()
                scanLocalSubnetForReceivers()

                if (cycle > 0 && cycle % 2 == 0 && manager != null && channel != null && hasWifiDirectPermission()) {
                    try {
                        if (dnsSdServiceRequest != null) {
                            manager.discoverServices(channel, null)
                        }
                        manager.discoverPeers(channel, null)
                    } catch (_: SecurityException) {
                    } catch (_: Exception) {}
                }
                cycle++

                // Remove peers not seen in the last 20 seconds
                val now = System.currentTimeMillis()
                val expiredIds = discoveredDevicesMap.filter { (id, peer) ->
                    !id.startsWith("manual_") && (now - peer.lastSeenTimestamp > 20000L)
                }.keys
                if (expiredIds.isNotEmpty()) {
                    expiredIds.forEach { discoveredDevicesMap.remove(it) }
                    publishDiscoveredPeers()
                }

                delay(2500L)
            }
        }
    }

    private fun requestP2pPeers() {
        val channel = p2pChannel ?: return
        val manager = wifiP2pManager ?: return
        if (!hasWifiDirectPermission()) return
        try {
            manager.requestPeers(channel) { peerList: WifiP2pDeviceList? ->
                if (peerList != null) {
                    val peers = peerList.deviceList
                    if (peers.isNotEmpty()) {
                        var addedOrUpdated = false
                        for (dev in peers) {
                            val id = dev.deviceAddress ?: continue
                            val mappedProfile = macToCuteProfileMap[id]
                            val dName = dev.deviceName
                            val isAdvertisingCuteShare = mappedProfile != null ||
                                (!dName.isNullOrBlank() && dName.startsWith("CuteShare_"))
                            if (!isAdvertisingCuteShare) continue

                            val name = mappedProfile?.first
                                ?: dName!!.removePrefix("CuteShare_").ifBlank { "Cute Friend" }
                            val avatarRes = mappedProfile?.second
                                ?: CartoonAvatars.resolveAvatarRes(null, null, id)

                            val directPeer = DiscoveredDevice(
                                id = id,
                                name = name,
                                avatarRes = avatarRes,
                                ip = NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP,
                                port = TCP_TRANSFER_PORT,
                                p2pMacAddress = id,
                                supports5Ghz = true,
                                is5GhzActive = true,
                                lastSeenTimestamp = System.currentTimeMillis()
                            )
                            discoveredDevicesMap[id] = directPeer
                            addedOrUpdated = true
                        }
                        if (addedOrUpdated) {
                            publishDiscoveredPeers()
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
    }

    private suspend fun scanLocalSubnetForReceivers() = withContext(Dispatchers.IO) {
        val myIp = NetworkUtils.getLocalIpAddress()
        val candidateIps = mutableListOf<String>()
        candidateIps.add(NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP)
        candidateIps.add("192.168.43.1")

        if (myIp != "127.0.0.1") {
            val prefix = NetworkUtils.getSubnetPrefix(myIp)
            if (prefix != null) {
                val myHost = myIp.substringAfterLast('.', "0").toIntOrNull() ?: 0
                candidateIps.add("${prefix}1")
                for (i in 1..8) {
                    val hUp = myHost + i
                    val hDown = myHost - i
                    if (hUp in 2..254) candidateIps.add("$prefix$hUp")
                    if (hDown in 2..254) candidateIps.add("$prefix$hDown")
                }
            }
        }

        for (targetIp in candidateIps.distinct()) {
            if (targetIp == myIp) continue
            try {
                Socket().use { s ->
                    s.tcpNoDelay = true
                    s.connect(InetSocketAddress(targetIp, TCP_TRANSFER_PORT), 200)
                    val out = BufferedOutputStream(s.getOutputStream())
                    val inStream = BufferedInputStream(s.getInputStream())
                    out.write("CUTESHARE_PING\n".toByteArray(Charsets.UTF_8))
                    out.flush()
                    val resp = readAsciiLine(inStream, maxLen = 256)
                    if (resp.startsWith("CUTESHARE_PONG|")) {
                        val parts = resp.split("|")
                        if (parts.size >= 4) {
                            val name = parts[1].ifBlank { "Cute Friend" }
                            val avatarRes = CartoonAvatars.resolveAvatarRes(
                                rawValue = parts[2],
                                indexHint = parts.getOrNull(5),
                                fallbackSeed = name
                            )
                            val port = parts[3].toIntOrNull() ?: TCP_TRANSFER_PORT
                            val id = "lan_${targetIp.replace('.', '_')}"
                            val peer = DiscoveredDevice(
                                id = id,
                                name = name,
                                avatarRes = avatarRes,
                                ip = targetIp,
                                port = port,
                                supports5Ghz = true,
                                is5GhzActive = true,
                                lastSeenTimestamp = System.currentTimeMillis()
                            )
                            discoveredDevicesMap[id] = peer
                            publishDiscoveredPeers()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun publishDiscoveredPeers() {
        val merged = mutableMapOf<String, DiscoveredDevice>()
        for (peer in discoveredDevicesMap.values) {
            val mergeKey = if (peer.id.startsWith("manual_")) {
                peer.id
            } else {
                "${peer.name.trim().lowercase(Locale.ROOT)}_${peer.avatarRes}"
            }
            val existing = merged[mergeKey]
            if (existing == null) {
                merged[mergeKey] = peer
            } else {
                val peerIsLan = peer.ip.isNotBlank() && peer.ip != NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP
                val existingIsLan = existing.ip.isNotBlank() && existing.ip != NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP
                val preferredIp = when {
                    peerIsLan && !existingIsLan -> peer.ip
                    !peerIsLan && existingIsLan -> existing.ip
                    peer.lastSeenTimestamp >= existing.lastSeenTimestamp -> peer.ip
                    else -> existing.ip
                }
                merged[mergeKey] = existing.copy(
                    id = if (preferredIp == peer.ip) peer.id else existing.id,
                    ip = preferredIp,
                    port = if (preferredIp == peer.ip) peer.port else existing.port,
                    p2pMacAddress = existing.p2pMacAddress ?: peer.p2pMacAddress,
                    lastSeenTimestamp = maxOf(existing.lastSeenTimestamp, peer.lastSeenTimestamp)
                )
            }
        }
        _discoveredPeers.value = merged.values.toList()
    }

    fun stopDiscovery() {
        peerDiscoveryPollingJob?.cancel()
        peerDiscoveryPollingJob = null

        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = null

        val channel = p2pChannel
        val manager = wifiP2pManager
        if (manager != null && channel != null) {
            try {
                manager.stopPeerDiscovery(channel, null)
                dnsSdServiceRequest?.let { manager.removeServiceRequest(channel, it, null) }
                dnsSdServiceRequest = null
            } catch (_: Exception) {}
        }

        unregisterP2pReceiver()
        discoveredDevicesMap.clear()
        _discoveredPeers.value = emptyList()
    }

    private fun requestConnectionInfo() {
        val channel = p2pChannel ?: return
        val manager = wifiP2pManager ?: return
        try {
            manager.requestConnectionInfo(channel) { info: WifiP2pInfo? ->
                if (info?.groupFormed == true) {
                    latestP2pGroupOwnerIp = info.groupOwnerAddress?.hostAddress
                }
            }
        } catch (_: Exception) {}
    }

    // =========================================================================
    // WI-FI DIRECT RECEIVER: ADVERTISE CUTESHARE USER & ACCEPT TCP CONNECTIONS
    // =========================================================================

    fun startReceiver(
        userProfile: UserProfile,
        onSessionStarted: () -> Unit
    ) {
        stopReceiver()
        latestP2pGroupOwnerIp = null
        registerP2pReceiver()

        val channel = p2pChannel
        val manager = wifiP2pManager
        val safeName = sanitizeWireField(userProfile.name).ifBlank { "Cute Friend" }
        val avatarIdx = CartoonAvatars.avatarIndexOf(userProfile.avatarRes)

        // 1. Create Autonomous Wi-Fi Direct Group so this receiver is Group Owner (192.168.49.1)
        if (manager != null && channel != null && hasWifiDirectPermission()) {
            try {
                manager.clearLocalServices(channel, null)
                manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}
                    override fun onFailure(reason: Int) {}
                })
            } catch (_: SecurityException) {
            } catch (_: Exception) {}

            // 2. Register DNS-SD Local Service advertising CuteShare user name and avatar
            try {
                val record = HashMap<String, String>().apply {
                    put("name", safeName)
                    put("avatar", userProfile.avatarRes.toString())
                    put("avatarIdx", avatarIdx.toString())
                    put("port", TCP_TRANSFER_PORT.toString())
                }
                val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                    "CuteShare_$safeName",
                    SERVICE_TYPE,
                    record
                )
                dnsSdServiceInfo = serviceInfo
                manager.addLocalService(channel, serviceInfo, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}
                    override fun onFailure(arg0: Int) {}
                })
            } catch (_: SecurityException) {
            } catch (_: Exception) {}
        }

        // 3. UDP Presence Beacon: Broadcasts CuteShare user name & avatar every 1.2 seconds
        receiverBeaconJob = scope.launch(Dispatchers.IO) {
            var udpSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket()
                udpSocket.broadcast = true
                while (isActive) {
                    val myIp = NetworkUtils.getLocalIpAddress()
                    val payload = "CUTESHARE_BEACON|$safeName|${userProfile.avatarRes}|$TCP_TRANSFER_PORT|$myIp|$avatarIdx"
                    val bytes = payload.toByteArray(Charsets.UTF_8)
                    try {
                        val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), UDP_DISCOVERY_PORT)
                        udpSocket.send(packet)

                        // Also send to Wi-Fi Direct group owner broadcast
                        val p2pBcast = InetAddress.getByName("192.168.49.255")
                        val p2pPacket = DatagramPacket(bytes, bytes.size, p2pBcast, UDP_DISCOVERY_PORT)
                        udpSocket.send(p2pPacket)
                    } catch (_: Exception) {}
                    delay(1200L)
                }
            } catch (_: Exception) {
            } finally {
                try { udpSocket?.close() } catch (_: Exception) {}
            }
        }

        // 4. Bind High-Speed TCP ServerSocket on port 8888
        try {
            serverSocket = ServerSocket(TCP_TRANSFER_PORT).apply {
                reuseAddress = true
                receiveBufferSize = 524288
            }
        } catch (_: Exception) {
            try {
                serverSocket = ServerSocket(0).apply {
                    reuseAddress = true
                    receiveBufferSize = 524288
                }
            } catch (_: Exception) {
                return
            }
        }

        val server = serverSocket ?: return

        receiverServerJob = scope.launch(Dispatchers.IO) {
            while (isActive && !server.isClosed) {
                try {
                    val socket = server.accept()
                    socket.tcpNoDelay = true
                    socket.receiveBufferSize = 524288
                    socket.sendBufferSize = 524288
                    launch {
                        handleIncomingWifiDirectConnection(socket, userProfile, onSessionStarted)
                    }
                } catch (_: Exception) {
                    if (!isActive) break
                }
            }
        }
    }

    private suspend fun handleIncomingWifiDirectConnection(
        socket: Socket,
        userProfile: UserProfile,
        onSessionStarted: () -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 15000
            val inStream = BufferedInputStream(socket.getInputStream(), 65536)
            val outStream = BufferedOutputStream(socket.getOutputStream(), 65536)

            val firstLine = readAsciiLine(inStream, maxLen = 256)

            // TCP Ping response with CuteShare user name and cute avatar
            if (firstLine.startsWith("CUTESHARE_PING")) {
                val ip = NetworkUtils.getLocalIpAddress()
                val safeName = sanitizeWireField(userProfile.name).ifBlank { "Cute Friend" }
                val avatarIdx = CartoonAvatars.avatarIndexOf(userProfile.avatarRes)
                val resp = "CUTESHARE_PONG|$safeName|${userProfile.avatarRes}|$TCP_TRANSFER_PORT|$ip|$avatarIdx\n"
                outStream.write(resp.toByteArray(Charsets.UTF_8))
                outStream.flush()
                socket.close()
                return@withContext
            }

            if (!firstLine.startsWith("CUTESHARE_WIFIDIRECT_v1")) {
                socket.close()
                return@withContext
            }

            activeSocket = socket

            // Sender metadata line: senderId|senderName|senderAvatarRes|fileCount|totalBytes|senderAvatarIdx
            val senderLine = readAsciiLine(inStream, maxLen = 512)
            val sParts = senderLine.split("|")
            val senderName = sParts.getOrNull(1)?.ifBlank { "Cute Peer" } ?: "Cute Peer"
            val senderAvatarRes = CartoonAvatars.resolveAvatarRes(
                rawValue = sParts.getOrNull(2),
                indexHint = sParts.getOrNull(5),
                fallbackSeed = senderName
            )

            val incomingFiles = mutableListOf<SharedFile>()
            var line = readAsciiLine(inStream, maxLen = 1024)
            while (line.isNotEmpty() && line != "END_HEADER") {
                val fParts = line.split("|")
                if (fParts.size >= 4) {
                    val fId = fParts[0]
                    val fName = fParts[1]
                    val fSize = fParts[2].toLongOrNull() ?: 0L
                    val fCategory = try { FileCategory.valueOf(fParts[3]) } catch (_: Exception) { FileCategory.DOCUMENTS }

                    incomingFiles.add(
                        SharedFile(
                            id = fId,
                            name = fName,
                            sizeBytes = fSize,
                            formattedSize = com.example.model.formatBytes(fSize),
                            category = fCategory,
                            extension = fName.substringAfterLast('.', "").uppercase(Locale.ROOT),
                            lastModified = "Wi-Fi Direct"
                        )
                    )
                }
                line = readAsciiLine(inStream, maxLen = 1024)
            }

            val items = incomingFiles.map { file ->
                TransferFileItem(
                    id = file.id,
                    name = file.name,
                    sizeBytes = file.sizeBytes,
                    transferredBytes = 0L,
                    progress = 0f,
                    status = TransferStatus.TRANSFERRING,
                    category = file.category
                )
            }

            val session = TransferSession(
                id = UUID.randomUUID().toString(),
                isOutgoing = false,
                peerName = senderName,
                avatarRes = senderAvatarRes,
                files = items,
                isPaused = false,
                isCancelled = false,
                isCompleted = false,
                currentSpeedBytesPerSec = 0L,
                remainingSeconds = 0L,
                wifiBand = "Wi-Fi Direct"
            )
            _currentSession.value = session
            _incomingRequest.value = null

            // Confirm acceptance
            outStream.write("ACCEPT\n".toByteArray(Charsets.UTF_8))
            outStream.flush()

            // Open TransferScreen on Receiver immediately
            withContext(Dispatchers.Main) {
                onSessionStarted()
            }

            // Stream actual file bytes directly into Download/Cute Share/
            receiveFilesStream(socket, inStream, outStream, incomingFiles)
        } catch (_: Exception) {
            _currentSession.update { s ->
                if (s != null && !s.isCompleted) {
                    s.copy(
                        isCancelled = true,
                        currentSpeedBytesPerSec = 0L,
                        remainingSeconds = 0L,
                        files = s.files.map { f -> if (!f.isFinished) f.copy(status = TransferStatus.CANCELLED) else f }
                    )
                } else s
            }
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private class DownloadDestination(
        val outputStream: OutputStream,
        val mediaStoreUri: Uri? = null,
        val legacyFile: File? = null
    )

    private fun openDownloadDestination(fileName: String): DownloadDestination? {
        val safeName = sanitizeDownloadFileName(fileName)
        val dotIdx = safeName.lastIndexOf('.')
        val stem = if (dotIdx > 0) safeName.substring(0, dotIdx) else safeName
        val ext = if (dotIdx in 1 until safeName.length - 1) safeName.substring(dotIdx + 1) else ""
        val detectedMime = if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.ROOT)) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collections = listOf(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            ).distinct()
            val candidateNames = buildList {
                add(safeName)
                for (i in 1..5) {
                    add(if (ext.isNotEmpty()) "$stem ($i).$ext" else "$stem ($i)")
                }
                val ts = System.currentTimeMillis()
                add(if (ext.isNotEmpty()) "${stem}_$ts.$ext" else "${stem}_$ts")
            }
            val candidateMimes = listOf(detectedMime, "application/octet-stream").distinct()
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_SUBFOLDER"

            for (collectionUri in collections) {
                for (candidateName in candidateNames) {
                    for (mime in candidateMimes) {
                        var insertedUri: Uri? = null
                        try {
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, candidateName)
                                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                            insertedUri = context.contentResolver.insert(collectionUri, values)
                            if (insertedUri != null) {
                                val os = context.contentResolver.openOutputStream(insertedUri)
                                if (os != null) {
                                    return DownloadDestination(outputStream = os, mediaStoreUri = insertedUri)
                                } else {
                                    try { context.contentResolver.delete(insertedUri, null, null) } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {
                            if (insertedUri != null) {
                                try { context.contentResolver.delete(insertedUri, null, null) } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }

        val canUsePublicDownloadsDirect = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())

        val candidateDirs = buildList {
            if (canUsePublicDownloadsDirect) {
                @Suppress("DEPRECATION")
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                add(File(publicDownloads, DOWNLOAD_SUBFOLDER))
            }
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                add(File(it, DOWNLOAD_SUBFOLDER))
            }
            add(File(context.filesDir, DOWNLOAD_SUBFOLDER))
        }

        for (targetDir in candidateDirs) {
            try {
                if (!targetDir.exists()) targetDir.mkdirs()
                if (!targetDir.exists() || !targetDir.isDirectory) continue

                var destFile = File(targetDir, safeName)
                var counter = 1
                while (destFile.exists()) {
                    val newName = if (ext.isNotEmpty()) "$stem ($counter).$ext" else "$stem ($counter)"
                    destFile = File(targetDir, newName)
                    counter++
                }
                return DownloadDestination(outputStream = FileOutputStream(destFile), legacyFile = destFile)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun finalizeDownloadDestination(dest: DownloadDestination, completed: Boolean) {
        if (dest.mediaStoreUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (completed) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    context.contentResolver.update(dest.mediaStoreUri, values, null, null)
                } else {
                    context.contentResolver.delete(dest.mediaStoreUri, null, null)
                }
            } catch (_: Exception) {}
        } else if (dest.legacyFile != null) {
            if (completed) {
                try {
                    MediaScannerConnection.scanFile(context, arrayOf(dest.legacyFile.absolutePath), null, null)
                } catch (_: Exception) {}
            } else {
                try {
                    dest.legacyFile.delete()
                } catch (_: Exception) {}
            }
        }
    }

    private suspend fun receiveFilesStream(
        socket: Socket,
        inStream: BufferedInputStream,
        outStream: BufferedOutputStream,
        files: List<SharedFile>
    ) = withContext(Dispatchers.IO) {
        socket.soTimeout = 0

        val items = files.map { file ->
            TransferFileItem(
                id = file.id,
                name = file.name,
                sizeBytes = file.sizeBytes,
                transferredBytes = 0L,
                progress = 0f,
                status = TransferStatus.TRANSFERRING,
                category = file.category
            )
        }

        val buffer = ByteArray(65536)
        var totalBytesTransferred = 0L
        val totalBytes = items.sumOf { it.sizeBytes }
        var completedFilesCount = 0

        fun cancelRemainingItems(failedItemId: String? = null) {
            _currentSession.update { s ->
                s?.copy(
                    isCancelled = true,
                    currentSpeedBytesPerSec = 0L,
                    remainingSeconds = 0L,
                    files = s.files.map { f ->
                        if (f.id == failedItemId || !f.isFinished) f.copy(status = TransferStatus.CANCELLED) else f
                    }
                )
            }
        }

        for (item in items) {
            if (_currentSession.value?.isCancelled == true) break

            val fileHeader = readAsciiLine(inStream, maxLen = 512)
            if (!fileHeader.startsWith("START_FILE")) {
                cancelRemainingItems(item.id)
                break
            }

            val parts = fileHeader.split(":")
            val targetSize = parts.getOrNull(2)?.toLongOrNull() ?: item.sizeBytes

            val dest = openDownloadDestination(item.name)
            if (dest == null) {
                cancelRemainingItems(item.id)
                break
            }

            var fileBytesReceived = 0L
            var lastSpeedCheckTime = System.currentTimeMillis()
            val fileStartTime = lastSpeedCheckTime
            var bytesSinceLastCheck = 0L
            var fileCompleted = false

            try {
                dest.outputStream.use { fileOut ->
                    while (fileBytesReceived < targetSize) {
                        if (_currentSession.value?.isCancelled == true) break
                        while (_currentSession.value?.isPaused == true && _currentSession.value?.isCancelled != true) {
                            delay(200)
                            lastSpeedCheckTime = System.currentTimeMillis()
                        }
                        if (_currentSession.value?.isCancelled == true) break

                        val toRead = (targetSize - fileBytesReceived).coerceAtMost(buffer.size.toLong()).toInt()
                        val read = inStream.read(buffer, 0, toRead)
                        if (read == -1) break

                        fileOut.write(buffer, 0, read)
                        fileBytesReceived += read
                        totalBytesTransferred += read
                        bytesSinceLastCheck += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSpeedCheckTime
                        if (elapsed >= 150 || fileBytesReceived >= targetSize) {
                            val windowElapsed = if (elapsed > 0) elapsed else (now - fileStartTime).coerceAtLeast(1L)
                            val windowBytes = if (elapsed > 0) bytesSinceLastCheck else fileBytesReceived
                            val speedBytesSec = (windowBytes * 1000L) / windowElapsed.coerceAtLeast(1L)
                            val remainingBytes = (totalBytes - totalBytesTransferred).coerceAtLeast(0L)
                            val remSecs = if (speedBytesSec > 0 && remainingBytes > 0) {
                                (remainingBytes / speedBytesSec).coerceAtLeast(1L)
                            } else 0L

                            if (elapsed >= 150) {
                                lastSpeedCheckTime = now
                                bytesSinceLastCheck = 0L
                            }

                            _currentSession.update { s ->
                                s?.copy(
                                    currentSpeedBytesPerSec = speedBytesSec,
                                    remainingSeconds = remSecs,
                                    files = s.files.map { f ->
                                        if (f.id == item.id) {
                                            f.copy(
                                                transferredBytes = fileBytesReceived,
                                                progress = if (targetSize > 0) (fileBytesReceived.toFloat() / targetSize).coerceIn(0f, 1f) else 1f
                                            )
                                        } else f
                                    }
                                )
                            }
                        }
                    }
                    fileOut.flush()
                }
                fileCompleted = fileBytesReceived >= targetSize && _currentSession.value?.isCancelled != true
            } finally {
                finalizeDownloadDestination(dest, completed = fileCompleted)
            }

            if (!fileCompleted) {
                cancelRemainingItems(item.id)
                break
            }

            completedFilesCount++
            _currentSession.update { s ->
                s?.copy(
                    files = s.files.map { f ->
                        if (f.id == item.id) f.copy(
                            transferredBytes = fileBytesReceived,
                            progress = 1f,
                            status = TransferStatus.COMPLETED
                        ) else f
                    }
                )
            }

            outStream.write("FILE_DONE:${item.id}\n".toByteArray(Charsets.UTF_8))
            outStream.flush()
        }

        val current = _currentSession.value
        if (current != null && !current.isCancelled && completedFilesCount == items.size) {
            val completedSession = current.copy(
                isCompleted = true,
                currentSpeedBytesPerSec = 0L,
                remainingSeconds = 0L
            )
            _currentSession.value = completedSession
            _transferHistory.update { listOf(completedSession) + it }
        } else if (current != null) {
            _currentSession.value = current.copy(
                isCancelled = true,
                currentSpeedBytesPerSec = 0L,
                remainingSeconds = 0L
            )
        }

        try { socket.close() } catch (_: Exception) {}
    }

    fun stopReceiver() {
        receiverServerJob?.cancel()
        receiverServerJob = null

        receiverBeaconJob?.cancel()
        receiverBeaconJob = null

        val channel = p2pChannel
        val manager = wifiP2pManager
        if (manager != null && channel != null) {
            try {
                manager.removeGroup(channel, null)
                dnsSdServiceInfo?.let { manager.removeLocalService(channel, it, null) }
                dnsSdServiceInfo = null
            } catch (_: Exception) {}
        }

        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null

        unregisterP2pReceiver()
    }

    // =========================================================================
    // WI-FI DIRECT SENDER: REAL P2P TCP BYTE STREAMING
    // =========================================================================

    fun startOutgoingTransfer(
        targetPeer: DiscoveredDevice,
        files: List<SharedFile>,
        senderProfile: UserProfile
    ) {
        activeTransferJob?.cancel()

        val items = files.map { file ->
            TransferFileItem(
                id = file.id,
                name = file.name,
                sizeBytes = file.sizeBytes,
                transferredBytes = 0L,
                progress = 0f,
                status = TransferStatus.TRANSFERRING,
                category = file.category
            )
        }

        val session = TransferSession(
            id = UUID.randomUUID().toString(),
            isOutgoing = true,
            peerName = targetPeer.name,
            avatarRes = targetPeer.avatarRes,
            files = items,
            isPaused = false,
            isCancelled = false,
            isCompleted = false,
            currentSpeedBytesPerSec = 0L,
            remainingSeconds = 0L,
            wifiBand = "Wi-Fi Direct"
        )
        _currentSession.value = session

        val channel = p2pChannel
        val manager = wifiP2pManager
        val targetMac = targetPeer.p2pMacAddress ?: targetPeer.id.takeIf { it.contains(":") }

        activeTransferJob = scope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                // Fast-path: if peer already has a direct LAN/Wi-Fi IP, try immediate socket connect first
                val hasLanIp = targetPeer.ip.isNotEmpty() &&
                    !targetPeer.ip.startsWith("127.") &&
                    targetPeer.ip != NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP
                var connected = false

                if (hasLanIp) {
                    try {
                        val directSocket = Socket()
                        directSocket.tcpNoDelay = true
                        directSocket.sendBufferSize = 524288
                        directSocket.receiveBufferSize = 524288
                        directSocket.connect(InetSocketAddress(targetPeer.ip, targetPeer.port), 600)
                        socket = directSocket
                        connected = true
                    } catch (_: Exception) {}
                }

                // Offline / P2P path: connect via Wi-Fi Direct group if not already connected over LAN
                if (!connected && manager != null && channel != null && targetMac != null && hasWifiDirectPermission()) {
                    val config = WifiP2pConfig().apply {
                        deviceAddress = targetMac
                        groupOwnerIntent = 0 // Receiver is Group Owner
                    }
                    try {
                        manager.connect(channel, config, null)
                    } catch (_: SecurityException) {
                    } catch (_: Exception) {}
                }

                var attempts = 0
                val maxAttempts = 28 // Up to ~18 seconds for Wi-Fi Direct group formation & DHCP

                while (!connected && attempts < maxAttempts && isActive && _currentSession.value?.isCancelled != true) {
                    attempts++
                    val candidateHosts = mutableListOf<String>()
                    latestP2pGroupOwnerIp?.takeIf { it.isNotEmpty() }?.let { candidateHosts.add(it) }
                    if (targetPeer.ip.isNotEmpty() && !targetPeer.ip.startsWith("127.")) {
                        candidateHosts.add(targetPeer.ip)
                    }
                    candidateHosts.add(NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP)
                    candidateHosts.add("192.168.43.1")

                    for (host in candidateHosts.distinct()) {
                        try {
                            val testSocket = Socket()
                            testSocket.tcpNoDelay = true
                            testSocket.sendBufferSize = 524288
                            testSocket.receiveBufferSize = 524288
                            testSocket.connect(InetSocketAddress(host, targetPeer.port), 800)
                            socket = testSocket
                            connected = true
                            break
                        } catch (_: Exception) {}
                    }
                    if (!connected) {
                        delay(500L)
                    }
                }

                if (!connected || socket == null) {
                    // Mark cancelled/failed, NEVER fake completion
                    _currentSession.update {
                        it?.copy(
                            isCancelled = true,
                            currentSpeedBytesPerSec = 0L,
                            remainingSeconds = 0L,
                            files = it.files.map { f -> f.copy(status = TransferStatus.CANCELLED) }
                        )
                    }
                    return@launch
                }

                activeSocket = socket
                val outStream = BufferedOutputStream(socket.getOutputStream(), 65536)
                val inStream = BufferedInputStream(socket.getInputStream(), 65536)

                // Normalize exact file sizes before building wire manifest
                val resolvedFiles = files.map { file ->
                    val exactSize = resolveAccurateFileSize(file)
                    if (exactSize != file.sizeBytes) file.copy(sizeBytes = exactSize) else file
                }
                _currentSession.update { s ->
                    s?.copy(
                        files = s.files.map { item ->
                            val updatedSize = resolvedFiles.find { it.id == item.id }?.sizeBytes ?: item.sizeBytes
                            item.copy(sizeBytes = updatedSize)
                        }
                    )
                }

                // Transmit Header: protocol, sender profile, file manifest
                val safeSenderName = sanitizeWireField(senderProfile.name).ifBlank { "Cute Friend" }
                val senderAvatarIdx = CartoonAvatars.avatarIndexOf(senderProfile.avatarRes)
                val header = StringBuilder()
                header.append("CUTESHARE_WIFIDIRECT_v1\n")
                val totalBytes = resolvedFiles.sumOf { it.sizeBytes }
                header.append("${UUID.randomUUID().toString().take(8)}|$safeSenderName|${senderProfile.avatarRes}|${resolvedFiles.size}|$totalBytes|$senderAvatarIdx\n")
                for (file in resolvedFiles) {
                    val safeFileName = sanitizeWireField(file.name).ifBlank { "file_${file.id.take(6)}" }
                    header.append("${file.id}|$safeFileName|${file.sizeBytes}|${file.category.name}\n")
                }
                header.append("END_HEADER\n")

                outStream.write(header.toString().toByteArray(Charsets.UTF_8))
                outStream.flush()

                val response = readAsciiLine(inStream, maxLen = 64)
                if (response != "ACCEPT") {
                    _currentSession.update {
                        it?.copy(
                            isCancelled = true,
                            currentSpeedBytesPerSec = 0L,
                            files = it.files.map { f -> f.copy(status = TransferStatus.CANCELLED) }
                        )
                    }
                    return@launch
                }

                val buffer = ByteArray(65536)
                var totalBytesSent = 0L

                for (file in resolvedFiles) {
                    if (_currentSession.value?.isCancelled == true) break

                    val fileInputStream = openFileInputStream(file)
                        ?: throw IllegalStateException("Cannot open source file: ${file.name}")

                    val fileHeader = "START_FILE:${file.id}:${file.sizeBytes}\n"
                    outStream.write(fileHeader.toByteArray(Charsets.UTF_8))
                    outStream.flush()

                    var fileBytesSent = 0L
                    var lastSpeedCheck = System.currentTimeMillis()
                    val fileStartTime = lastSpeedCheck
                    var bytesSinceLastCheck = 0L

                    fileInputStream.use { input ->
                        while (fileBytesSent < file.sizeBytes) {
                            if (_currentSession.value?.isCancelled == true) break
                            while (_currentSession.value?.isPaused == true && _currentSession.value?.isCancelled != true) {
                                delay(200)
                                lastSpeedCheck = System.currentTimeMillis()
                            }
                            if (_currentSession.value?.isCancelled == true) break

                            val toRead = (file.sizeBytes - fileBytesSent).coerceAtMost(buffer.size.toLong()).toInt()
                            val read = input.read(buffer, 0, toRead)
                            if (read == -1) break

                            outStream.write(buffer, 0, read)
                            fileBytesSent += read
                            totalBytesSent += read
                            bytesSinceLastCheck += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastSpeedCheck
                            if (elapsed >= 150 || fileBytesSent >= file.sizeBytes) {
                                val windowElapsed = if (elapsed > 0) elapsed else (now - fileStartTime).coerceAtLeast(1L)
                                val windowBytes = if (elapsed > 0) bytesSinceLastCheck else fileBytesSent
                                val speed = (windowBytes * 1000L) / windowElapsed.coerceAtLeast(1L)
                                val remaining = (totalBytes - totalBytesSent).coerceAtLeast(0L)
                                val remSecs = if (speed > 0 && remaining > 0) {
                                    (remaining / speed).coerceAtLeast(1L)
                                } else 0L

                                if (elapsed >= 150) {
                                    lastSpeedCheck = now
                                    bytesSinceLastCheck = 0L
                                }

                                _currentSession.update { s ->
                                    s?.copy(
                                        currentSpeedBytesPerSec = speed,
                                        remainingSeconds = remSecs,
                                        files = s.files.map { f ->
                                            if (f.id == file.id) {
                                                f.copy(
                                                    transferredBytes = fileBytesSent,
                                                    progress = if (file.sizeBytes > 0) (fileBytesSent.toFloat() / file.sizeBytes).coerceIn(0f, 1f) else 1f
                                                )
                                            } else f
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (_currentSession.value?.isCancelled == true) break
                    if (fileBytesSent < file.sizeBytes) {
                        throw IllegalStateException("Unexpected EOF while reading ${file.name}")
                    }

                    outStream.flush()
                    val ack = readAsciiLine(inStream, maxLen = 128)
                    if (!ack.startsWith("FILE_DONE")) {
                        throw IllegalStateException("Receiver did not acknowledge file: ${file.name}")
                    }

                    _currentSession.update { s ->
                        s?.copy(
                            files = s.files.map { f ->
                                if (f.id == file.id) f.copy(
                                    transferredBytes = fileBytesSent,
                                    progress = 1f,
                                    status = TransferStatus.COMPLETED
                                ) else f
                            }
                        )
                    }
                }

                val current = _currentSession.value
                if (current != null && !current.isCancelled) {
                    val completedSession = current.copy(
                        isCompleted = true,
                        currentSpeedBytesPerSec = 0L,
                        remainingSeconds = 0L
                    )
                    _currentSession.value = completedSession
                    _transferHistory.update { listOf(completedSession) + it }
                } else if (current != null) {
                    _currentSession.value = current.copy(
                        currentSpeedBytesPerSec = 0L,
                        remainingSeconds = 0L
                    )
                }

            } catch (_: Exception) {
                // If real transfer fails, mark as cancelled, do NOT fake completion
                _currentSession.update {
                    it?.copy(
                        isCancelled = true,
                        currentSpeedBytesPerSec = 0L,
                        files = it.files.map { f -> f.copy(status = TransferStatus.CANCELLED) }
                    )
                }
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    fun addFilesToActiveSession(newFiles: List<SharedFile>) {
        if (newFiles.isEmpty()) return
        val newItems = newFiles.map { file ->
            TransferFileItem(
                id = file.id,
                name = file.name,
                sizeBytes = file.sizeBytes,
                transferredBytes = 0L,
                progress = 0f,
                status = TransferStatus.TRANSFERRING,
                category = file.category
            )
        }
        _currentSession.update { session ->
            if (session == null) return@update null
            session.copy(
                files = session.files + newItems,
                isCompleted = false,
                isCancelled = false
            )
        }
    }

    fun toggleSessionPause() {
        _currentSession.update { session ->
            if (session == null) return@update null
            val newPaused = !session.isPaused
            val updated = session.files.map { item ->
                if (!item.isFinished) item.copy(status = if (newPaused) TransferStatus.PAUSED else TransferStatus.TRANSFERRING)
                else item
            }
            session.copy(isPaused = newPaused, files = updated)
        }
    }

    fun cancelSession() {
        activeTransferJob?.cancel()
        try { activeSocket?.close() } catch (_: Exception) {}
        _currentSession.update { session ->
            if (session == null) return@update null
            val updated = session.files.map { if (!it.isFinished) it.copy(status = TransferStatus.CANCELLED) else it }
            session.copy(isCancelled = true, files = updated, currentSpeedBytesPerSec = 0L, remainingSeconds = 0L)
        }
    }

    fun toggleFilePause(fileId: String) {
        _currentSession.update { session ->
            if (session == null) return@update null
            val updated = session.files.map { item ->
                if (item.id == fileId) {
                    when (item.status) {
                        TransferStatus.TRANSFERRING -> item.copy(status = TransferStatus.PAUSED)
                        TransferStatus.PAUSED -> item.copy(status = TransferStatus.TRANSFERRING)
                        else -> item
                    }
                } else item
            }
            session.copy(files = updated)
        }
    }

    fun cancelFile(fileId: String) {
        _currentSession.update { session ->
            if (session == null) return@update null
            val updated = session.files.map { item ->
                if (item.id == fileId) item.copy(status = TransferStatus.CANCELLED) else item
            }
            val allFinished = updated.all { it.isFinished }
            val anyCompleted = updated.any { it.status == TransferStatus.COMPLETED }
            session.copy(
                files = updated,
                isCompleted = allFinished && anyCompleted,
                isCancelled = allFinished && !anyCompleted
            )
        }
    }

    fun clearSession() {
        val s = _currentSession.value
        if (s != null) {
            _transferHistory.update { listOf(s) + it }
        }
        _currentSession.value = null
    }

    fun addManualPeer(ip: String, port: Int = TCP_TRANSFER_PORT) {
        val trimmed = ip.trim()
        if (trimmed.isEmpty()) return
        val id = "manual_${trimmed.replace('.', '_')}"
        val peer = DiscoveredDevice(
            id = id,
            name = "Direct Peer",
            avatarRes = CartoonAvatars.list.first().drawableRes,
            ip = trimmed,
            port = port,
            supports5Ghz = true,
            is5GhzActive = true,
            lastSeenTimestamp = System.currentTimeMillis() + 120000L
        )
        discoveredDevicesMap[id] = peer
        _discoveredPeers.value = discoveredDevicesMap.values.toList()
    }

    fun isTransferActive(): Boolean {
        val s = _currentSession.value ?: return false
        return !s.isCompleted && !s.isCancelled
    }

    fun shutdownIdleBackgroundWork() {
        if (!isTransferActive()) {
            stopDiscovery()
            stopReceiver()
            try { activeSocket?.close() } catch (_: Exception) {}
            activeSocket = null
        }
    }

    fun shutdownAll() {
        stopDiscovery()
        stopReceiver()
        activeTransferJob?.cancel()
        activeTransferJob = null
        try { activeSocket?.close() } catch (_: Exception) {}
        activeSocket = null
    }

    private fun resolveAccurateFileSize(file: SharedFile): Long {
        val uriStr = file.uriString ?: return file.sizeBytes.coerceAtLeast(0L)
        try {
            val parsed = uriStr.toUri()
            when (parsed.scheme) {
                "content" -> {
                    context.contentResolver.openAssetFileDescriptor(parsed, "r")?.use { afd ->
                        if (afd.length >= 0L) return afd.length
                    }
                    context.contentResolver.openFileDescriptor(parsed, "r")?.use { pfd ->
                        if (pfd.statSize >= 0L) return pfd.statSize
                    }
                }
                "file" -> {
                    val f = File(parsed.path ?: "")
                    if (f.exists() && f.isFile) return f.length()
                }
                else -> {
                    val f = File(uriStr)
                    if (f.exists() && f.isFile) return f.length()
                }
            }
        } catch (_: Exception) {}
        return file.sizeBytes.coerceAtLeast(0L)
    }

    private fun openFileInputStream(file: SharedFile): InputStream? {
        val uriStr = file.uriString ?: return null
        try {
            val parsed = uriStr.toUri()
            val stream = when (parsed.scheme) {
                "content" -> context.contentResolver.openInputStream(parsed)
                "file" -> File(parsed.path ?: "").takeIf { it.exists() && it.canRead() }?.inputStream()
                else -> File(uriStr).takeIf { it.exists() && it.canRead() }?.inputStream()
            }
            if (stream != null) return stream
        } catch (_: Exception) {}

        // MediaStore lookup fallback when filesystem path is scoped-storage restricted on Android 11+
        try {
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
            val args = arrayOf(file.name, file.sizeBytes.toString())
            context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val resolvedUri = android.content.ContentUris.withAppendedId(collection, id)
                    return context.contentResolver.openInputStream(resolvedUri)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun sanitizeWireField(raw: String): String {
        return raw.replace("|", " ").replace("\n", " ").replace("\r", " ").trim()
    }

    private fun readAsciiLine(input: InputStream, maxLen: Int = 512): String {
        val sb = StringBuilder()
        var count = 0
        while (count < maxLen) {
            val b = input.read()
            if (b == -1) break
            val c = b.toChar()
            if (c == '\n') break
            if (c != '\r') {
                sb.append(c)
                count++
            }
        }
        return sb.toString()
    }
}
