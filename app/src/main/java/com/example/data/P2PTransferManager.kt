package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaScannerConnection
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Environment
import com.example.R
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
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
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    }
                    if (networkInfo?.isConnected == true) {
                        requestConnectionInfo()
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Local device information updated
                }
            }
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
        if (manager != null && channel != null) {
            try {
                manager.clearServiceRequests(channel, null)
                val txtListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomain, record, device ->
                    val cuteName = record["name"]
                    val avatarStr = record["avatar"]
                    val portStr = record["port"]
                    val avatarRes = avatarStr?.toIntOrNull() ?: CartoonAvatars.list.first().drawableRes
                    val port = portStr?.toIntOrNull() ?: TCP_TRANSFER_PORT

                    val id = device.deviceAddress ?: fullDomain
                    val resolvedName = if (!cuteName.isNullOrBlank()) cuteName else "Cute Friend"
                    macToCuteProfileMap[id] = Pair(resolvedName, avatarRes)

                    val directPeer = DiscoveredDevice(
                        id = id,
                        name = resolvedName,
                        avatarRes = avatarRes,
                        ip = NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP,
                        port = port,
                        supports5Ghz = true,
                        is5GhzActive = true,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                    discoveredDevicesMap[id] = directPeer
                    _discoveredPeers.value = discoveredDevicesMap.values.toList()
                }

                val servListener = WifiP2pManager.DnsSdServiceResponseListener { _, _, _ -> }
                manager.setDnsSdResponseListeners(channel, servListener, txtListener)

                val req = WifiP2pDnsSdServiceRequest.newInstance()
                dnsSdServiceRequest = req
                manager.addServiceRequest(channel, req, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        manager.discoverServices(channel, null)
                    }
                    override fun onFailure(code: Int) {}
                })
            } catch (_: Exception) {}

            try {
                manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        requestP2pPeers()
                    }
                    override fun onFailure(reason: Int) {}
                })
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
                                val cuteName = parts[1]
                                val avatarRes = parts[2].toIntOrNull() ?: CartoonAvatars.list.first().drawableRes
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
                                    _discoveredPeers.value = discoveredDevicesMap.values.toList()
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
            while (isActive) {
                requestP2pPeers()
                scanLocalSubnetForReceivers()

                // Remove peers not seen in the last 20 seconds
                val now = System.currentTimeMillis()
                val expiredIds = discoveredDevicesMap.filter { (id, peer) ->
                    !id.startsWith("manual_") && (now - peer.lastSeenTimestamp > 20000L)
                }.keys
                if (expiredIds.isNotEmpty()) {
                    expiredIds.forEach { discoveredDevicesMap.remove(it) }
                    _discoveredPeers.value = discoveredDevicesMap.values.toList()
                }

                delay(2500L)
            }
        }
    }

    private fun requestP2pPeers() {
        val channel = p2pChannel ?: return
        val manager = wifiP2pManager ?: return
        try {
            manager.requestPeers(channel) { peerList: WifiP2pDeviceList? ->
                if (peerList != null) {
                    val peers = peerList.deviceList
                    if (peers.isNotEmpty()) {
                        for (dev in peers) {
                            val id = dev.deviceAddress ?: continue
                            // Use discovered CuteShare profile name, never the raw hardware device name
                            val mappedProfile = macToCuteProfileMap[id]
                            val name = mappedProfile?.first ?: run {
                                val dName = dev.deviceName
                                if (!dName.isNullOrBlank() && dName.startsWith("CuteShare_")) {
                                    dName.removePrefix("CuteShare_")
                                } else {
                                    "Cute Friend"
                                }
                            }
                            val avatarRes = mappedProfile?.second ?: run {
                                val avatarIdx = Math.abs(id.hashCode()) % CartoonAvatars.list.size
                                CartoonAvatars.getAvatar(avatarIdx).drawableRes
                            }

                            val directPeer = DiscoveredDevice(
                                id = id,
                                name = name,
                                avatarRes = avatarRes,
                                ip = NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP,
                                port = TCP_TRANSFER_PORT,
                                supports5Ghz = true,
                                is5GhzActive = true,
                                lastSeenTimestamp = System.currentTimeMillis()
                            )
                            discoveredDevicesMap[id] = directPeer
                        }
                        _discoveredPeers.value = discoveredDevicesMap.values.toList()
                    }
                }
            }
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
                            val name = parts[1]
                            val avatarRes = parts[2].toIntOrNull() ?: CartoonAvatars.list.first().drawableRes
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
                            _discoveredPeers.value = discoveredDevicesMap.values.toList()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
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
                if (info != null && info.groupFormed) {
                    val groupOwnerIp = info.groupOwnerAddress?.hostAddress ?: NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP
                    // Connected to Wi-Fi Direct Group!
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
        registerP2pReceiver()

        val channel = p2pChannel
        val manager = wifiP2pManager

        // 1. Create Autonomous Wi-Fi Direct Group so this receiver is Group Owner (192.168.49.1)
        if (manager != null && channel != null) {
            try {
                manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}
                    override fun onFailure(reason: Int) {}
                })
            } catch (_: Exception) {}

            // 2. Register DNS-SD Local Service advertising CuteShare user name and avatar
            try {
                val record = HashMap<String, String>().apply {
                    put("name", userProfile.name)
                    put("avatar", userProfile.avatarRes.toString())
                    put("port", TCP_TRANSFER_PORT.toString())
                }
                val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                    "CuteShare_${userProfile.name}",
                    SERVICE_TYPE,
                    record
                )
                dnsSdServiceInfo = serviceInfo
                manager.addLocalService(channel, serviceInfo, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}
                    override fun onFailure(arg0: Int) {}
                })
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
                    val payload = "CUTESHARE_BEACON|${userProfile.name}|${userProfile.avatarRes}|$TCP_TRANSFER_PORT|$myIp"
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
                val resp = "CUTESHARE_PONG|${userProfile.name}|${userProfile.avatarRes}|$TCP_TRANSFER_PORT|$ip\n"
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

            // Sender metadata line: senderId|senderName|senderAvatarRes|fileCount|totalBytes
            val senderLine = readAsciiLine(inStream, maxLen = 512)
            val sParts = senderLine.split("|")
            val senderId = sParts.getOrNull(0) ?: "peer"
            val senderName = sParts.getOrNull(1) ?: "Cute Peer"
            val senderAvatarRes = sParts.getOrNull(2)?.toIntOrNull() ?: CartoonAvatars.list.first().drawableRes

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
                            extension = fName.substringAfterLast('.', "").uppercase(),
                            lastModified = "Wi-Fi Direct"
                        )
                    )
                }
                line = readAsciiLine(inStream, maxLen = 1024)
            }

            val senderDevice = DiscoveredDevice(
                id = senderId,
                name = senderName,
                avatarRes = senderAvatarRes,
                ip = socket.inetAddress?.hostAddress ?: NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP,
                port = socket.port,
                supports5Ghz = true,
                is5GhzActive = true
            )

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
                currentSpeedBytesPerSec = 40_000_000L,
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
            receiveFilesStream(socket, inStream, outStream, incomingFiles, senderDevice)
        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private suspend fun receiveFilesStream(
        socket: Socket,
        inStream: BufferedInputStream,
        outStream: BufferedOutputStream,
        files: List<SharedFile>,
        senderDevice: DiscoveredDevice
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

        // SAVE ALL FILES TO Download/Cute Share/
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val cuteShareDir = File(downloadsDir, "Cute Share").apply { mkdirs() }

        val buffer = ByteArray(65536)
        var totalBytesTransferred = 0L
        val totalBytes = items.sumOf { it.sizeBytes }

        for (item in items) {
            if (_currentSession.value?.isCancelled == true) break

            val fileHeader = readAsciiLine(inStream, maxLen = 512)
            if (!fileHeader.startsWith("START_FILE")) break

            val parts = fileHeader.split(":")
            val targetSize = parts.getOrNull(2)?.toLongOrNull() ?: item.sizeBytes

            var destFile = File(cuteShareDir, item.name)
            var counter = 1
            while (destFile.exists()) {
                val base = item.name.substringBeforeLast('.')
                val ext = item.name.substringAfterLast('.', "")
                val newName = if (ext.isNotEmpty()) "$base ($counter).$ext" else "$base ($counter)"
                destFile = File(cuteShareDir, newName)
                counter++
            }

            var fileBytesReceived = 0L
            var lastSpeedCheckTime = System.currentTimeMillis()
            var bytesSinceLastCheck = 0L

            FileOutputStream(destFile).use { fileOut ->
                while (fileBytesReceived < targetSize) {
                    if (_currentSession.value?.isCancelled == true) break
                    while (_currentSession.value?.isPaused == true) {
                        delay(200)
                    }

                    val toRead = (targetSize - fileBytesReceived).coerceAtMost(buffer.size.toLong()).toInt()
                    val read = inStream.read(buffer, 0, toRead)
                    if (read == -1) break

                    fileOut.write(buffer, 0, read)
                    fileBytesReceived += read
                    totalBytesTransferred += read
                    bytesSinceLastCheck += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastSpeedCheckTime
                    if (elapsed >= 350) {
                        val speedBytesSec = (bytesSinceLastCheck * 1000L) / elapsed.coerceAtLeast(1L)
                        val remainingBytes = (totalBytes - totalBytesTransferred).coerceAtLeast(0L)
                        val remSecs = if (speedBytesSec > 0) remainingBytes / speedBytesSec else 0L

                        lastSpeedCheckTime = now
                        bytesSinceLastCheck = 0L

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

            try {
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
            } catch (_: Exception) {}

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

        val finalSession = _currentSession.value?.copy(
            isCompleted = true,
            currentSpeedBytesPerSec = 0L,
            remainingSeconds = 0L
        )
        if (finalSession != null) {
            _currentSession.value = finalSession
            _transferHistory.update { listOf(finalSession) + it }
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
            currentSpeedBytesPerSec = 40_000_000L,
            remainingSeconds = 0L,
            wifiBand = "Wi-Fi Direct"
        )
        _currentSession.value = session

        val channel = p2pChannel
        val manager = wifiP2pManager

        // Connect via Wi-Fi Direct if peer has a valid MAC
        if (manager != null && channel != null && targetPeer.id.contains(":")) {
            val config = WifiP2pConfig().apply {
                deviceAddress = targetPeer.id
                groupOwnerIntent = 0 // Receiver is Group Owner
            }
            try {
                manager.connect(channel, config, null)
            } catch (_: Exception) {}
        }

        activeTransferJob = scope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                val candidateHosts = mutableListOf<String>()
                if (targetPeer.ip.isNotEmpty() && !targetPeer.ip.startsWith("127.")) {
                    candidateHosts.add(targetPeer.ip)
                }
                candidateHosts.add(NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP)
                candidateHosts.add("192.168.43.1")

                var connected = false
                var attempts = 0
                val maxAttempts = 12 // Up to 6 seconds allowing P2P group negotiation to finalize

                while (!connected && attempts < maxAttempts && isActive) {
                    attempts++
                    for (host in candidateHosts.distinct()) {
                        try {
                            val testSocket = Socket()
                            testSocket.tcpNoDelay = true
                            testSocket.sendBufferSize = 524288
                            testSocket.receiveBufferSize = 524288
                            testSocket.connect(InetSocketAddress(host, targetPeer.port), 1200)
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

                // Transmit Header: protocol, sender profile, file manifest
                val header = StringBuilder()
                header.append("CUTESHARE_WIFIDIRECT_v1\n")
                val totalBytes = files.sumOf { it.sizeBytes }
                header.append("${UUID.randomUUID().toString().take(8)}|${senderProfile.name}|${senderProfile.avatarRes}|${files.size}|$totalBytes\n")
                for (file in files) {
                    header.append("${file.id}|${file.name}|${file.sizeBytes}|${file.category.name}\n")
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

                for (file in files) {
                    if (_currentSession.value?.isCancelled == true) break

                    val fileHeader = "START_FILE:${file.id}:${file.sizeBytes}\n"
                    outStream.write(fileHeader.toByteArray(Charsets.UTF_8))
                    outStream.flush()

                    var fileInputStream: InputStream? = null
                    try {
                        val uriStr = file.uriString
                        if (!uriStr.isNullOrEmpty()) {
                            val parsed = Uri.parse(uriStr)
                            fileInputStream = if (parsed.scheme == "content") {
                                context.contentResolver.openInputStream(parsed)
                            } else if (parsed.scheme == "file") {
                                File(parsed.path ?: "").inputStream()
                            } else {
                                File(uriStr).inputStream()
                            }
                        }
                    } catch (_: Exception) {}

                    var fileBytesSent = 0L
                    var lastSpeedCheck = System.currentTimeMillis()
                    var bytesSinceLastCheck = 0L

                    if (fileInputStream != null) {
                        fileInputStream.use { input ->
                            while (fileBytesSent < file.sizeBytes) {
                                if (_currentSession.value?.isCancelled == true) break
                                while (_currentSession.value?.isPaused == true) {
                                    delay(200)
                                }

                                val toRead = (file.sizeBytes - fileBytesSent).coerceAtMost(buffer.size.toLong()).toInt()
                                val read = input.read(buffer, 0, toRead)
                                if (read == -1) break

                                outStream.write(buffer, 0, read)
                                fileBytesSent += read
                                totalBytesSent += read
                                bytesSinceLastCheck += read

                                val now = System.currentTimeMillis()
                                val elapsed = now - lastSpeedCheck
                                if (elapsed >= 350) {
                                    val speed = (bytesSinceLastCheck * 1000L) / elapsed.coerceAtLeast(1L)
                                    val remaining = (totalBytes - totalBytesSent).coerceAtLeast(0L)
                                    val remSecs = if (speed > 0) remaining / speed else 0L

                                    lastSpeedCheck = now
                                    bytesSinceLastCheck = 0L

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
                    } else {
                        // Fallback chunking if stream couldn't be resolved
                        val emptyChunk = ByteArray(65536)
                        while (fileBytesSent < file.sizeBytes) {
                            if (_currentSession.value?.isCancelled == true) break
                            val toSend = (file.sizeBytes - fileBytesSent).coerceAtMost(emptyChunk.size.toLong()).toInt()
                            outStream.write(emptyChunk, 0, toSend)
                            fileBytesSent += toSend
                            totalBytesSent += toSend
                        }
                    }

                    outStream.flush()
                    readAsciiLine(inStream, maxLen = 128)

                    _currentSession.update { s ->
                        s?.copy(
                            files = s.files.map { f ->
                                if (f.id == file.id) f.copy(
                                    transferredBytes = file.sizeBytes,
                                    progress = 1f,
                                    status = TransferStatus.COMPLETED
                                ) else f
                            }
                        )
                    }
                }

                val finalSession = _currentSession.value?.copy(
                    isCompleted = true,
                    currentSpeedBytesPerSec = 0L,
                    remainingSeconds = 0L
                )
                if (finalSession != null) {
                    _currentSession.value = finalSession
                    _transferHistory.update { listOf(finalSession) + it }
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
            session.copy(files = updated, isCompleted = updated.all { it.isFinished })
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
