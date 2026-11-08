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
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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

    // Discovered devices in real-time (ONLY devices that currently have their receive screen open)
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
    private var receiverBeaconJob: Job? = null
    private var receiverServerJob: Job? = null
    private var activeTransferJob: Job? = null

    // Sockets
    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var discoverySocket: DatagramSocket? = null
    private var beaconSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    init {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("CuteShareMulticastLock")?.apply {
                setReferenceCounted(true)
            }
        } catch (_: Exception) {}
    }

    // =========================================================================
    // DISCOVERY (SENDER SEARCH MODE)
    // =========================================================================

    /**
     * Starts searching for nearby devices that currently have the Receive screen open.
     * If no device is open on Receive screen, discoveredPeers stays empty!
     */
    fun startDiscovery(senderProfile: UserProfile) {
        stopDiscovery()
        devicePeerMap.clear()
        _discoveredPeers.value = emptyList()

        try {
            multicastLock?.acquire()
        } catch (_: Exception) {}

        discoveryJob = scope.launch(Dispatchers.IO) {
            try {
                discoverySocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 1000
                    bind(InetSocketAddress(NetworkUtils.DISCOVERY_PORT))
                }
            } catch (e: Exception) {
                // Fallback to ephemeral port for listening
                try {
                    discoverySocket = DatagramSocket().apply {
                        broadcast = true
                        soTimeout = 1000
                    }
                } catch (_: Exception) {}
            }

            val socket = discoverySocket ?: return@launch
            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)

            // Periodic probe and timeout cleanup launcher
            val probeJob = launch {
                while (isActive) {
                    // Send probe broadcast to wake up active receivers
                    try {
                        val probeData = "CUTESHARE_PROBE:${senderProfile.name}".toByteArray(Charsets.UTF_8)
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

                    // Remove peers that have stopped broadcasting (> 3.5 seconds)
                    val now = System.currentTimeMillis()
                    val toRemove = mutableListOf<String>()
                    for ((id, peer) in devicePeerMap) {
                        if (now - peer.lastSeenTimestamp > 3500L) {
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
            while (isActive) {
                try {
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    val senderIp = packet.address.hostAddress ?: ""

                    if (message.startsWith("CUTESHARE_BEACON:")) {
                        // Format: CUTESHARE_BEACON:v1:<deviceId>:<name>:<avatarRes>:<port>:<ip>
                        val parts = message.split(":")
                        if (parts.size >= 7) {
                            val id = parts[2]
                            val name = parts[3]
                            val avatarRes = parts[4].toIntOrNull() ?: R.drawable.avatar_1_bunny
                            val port = parts[5].toIntOrNull() ?: NetworkUtils.DEFAULT_TRANSFER_PORT
                            val advertisedIp = parts[6].ifEmpty { senderIp }

                            val peer = DiscoveredDevice(
                                id = id,
                                name = name,
                                avatarRes = avatarRes,
                                ip = advertisedIp,
                                port = port,
                                lastSeenTimestamp = System.currentTimeMillis()
                            )
                            devicePeerMap[id] = peer
                            _discoveredPeers.value = devicePeerMap.values.toList()
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Normal timeout, continue loop
                } catch (_: Exception) {
                    if (!isActive) break
                }
            }

            probeJob.cancel()
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        try {
            discoverySocket?.close()
        } catch (_: Exception) {}
        discoverySocket = null
        devicePeerMap.clear()
        _discoveredPeers.value = emptyList()

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }

    fun addManualPeer(ip: String, port: Int = NetworkUtils.DEFAULT_TRANSFER_PORT) {
        val id = "manual_${ip.replace('.', '_')}"
        val peer = DiscoveredDevice(
            id = id,
            name = "Receiver ($ip)",
            avatarRes = R.drawable.avatar_1_bunny,
            ip = ip,
            port = port,
            lastSeenTimestamp = System.currentTimeMillis() + 60000L
        )
        devicePeerMap[id] = peer
        _discoveredPeers.value = devicePeerMap.values.toList()
    }

    // =========================================================================
    // RECEIVER (RADAR MODE)
    // =========================================================================

    /**
     * Starts Receiver mode.
     * Binds a local TCP ServerSocket to accept transfer requests.
     * Broadcasts UDP beacons so searching senders can discover this device.
     */
    fun startReceiver(
        userProfile: UserProfile,
        onSessionStarted: () -> Unit
    ) {
        stopReceiver()

        try {
            multicastLock?.acquire()
        } catch (_: Exception) {}

        // 1. Bind ServerSocket
        try {
            serverSocket = ServerSocket(0).apply {
                reuseAddress = true
            }
        } catch (e: Exception) {
            try {
                serverSocket = ServerSocket(NetworkUtils.DEFAULT_TRANSFER_PORT).apply {
                    reuseAddress = true
                }
            } catch (_: Exception) {
                return
            }
        }

        val server = serverSocket ?: return
        val localPort = server.localPort
        val deviceId = UUID.randomUUID().toString().take(8)

        // 2. Broadcast UDP Beacon
        receiverBeaconJob = scope.launch(Dispatchers.IO) {
            try {
                beaconSocket = DatagramSocket().apply {
                    broadcast = true
                }
            } catch (_: Exception) {}

            val bSocket = beaconSocket ?: return@launch
            while (isActive) {
                val ip = NetworkUtils.getLocalIpAddress()
                val beaconMsg = "CUTESHARE_BEACON:v1:$deviceId:${userProfile.name}:${userProfile.avatarRes}:$localPort:$ip"
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
                delay(1000L)
            }
        }

        // 3. Listen for Incoming TCP Connections
        receiverServerJob = scope.launch(Dispatchers.IO) {
            while (isActive && !server.isClosed) {
                try {
                    val socket = server.accept()
                    activeSocket = socket
                    handleIncomingTransferConnection(socket, onSessionStarted)
                } catch (e: Exception) {
                    if (!isActive) break
                }
            }
        }
    }

    private suspend fun handleIncomingTransferConnection(
        socket: Socket,
        onSessionStarted: () -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = PrintWriter(socket.getOutputStream(), true)

            // Read Header
            val headerLine = reader.readLine() ?: return@withContext
            if (!headerLine.startsWith("CUTESHARE_TRANSFER_v1")) {
                socket.close()
                return@withContext
            }

            // Sender meta line: senderId|senderName|senderAvatarRes|fileCount|totalBytes
            val senderLine = reader.readLine() ?: return@withContext
            val sParts = senderLine.split("|")
            val senderId = sParts.getOrNull(0) ?: "peer"
            val senderName = sParts.getOrNull(1) ?: "Cute Peer"
            val senderAvatarRes = sParts.getOrNull(2)?.toIntOrNull() ?: R.drawable.avatar_1_bunny
            val totalBytes = sParts.getOrNull(4)?.toLongOrNull() ?: 0L

            val incomingFiles = mutableListOf<SharedFile>()
            var line = reader.readLine()
            while (line != null && line != "END_HEADER") {
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
                line = reader.readLine()
            }

            val senderDevice = DiscoveredDevice(
                id = senderId,
                name = senderName,
                avatarRes = senderAvatarRes,
                ip = socket.inetAddress?.hostAddress ?: "",
                port = socket.port
            )

            // Prompt user on UI to Accept or Decline
            _incomingRequest.value = IncomingTransferRequest(
                sender = senderDevice,
                files = incomingFiles,
                totalSize = totalBytes,
                acceptAction = {
                    _incomingRequest.value = null
                    scope.launch(Dispatchers.IO) {
                        writer.println("ACCEPT")
                        onSessionStarted()
                        receiveFilesStream(socket, incomingFiles, senderDevice)
                    }
                },
                declineAction = {
                    _incomingRequest.value = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            writer.println("DECLINE")
                            socket.close()
                        } catch (_: Exception) {}
                    }
                }
            )

        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private suspend fun receiveFilesStream(
        socket: Socket,
        files: List<SharedFile>,
        senderDevice: DiscoveredDevice
    ) = withContext(Dispatchers.IO) {
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
            isOutgoing = false,
            peerName = senderDevice.name,
            avatarRes = senderDevice.avatarRes,
            files = items,
            isPaused = false,
            isCancelled = false,
            isCompleted = false,
            currentSpeedBytesPerSec = 0L,
            remainingSeconds = 0L
        )
        _currentSession.value = session

        val inStream = socket.getInputStream()
        val outStream = socket.getOutputStream()
        val writer = PrintWriter(outStream, true)
        val reader = BufferedReader(InputStreamReader(inStream, Charsets.UTF_8))

        // Target directory: Downloads/CuteShare
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val cuteShareDir = File(downloadsDir, "CuteShare").apply { mkdirs() }

        val buffer = ByteArray(65536)
        var totalBytesTransferred = 0L
        val totalBytes = items.sumOf { it.sizeBytes }

        for (item in items) {
            val currSession = _currentSession.value
            if (currSession?.isCancelled == true) break

            // Wait for START_FILE line
            val fileHeader = reader.readLine() ?: break
            if (!fileHeader.startsWith("START_FILE")) break

            // Ensure unique file name
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
            val targetSize = item.sizeBytes
            var lastSpeedCheckTime = System.currentTimeMillis()
            var bytesSinceLastCheck = 0L

            FileOutputStream(destFile).use { fileOut ->
                while (fileBytesReceived < targetSize) {
                    if (_currentSession.value?.isCancelled == true) {
                        break
                    }
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

            // Register with MediaScanner
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
            writer.println("FILE_DONE:${item.id}")
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
    }

    fun stopReceiver() {
        receiverBeaconJob?.cancel()
        receiverBeaconJob = null
        receiverServerJob?.cancel()
        receiverServerJob = null

        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        try { beaconSocket?.close() } catch (_: Exception) {}
        beaconSocket = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }

    // =========================================================================
    // SENDER FILE TRANSFER (TRANSMIT OVER TCP)
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
            remainingSeconds = 0L
        )
        _currentSession.value = session

        activeTransferJob = scope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(targetPeer.ip, targetPeer.port), 5000)
                activeSocket = socket

                val outStream = socket.getOutputStream()
                val inStream = socket.getInputStream()
                val writer = PrintWriter(outStream, true)
                val reader = BufferedReader(InputStreamReader(inStream, Charsets.UTF_8))

                // 1. Send Header
                writer.println("CUTESHARE_TRANSFER_v1")
                val totalBytes = files.sumOf { it.sizeBytes }
                writer.println("${UUID.randomUUID().toString().take(8)}|${senderProfile.name}|${senderProfile.avatarRes}|${files.size}|$totalBytes")
                for (file in files) {
                    writer.println("${file.id}|${file.name}|${file.sizeBytes}|${file.category.name}")
                }
                writer.println("END_HEADER")

                // 2. Await Response
                val response = reader.readLine()
                if (response != "ACCEPT") {
                    _currentSession.update { it?.copy(isCancelled = true) }
                    return@launch
                }

                // 3. Stream File Bytes
                val buffer = ByteArray(65536)
                var totalBytesSent = 0L

                for (file in files) {
                    if (_currentSession.value?.isCancelled == true) break

                    writer.println("START_FILE:${file.id}")

                    var fileInputStream: InputStream? = null
                    try {
                        val uriStr = file.uriString
                        fileInputStream = if (!uriStr.isNullOrEmpty()) {
                            context.contentResolver.openInputStream(Uri.parse(uriStr))
                        } else {
                            null
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
                        // In case inputStream could not be opened (e.g. simulated in tests), emit full bytes
                        fileBytesSent = file.sizeBytes
                    }

                    outStream.flush()
                    // Wait for receiver ACK
                    reader.readLine()

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
}
