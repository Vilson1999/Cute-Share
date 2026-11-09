package com.example.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Environment
import com.example.R
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
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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

    // Discovered devices in real-time (devices that have Cute Share open nearby)
    private val _discoveredPeers = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredDevice>> = _discoveredPeers.asStateFlow()

    // Incoming transfer request prompt for receiver
    data class IncomingTransferRequest(
        val sender: DiscoveredDevice,
        val files: List<SharedFile>,
        val totalSize: Long,
        val acceptAction: () -> Unit,
        val declineAction: () -> Unit
    )
    private val _incomingRequest = MutableStateFlow<IncomingTransferRequest?>(null)
    val incomingRequest: StateFlow<IncomingTransferRequest?> = _incomingRequest.asStateFlow()

    private val devicePeerMap = ConcurrentHashMap<String, DiscoveredDevice>()

    // Background jobs
    private var discoveryJob: Job? = null
    private var subnetScanJob: Job? = null
    private var receiverBeaconJob: Job? = null
    private var receiverServerJob: Job? = null
    private var receiverDiscoveryListenerJob: Job? = null
    private var activeTransferJob: Job? = null

    // Sockets
    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var discoverySocket: DatagramSocket? = null
    private var beaconSocket: DatagramSocket? = null
    private var receiverUdpSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    init {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("CuteShareMulticastLock")?.apply {
                setReferenceCounted(false)
            }
        } catch (_: Exception) {}
    }

    /**
     * Inspects device Wi-Fi hardware and connection to detect 5 GHz capability and frequency.
     * Returns Pair(supports5Ghz, is5GhzActive)
     */
    fun getLocalWifiBandInfo(): Pair<Boolean, Boolean> {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return Pair(true, false)
            val supports5Ghz = wifi.is5GHzBandSupported
            val info = wifi.connectionInfo
            val freq = info?.frequency ?: 0
            val is5GhzActive = freq >= 4900
            return Pair(supports5Ghz, is5GhzActive)
        } catch (_: Exception) {
            return Pair(true, false)
        }
    }

    private fun acquireMulticast() {
        try {
            if (multicastLock?.isHeld != true) {
                multicastLock?.acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseMulticast() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }

    // =========================================================================
    // DISCOVERY (SENDER SEARCH MODE)
    // =========================================================================

    /**
     * Starts searching for nearby devices running Cute Share.
     * Detects 5 GHz and 2.4 GHz capabilities across devices.
     */
    fun startDiscovery(senderProfile: UserProfile) {
        stopDiscovery()
        devicePeerMap.clear()
        _discoveredPeers.value = emptyList()

        acquireMulticast()

        val myIp = NetworkUtils.getLocalIpAddress()
        val myDeviceId = UUID.randomUUID().toString().take(8)
        val (my5Ghz, my5GhzActive) = getLocalWifiBandInfo()

        // 1. UDP Broadcast Listener & Prober
        discoveryJob = scope.launch(Dispatchers.IO) {
            try {
                discoverySocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 1200
                    bind(InetSocketAddress(NetworkUtils.DISCOVERY_PORT))
                }
            } catch (e: Exception) {
                try {
                    discoverySocket = DatagramSocket().apply {
                        broadcast = true
                        soTimeout = 1200
                    }
                } catch (_: Exception) {}
            }

            val socket = discoverySocket ?: return@launch
            val buffer = ByteArray(4096)
            val packet = DatagramPacket(buffer, buffer.size)

            // Periodic probe broadcast
            val probeJob = launch {
                while (isActive) {
                    try {
                        val probeData = "CUTESHARE_PROBE:v2:$myDeviceId:${senderProfile.name}:$myIp:$my5Ghz:$my5GhzActive".toByteArray(Charsets.UTF_8)
                        for (broadcastAddr in NetworkUtils.getBroadcastAddresses()) {
                            try {
                                val probePacket = DatagramPacket(
                                    probeData,
                                    probeData.size,
                                    broadcastAddr,
                                    NetworkUtils.DISCOVERY_PORT
                                )
                                socket.send(probePacket)
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}

                    // Remove peers not seen in the last 25 seconds
                    val now = System.currentTimeMillis()
                    val toRemove = mutableListOf<String>()
                    for ((id, peer) in devicePeerMap) {
                        if (now - peer.lastSeenTimestamp > 25000L) {
                            toRemove.add(id)
                        }
                    }
                    if (toRemove.isNotEmpty()) {
                        toRemove.forEach { devicePeerMap.remove(it) }
                        _discoveredPeers.value = devicePeerMap.values.toList()
                    }

                    delay(1000L)
                }
            }

            // Packet receiving loop
            while (isActive && !socket.isClosed) {
                try {
                    // MUST reset packet length before each receive to prevent packet truncation!
                    packet.length = buffer.size
                    socket.receive(packet)

                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    val senderIp = packet.address?.hostAddress ?: ""

                    // Ignore packets from self
                    if (senderIp == myIp && message.contains(myDeviceId)) {
                        continue
                    }

                    if (message.startsWith("CUTESHARE_BEACON:")) {
                        // Format: CUTESHARE_BEACON:v2:<deviceId>:<name>:<avatarRes>:<port>:<ip>:<supports5Ghz>:<is5GhzActive>
                        val parts = message.split(":")
                        if (parts.size >= 7) {
                            val id = parts[2]
                            if (id == myDeviceId) continue

                            val name = parts[3]
                            val avatarRes = parts[4].toIntOrNull() ?: R.drawable.avatar_1_bunny
                            val port = parts[5].toIntOrNull() ?: NetworkUtils.DEFAULT_TRANSFER_PORT
                            val advertisedIp = parts[6].trim()
                            val realIp = if (advertisedIp.isEmpty() || advertisedIp.startsWith("127.")) senderIp else advertisedIp
                            val supports5Ghz = parts.getOrNull(7)?.toBooleanStrictOrNull() ?: true
                            val is5GhzActive = parts.getOrNull(8)?.toBooleanStrictOrNull() ?: false

                            if (realIp.isNotEmpty() && !realIp.startsWith("127.")) {
                                val peer = DiscoveredDevice(
                                    id = id,
                                    name = name,
                                    avatarRes = avatarRes,
                                    ip = realIp,
                                    port = port,
                                    supports5Ghz = supports5Ghz,
                                    is5GhzActive = is5GhzActive,
                                    lastSeenTimestamp = System.currentTimeMillis()
                                )
                                devicePeerMap[id] = peer
                                _discoveredPeers.value = devicePeerMap.values.toList()
                            }
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Normal read timeout, loop around
                } catch (_: Exception) {
                    if (!isActive) break
                }
            }

            probeJob.cancel()
        }

        // 2. Active Subnet TCP Scanning
        subnetScanJob = scope.launch(Dispatchers.IO) {
            val prefix = NetworkUtils.getSubnetPrefix(myIp)
            val myHostNum = myIp.substringAfterLast('.', "0").toIntOrNull() ?: 0

            while (isActive) {
                if (prefix != null) {
                    val priorityList = mutableListOf<String>()
                    // Common gateways
                    priorityList.add("192.168.43.1")
                    priorityList.add("192.168.49.1")
                    priorityList.add("${prefix}1")
                    // Immediate neighbor IPs
                    for (offset in 1..8) {
                        val up = myHostNum + offset
                        val down = myHostNum - offset
                        if (up in 2..254) priorityList.add("$prefix$up")
                        if (down in 2..254) priorityList.add("$prefix$down")
                    }
                    // Remaining subnet hosts
                    for (i in 2..254) {
                        val ip = "$prefix$i"
                        if (i != myHostNum && !priorityList.contains(ip)) {
                            priorityList.add(ip)
                        }
                    }

                    val batchSize = 25
                    for (chunk in priorityList.chunked(batchSize)) {
                        if (!isActive) break
                        val jobs = chunk.map { targetIp ->
                            launch {
                                probeTcpPeer(targetIp, NetworkUtils.DEFAULT_TRANSFER_PORT, myDeviceId)
                            }
                        }
                        jobs.forEach { it.join() }
                    }
                }
                delay(3000L)
            }
        }
    }

    private suspend fun probeTcpPeer(ip: String, port: Int, myDeviceId: String) = withContext(Dispatchers.IO) {
        var s: Socket? = null
        try {
            s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(ip, port), 350)
            val out = BufferedOutputStream(s.getOutputStream())
            val inStream = BufferedInputStream(s.getInputStream())

            val (my5Ghz, my5GhzActive) = getLocalWifiBandInfo()
            val pingMsg = "CUTESHARE_PING:$myDeviceId:$my5Ghz:$my5GhzActive\n".toByteArray(Charsets.UTF_8)
            out.write(pingMsg)
            out.flush()

            val line = readAsciiLine(inStream, maxLen = 256)
            if (line.startsWith("CUTESHARE_PONG|")) {
                // Format: CUTESHARE_PONG|deviceId|name|avatarRes|port|ip|supports5Ghz|is5GhzActive
                val parts = line.split("|")
                if (parts.size >= 6) {
                    val id = parts[1]
                    if (id != myDeviceId) {
                        val name = parts[2]
                        val avatarRes = parts[3].toIntOrNull() ?: R.drawable.avatar_1_bunny
                        val peerPort = parts[4].toIntOrNull() ?: port
                        val advertisedIp = parts[5].trim()
                        val realIp = if (advertisedIp.isEmpty() || advertisedIp.startsWith("127.")) ip else advertisedIp
                        val supports5Ghz = parts.getOrNull(6)?.toBooleanStrictOrNull() ?: true
                        val is5GhzActive = parts.getOrNull(7)?.toBooleanStrictOrNull() ?: false

                        val peer = DiscoveredDevice(
                            id = id,
                            name = name,
                            avatarRes = avatarRes,
                            ip = realIp,
                            port = peerPort,
                            supports5Ghz = supports5Ghz,
                            is5GhzActive = is5GhzActive,
                            lastSeenTimestamp = System.currentTimeMillis()
                        )
                        devicePeerMap[id] = peer
                        _discoveredPeers.value = devicePeerMap.values.toList()
                    }
                }
            }
        } catch (_: Exception) {
            // Unreachable or not a CuteShare receiver
        } finally {
            try { s?.close() } catch (_: Exception) {}
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        subnetScanJob?.cancel()
        subnetScanJob = null

        try { discoverySocket?.close() } catch (_: Exception) {}
        discoverySocket = null
        devicePeerMap.clear()
        _discoveredPeers.value = emptyList()

        releaseMulticast()
    }

    fun addManualPeer(ip: String, port: Int = NetworkUtils.DEFAULT_TRANSFER_PORT) {
        val trimmedIp = ip.trim()
        if (trimmedIp.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            probeTcpPeer(trimmedIp, port, "manual")

            if (!devicePeerMap.containsKey("manual_${trimmedIp.replace('.', '_')}")) {
                val id = "manual_${trimmedIp.replace('.', '_')}"
                val peer = DiscoveredDevice(
                    id = id,
                    name = "Nearby Device",
                    avatarRes = R.drawable.avatar_1_bunny,
                    ip = trimmedIp,
                    port = port,
                    supports5Ghz = true,
                    is5GhzActive = false,
                    lastSeenTimestamp = System.currentTimeMillis() + 120000L
                )
                devicePeerMap[id] = peer
                _discoveredPeers.value = devicePeerMap.values.toList()
            }
        }
    }

    // =========================================================================
    // RECEIVER (RADAR MODE)
    // =========================================================================

    /**
     * Starts Receiver mode:
     * 1) Binds local TCP ServerSocket (prioritizing 42888) to answer pings and accept file transfers
     * 2) Broadcasts UDP beacons with 5 GHz / 2.4 GHz capability info every 800ms
     * 3) Answers UDP probes immediately with beacon
     */
    fun startReceiver(
        userProfile: UserProfile,
        onSessionStarted: () -> Unit
    ) {
        stopReceiver()
        acquireMulticast()

        val deviceId = UUID.randomUUID().toString().take(8)
        val (my5Ghz, my5GhzActive) = getLocalWifiBandInfo()

        try {
            serverSocket = ServerSocket(NetworkUtils.DEFAULT_TRANSFER_PORT).apply {
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
        val localPort = server.localPort

        // 2. Broadcast UDP Beacon with 5 GHz band info
        receiverBeaconJob = scope.launch(Dispatchers.IO) {
            try {
                beaconSocket = DatagramSocket().apply {
                    broadcast = true
                }
            } catch (_: Exception) {}

            val bSocket = beaconSocket ?: return@launch
            while (isActive && !bSocket.isClosed) {
                val currentIp = NetworkUtils.getLocalIpAddress()
                val beaconMsg = "CUTESHARE_BEACON:v2:$deviceId:${userProfile.name}:${userProfile.avatarRes}:$localPort:$currentIp:$my5Ghz:$my5GhzActive"
                val beaconData = beaconMsg.toByteArray(Charsets.UTF_8)

                for (broadcastAddr in NetworkUtils.getBroadcastAddresses()) {
                    try {
                        val packet = DatagramPacket(
                            beaconData,
                            beaconData.size,
                            broadcastAddr,
                            NetworkUtils.DISCOVERY_PORT
                        )
                        bSocket.send(packet)
                    } catch (_: Exception) {}
                }
                delay(800L)
            }
        }

        // 3. Listen for UDP Probes to respond immediately
        receiverDiscoveryListenerJob = scope.launch(Dispatchers.IO) {
            try {
                receiverUdpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 1500
                    bind(InetSocketAddress(NetworkUtils.DISCOVERY_PORT))
                }
            } catch (_: Exception) {}

            val uSocket = receiverUdpSocket ?: return@launch
            val buf = ByteArray(2048)
            val p = DatagramPacket(buf, buf.size)

            while (isActive && !uSocket.isClosed) {
                try {
                    p.length = buf.size
                    uSocket.receive(p)
                    val msg = String(p.data, 0, p.length, Charsets.UTF_8).trim()
                    if (msg.startsWith("CUTESHARE_PROBE:") && !msg.contains(deviceId)) {
                        val currentIp = NetworkUtils.getLocalIpAddress()
                        val respMsg = "CUTESHARE_BEACON:v2:$deviceId:${userProfile.name}:${userProfile.avatarRes}:$localPort:$currentIp:$my5Ghz:$my5GhzActive"
                        val respData = respMsg.toByteArray(Charsets.UTF_8)
                        val replyPacket = DatagramPacket(respData, respData.size, p.address, NetworkUtils.DISCOVERY_PORT)
                        uSocket.send(replyPacket)
                    }
                } catch (_: java.net.SocketTimeoutException) {
                } catch (_: Exception) {
                    if (!isActive) break
                }
            }
        }

        // 4. Accept TCP Connections (handles ping probe and transfer stream)
        receiverServerJob = scope.launch(Dispatchers.IO) {
            while (isActive && !server.isClosed) {
                try {
                    val socket = server.accept()
                    socket.tcpNoDelay = true
                    socket.receiveBufferSize = 524288
                    socket.sendBufferSize = 524288
                    launch {
                        handleIncomingTcpConnection(socket, deviceId, userProfile, localPort, my5Ghz, my5GhzActive, onSessionStarted)
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                }
            }
        }
    }

    private suspend fun handleIncomingTcpConnection(
        socket: Socket,
        deviceId: String,
        userProfile: UserProfile,
        localPort: Int,
        my5Ghz: Boolean,
        my5GhzActive: Boolean,
        onSessionStarted: () -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 15000
            val inStream = BufferedInputStream(socket.getInputStream(), 65536)
            val outStream = BufferedOutputStream(socket.getOutputStream(), 65536)

            val firstLine = readAsciiLine(inStream, maxLen = 256)

            // A) Ping probe from sender
            if (firstLine.startsWith("CUTESHARE_PING")) {
                val ip = NetworkUtils.getLocalIpAddress()
                val resp = "CUTESHARE_PONG|$deviceId|${userProfile.name}|${userProfile.avatarRes}|$localPort|$ip|$my5Ghz|$my5GhzActive\n"
                outStream.write(resp.toByteArray(Charsets.UTF_8))
                outStream.flush()
                socket.close()
                return@withContext
            }

            // B) Transfer handshake
            if (!firstLine.startsWith("CUTESHARE_TRANSFER_v1")) {
                socket.close()
                return@withContext
            }

            activeSocket = socket

            // Sender meta line: senderId|senderName|senderAvatarRes|fileCount|totalBytes|negotiatedBand
            val senderLine = readAsciiLine(inStream, maxLen = 512)
            val sParts = senderLine.split("|")
            val senderId = sParts.getOrNull(0) ?: "peer"
            val senderName = sParts.getOrNull(1) ?: "Cute Peer"
            val senderAvatarRes = sParts.getOrNull(2)?.toIntOrNull() ?: R.drawable.avatar_1_bunny
            val totalBytes = sParts.getOrNull(4)?.toLongOrNull() ?: 0L
            val negotiatedBand = sParts.getOrNull(5) ?: (if (my5Ghz) "5 GHz" else "2.4 GHz")

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
                            lastModified = "Incoming"
                        )
                    )
                }
                line = readAsciiLine(inStream, maxLen = 1024)
            }

            val senderDevice = DiscoveredDevice(
                id = senderId,
                name = senderName,
                avatarRes = senderAvatarRes,
                ip = socket.inetAddress?.hostAddress ?: "",
                port = socket.port,
                supports5Ghz = negotiatedBand == "5 GHz"
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

            val initialSpeed = if (negotiatedBand == "5 GHz") 58_000_000L else 24_000_000L

            val session = TransferSession(
                id = UUID.randomUUID().toString(),
                isOutgoing = false,
                peerName = senderName,
                avatarRes = senderAvatarRes,
                files = items,
                isPaused = false,
                isCancelled = false,
                isCompleted = false,
                currentSpeedBytesPerSec = initialSpeed,
                remainingSeconds = 0L,
                wifiBand = negotiatedBand
            )
            _currentSession.value = session
            _incomingRequest.value = null

            // Auto-accept incoming transfer since receiver is explicitly in Receive Radar mode waiting for files
            outStream.write("ACCEPT\n".toByteArray(Charsets.UTF_8))
            outStream.flush()

            withContext(Dispatchers.Main) {
                onSessionStarted()
            }

            receiveFilesStream(socket, inStream, outStream, incomingFiles, senderDevice, negotiatedBand)
        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private suspend fun receiveFilesStream(
        socket: Socket,
        inStream: BufferedInputStream,
        outStream: BufferedOutputStream,
        files: List<SharedFile>,
        senderDevice: DiscoveredDevice,
        negotiatedBand: String
    ) = withContext(Dispatchers.IO) {
        socket.soTimeout = 0 // Remove timeout during active byte streaming

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

        val initialSpeed = if (negotiatedBand == "5 GHz") 58_000_000L else 24_000_000L

        val session = _currentSession.value ?: TransferSession(
            id = UUID.randomUUID().toString(),
            isOutgoing = false,
            peerName = senderDevice.name,
            avatarRes = senderDevice.avatarRes,
            files = items,
            isPaused = false,
            isCancelled = false,
            isCompleted = false,
            currentSpeedBytesPerSec = initialSpeed,
            remainingSeconds = 0L,
            wifiBand = negotiatedBand
        )
        _currentSession.value = session

        // Target directory: Downloads/CuteShare
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val cuteShareDir = File(downloadsDir, "CuteShare").apply { mkdirs() }

        val buffer = ByteArray(65536)
        var totalBytesTransferred = 0L
        val totalBytes = items.sumOf { it.sizeBytes }

        for (item in items) {
            if (_currentSession.value?.isCancelled == true) break

            // Wait for START_FILE header line
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
            }

            // Register file with Android media indexer
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

            // Send ACK to sender
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
        receiverBeaconJob?.cancel()
        receiverBeaconJob = null
        receiverDiscoveryListenerJob?.cancel()
        receiverDiscoveryListenerJob = null
        receiverServerJob?.cancel()
        receiverServerJob = null

        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        try { beaconSocket?.close() } catch (_: Exception) {}
        beaconSocket = null
        try { receiverUdpSocket?.close() } catch (_: Exception) {}
        receiverUdpSocket = null

        releaseMulticast()
    }

    // =========================================================================
    // SENDER FILE TRANSFER (TRANSMIT OVER TCP)
    // =========================================================================

    /**
     * Starts outgoing transfer:
     * Checks if both devices have 5 GHz Wi-Fi capability:
     * If both support 5 GHz -> connects on 5 GHz band profile.
     * If either only supports 2.4 GHz -> connects on 2.4 GHz band profile.
     */
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

        // 5GHz vs 2.4GHz Wi-Fi Band Check: Both devices must support 5GHz for 5GHz profile
        val (sender5Ghz, _) = getLocalWifiBandInfo()
        val effectiveBand = if (sender5Ghz && targetPeer.supports5Ghz) "5 GHz" else "2.4 GHz"
        val initialSpeed = if (effectiveBand == "5 GHz") 58_000_000L else 24_000_000L

        val session = TransferSession(
            id = UUID.randomUUID().toString(),
            isOutgoing = true,
            peerName = targetPeer.name,
            avatarRes = targetPeer.avatarRes,
            files = items,
            isPaused = false,
            isCancelled = false,
            isCompleted = false,
            currentSpeedBytesPerSec = initialSpeed,
            remainingSeconds = 0L,
            wifiBand = effectiveBand
        )
        _currentSession.value = session

        activeTransferJob = scope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.tcpNoDelay = true
                socket.sendBufferSize = 524288
                socket.receiveBufferSize = 524288
                socket.connect(InetSocketAddress(targetPeer.ip, targetPeer.port), 6000)
                activeSocket = socket

                val outStream = BufferedOutputStream(socket.getOutputStream(), 65536)
                val inStream = BufferedInputStream(socket.getInputStream(), 65536)

                // 1. Send Header with negotiated Wi-Fi band
                val header = StringBuilder()
                header.append("CUTESHARE_TRANSFER_v1\n")
                val totalBytes = files.sumOf { it.sizeBytes }
                header.append("${UUID.randomUUID().toString().take(8)}|${senderProfile.name}|${senderProfile.avatarRes}|${files.size}|$totalBytes|$effectiveBand\n")
                for (file in files) {
                    header.append("${file.id}|${file.name}|${file.sizeBytes}|${file.category.name}\n")
                }
                header.append("END_HEADER\n")

                outStream.write(header.toString().toByteArray(Charsets.UTF_8))
                outStream.flush()

                // 2. Await Response
                val response = readAsciiLine(inStream, maxLen = 64)
                if (response != "ACCEPT") {
                    _currentSession.update { it?.copy(isCancelled = true) }
                    return@launch
                }

                // 3. Stream File Bytes
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

                    // Wait for receiver ACK
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

            } catch (e: Exception) {
                _currentSession.update { it?.copy(isCancelled = true) }
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
            val updated = session.files.map {
                if (it.id == fileId) it.copy(status = TransferStatus.CANCELLED) else it
            }
            val allDone = updated.all { it.isFinished }
            session.copy(files = updated, isCompleted = allDone)
        }
    }

    fun clearSession() {
        _currentSession.value = null
    }

    private fun readAsciiLine(input: InputStream, maxLen: Int = 1024): String {
        val sb = StringBuilder()
        var ch = input.read()
        while (ch != -1 && ch != '\n'.code && sb.length < maxLen) {
            if (ch != '\r'.code) {
                sb.append(ch.toChar())
            }
            ch = input.read()
        }
        return sb.toString()
    }
}
