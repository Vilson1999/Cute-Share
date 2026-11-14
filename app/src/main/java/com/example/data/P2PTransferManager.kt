package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.provider.MediaStore
import androidx.core.net.toUri
import com.example.model.CartoonAvatars
import com.example.model.DiscoveredDevice
import com.example.model.FileCategory
import com.example.model.SharedFile
import com.example.model.TransferFileItem
import com.example.model.TransferSession
import com.example.model.TransferStatus
import com.example.model.UserProfile
import com.example.model.formatBytes
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
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestrates peer discovery, Wi-Fi Direct group/link setup, LAN/NSD fallback,
 * bidirectional GO/Client socket rendezvous, and verified TCP byte streaming.
 */
class P2PTransferManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _currentSession = MutableStateFlow<TransferSession?>(null)
    val currentSession: StateFlow<TransferSession?> = _currentSession.asStateFlow()

    private val _transferHistory = MutableStateFlow<List<TransferSession>>(emptyList())
    val transferHistory: StateFlow<List<TransferSession>> = _transferHistory.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredDevice>> = _discoveredPeers.asStateFlow()

    data class IncomingTransferRequest(
        val sender: DiscoveredDevice,
        val files: List<SharedFile>,
        val totalSize: Long,
        val acceptAction: () -> Unit,
        val declineAction: () -> Unit
    )

    private val _incomingRequest = MutableStateFlow<IncomingTransferRequest?>(null)
    val incomingRequest: StateFlow<IncomingTransferRequest?> = _incomingRequest.asStateFlow()

    private val p2pController = WifiP2pController(context)
    private val lanDiscovery = LanDiscoveryHelper(context, scope)

    private data class CachedCutePeerMeta(
        val name: String,
        val avatarRes: Int,
        val port: Int,
        val lanIp: String? = null
    )

    private val discoveredDevicesMap = ConcurrentHashMap<String, DiscoveredDevice>()
    private val macToCuteProfileMap = ConcurrentHashMap<String, CachedCutePeerMeta>()

    @Volatile
    private var isReceiverActive = false
    @Volatile
    private var isDiscoveryActive = false
    @Volatile
    private var discoveryStartedAtMs = 0L
    @Volatile
    private var activeListeningPort = TCP_TRANSFER_PORT
    private val incomingTransferLock = AtomicBoolean(false)

    // Sockets & Jobs
    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var receiverServerJob: Job? = null
    private var receiverSetupJob: Job? = null
    private var receiverKeepAliveJob: Job? = null
    private var reverseConnectJob: Job? = null
    private var discoverySetupJob: Job? = null
    private var peerDiscoveryPollingJob: Job? = null
    private var activeTransferJob: Job? = null

    @Volatile
    private var latestReceiverProfile: UserProfile? = null
    @Volatile
    private var latestOnSessionStarted: (() -> Unit)? = null

    companion object {
        const val UDP_DISCOVERY_PORT = NetworkUtils.UDP_DISCOVERY_PORT
        const val TCP_TRANSFER_PORT = NetworkUtils.WIFI_DIRECT_DEFAULT_PORT
        const val REVERSE_HANDSHAKE_PORT = NetworkUtils.REVERSE_HANDSHAKE_PORT
        const val SERVICE_TYPE = "_cuteshare._tcp"
        const val DOWNLOAD_SUBFOLDER = DownloadStorageHelper.DOWNLOAD_SUBFOLDER

        fun sanitizeDownloadFileName(
            rawName: String,
            timestamp: Long = System.currentTimeMillis()
        ): String = DownloadStorageHelper.sanitizeDownloadFileName(rawName, timestamp)

        /**
         * Merges peers discovered concurrently over Wi-Fi Direct DNS-SD, NSD mDNS, UDP Beacon,
         * and TCP Subnet Ping into unified peer bubbles retaining BOTH LAN IP and P2P MAC.
         */
        fun mergeDiscoveredPeers(peers: Collection<DiscoveredDevice>): List<DiscoveredDevice> {
            if (peers.isEmpty()) return emptyList()
            val merged = mutableListOf<DiscoveredDevice>()

            for (peer in peers) {
                val matchIdx = merged.indexOfFirst { existing ->
                    if (peer.id.startsWith("manual_") || existing.id.startsWith("manual_")) {
                        peer.id == existing.id
                    } else {
                        val sameMac = !peer.p2pMacAddress.isNullOrBlank() &&
                            peer.p2pMacAddress.equals(existing.p2pMacAddress, ignoreCase = true)
                        val sameProfile = peer.name.trim().equals(existing.name.trim(), ignoreCase = true) &&
                            peer.avatarRes == existing.avatarRes
                        sameMac || sameProfile
                    }
                }

                if (matchIdx == -1) {
                    merged.add(peer)
                    continue
                }

                val existing = merged[matchIdx]
                val peerIsLan = peer.ip.isNotBlank() &&
                    !peer.ip.startsWith("127.") &&
                    peer.ip != NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP
                val existingIsLan = existing.ip.isNotBlank() &&
                    !existing.ip.startsWith("127.") &&
                    existing.ip != NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP

                val choosePeerNetwork = when {
                    peerIsLan && !existingIsLan -> true
                    !peerIsLan && existingIsLan -> false
                    else -> peer.lastSeenTimestamp >= existing.lastSeenTimestamp
                }

                val existingHasGenericName = existing.id.startsWith("fallback_")
                val peerHasGenericName = peer.id.startsWith("fallback_")
                val preferredName = when {
                    existingHasGenericName && !peerHasGenericName -> peer.name
                    !existingHasGenericName && peerHasGenericName -> existing.name
                    peer.lastSeenTimestamp >= existing.lastSeenTimestamp -> peer.name
                    else -> existing.name
                }
                val preferredAvatar = when {
                    existingHasGenericName && !peerHasGenericName -> peer.avatarRes
                    else -> existing.avatarRes
                }

                merged[matchIdx] = existing.copy(
                    id = if (choosePeerNetwork) peer.id else existing.id,
                    name = preferredName,
                    avatarRes = preferredAvatar,
                    ip = if (choosePeerNetwork) peer.ip else existing.ip,
                    port = if (choosePeerNetwork) peer.port else existing.port,
                    p2pMacAddress = peer.p2pMacAddress ?: existing.p2pMacAddress,
                    lastSeenTimestamp = maxOf(existing.lastSeenTimestamp, peer.lastSeenTimestamp)
                )
            }

            return merged.sortedByDescending { it.lastSeenTimestamp }
        }
    }

    // =========================================================================
    // SENDER DISCOVERY (DNS-SD + NSD + UDP + FAST PARALLEL SUBNET PING + P2P)
    // =========================================================================

    fun startDiscovery(senderProfile: UserProfile) {
        stopDiscovery(keepP2pReceiver = false)
        isDiscoveryActive = true
        discoveryStartedAtMs = System.currentTimeMillis()
        discoveredDevicesMap.clear()
        _discoveredPeers.value = emptyList()

        p2pController.ensureReceiverRegistered(
            onPeersUpdated = { devices -> processP2pPeerList(devices) },
            onConnectionChanged = { snap -> handleP2pConnectionChanged(snap) }
        )

        // 1. NSD mDNS + UDP Broadcast Beacon Discovery
        lanDiscovery.startDiscovery { peer ->
            upsertDiscoveredPeer(peer)
        }

        // 2. Serialized Wi-Fi Direct DNS-SD + Peer Discovery
        discoverySetupJob = scope.launch(Dispatchers.IO) {
            p2pController.startServiceAndPeerDiscovery(
                onTxtRecord = { fullDomain, record, device ->
                    val mac = device.deviceAddress?.takeIf { it.isNotBlank() } ?: fullDomain
                    val cuteName = record["name"]?.trim()?.ifBlank { null }
                        ?: WifiP2pController.extractCuteShareNameFromInstance(device.deviceName)
                        ?: "Cute Friend"
                    val avatarRes = CartoonAvatars.resolveAvatarRes(
                        rawValue = record["avatar"],
                        indexHint = record["avatarIdx"],
                        fallbackSeed = cuteName
                    )
                    val port = record["port"]?.toIntOrNull() ?: TCP_TRANSFER_PORT
                    val lanIp = record["lanIp"]?.trim()?.takeIf {
                        it.isNotEmpty() && !it.startsWith("127.") && it != NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP
                    }

                    macToCuteProfileMap[mac] = CachedCutePeerMeta(
                        name = cuteName,
                        avatarRes = avatarRes,
                        port = port,
                        lanIp = lanIp
                    )

                    upsertDiscoveredPeer(
                        DiscoveredDevice(
                            id = mac,
                            name = cuteName,
                            avatarRes = avatarRes,
                            ip = lanIp ?: NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP,
                            port = port,
                            p2pMacAddress = device.deviceAddress,
                            supports5Ghz = true,
                            is5GhzActive = true,
                            lastSeenTimestamp = System.currentTimeMillis()
                        )
                    )
                },
                onServiceResponse = { instanceName, _, device ->
                    val mac = device.deviceAddress ?: return@startServiceAndPeerDiscovery
                    val existingMeta = macToCuteProfileMap[mac]
                    val extractedName = WifiP2pController.extractCuteShareNameFromInstance(instanceName)

                    if (existingMeta != null) {
                        upsertDiscoveredPeer(
                            DiscoveredDevice(
                                id = mac,
                                name = existingMeta.name,
                                avatarRes = existingMeta.avatarRes,
                                ip = existingMeta.lanIp ?: NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP,
                                port = existingMeta.port,
                                p2pMacAddress = mac,
                                supports5Ghz = true,
                                is5GhzActive = true,
                                lastSeenTimestamp = System.currentTimeMillis()
                            )
                        )
                    } else if (extractedName != null) {
                        val fallbackAvatar = CartoonAvatars.resolveAvatarRes(null, null, extractedName)
                        macToCuteProfileMap[mac] = CachedCutePeerMeta(
                            name = extractedName,
                            avatarRes = fallbackAvatar,
                            port = TCP_TRANSFER_PORT
                        )
                        upsertDiscoveredPeer(
                            DiscoveredDevice(
                                id = mac,
                                name = extractedName,
                                avatarRes = fallbackAvatar,
                                ip = NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP,
                                port = TCP_TRANSFER_PORT,
                                p2pMacAddress = mac,
                                supports5Ghz = true,
                                is5GhzActive = true,
                                lastSeenTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            )
        }

        // 3. Periodic discovery refresh & parallel subnet probe
        peerDiscoveryPollingJob = scope.launch(Dispatchers.IO) {
            var cycle = 0
            while (isActive && isDiscoveryActive) {
                p2pController.requestPeersNonBlocking()
                lanDiscovery.scanLocalSubnetForReceivers { peer ->
                    upsertDiscoveredPeer(peer)
                }

                if (cycle > 0 && cycle % 2 == 0) {
                    p2pController.refreshDiscovery()
                }
                cycle++

                val now = System.currentTimeMillis()
                val expiredIds = discoveredDevicesMap.filter { (id, peer) ->
                    !id.startsWith("manual_") && (now - peer.lastSeenTimestamp > 20_000L)
                }.keys
                if (expiredIds.isNotEmpty()) {
                    expiredIds.forEach { discoveredDevicesMap.remove(it) }
                    publishDiscoveredPeers()
                }

                delay(2500L)
            }
        }
    }

    private fun processP2pPeerList(peers: Collection<WifiP2pDevice>) {
        if (!isDiscoveryActive) return
        if (peers.isEmpty()) return

        val elapsedSinceScanStart = System.currentTimeMillis() - discoveryStartedAtMs
        val hasVerifiedCuteSharePeers = discoveredDevicesMap.keys.any { !it.startsWith("fallback_") }
        val allowFallbackPhonePeers = elapsedSinceScanStart >= 5500L && !hasVerifiedCuteSharePeers

        var changed = false
        for (dev in peers) {
            val mac = dev.deviceAddress ?: continue
            val mappedMeta = macToCuteProfileMap[mac]
            val dName = dev.deviceName.orEmpty()
            val instanceNameFromDev = WifiP2pController.extractCuteShareNameFromInstance(dName)
            val isExplicitCuteShare = mappedMeta != null || instanceNameFromDev != null
            val isGroupPeer = dev.isGroupOwner ||
                dev.status == WifiP2pDevice.CONNECTED ||
                dev.status == WifiP2pDevice.INVITED

            val shouldInclude = isExplicitCuteShare || isGroupPeer ||
                (allowFallbackPhonePeers && WifiP2pController.isPhoneOrTabletDeviceType(dev.primaryDeviceType))

            if (!shouldInclude) continue

            val name = mappedMeta?.name
                ?: instanceNameFromDev
                ?: dName.ifBlank { "Nearby Phone" }
            val avatarRes = mappedMeta?.avatarRes
                ?: CartoonAvatars.resolveAvatarRes(null, null, name)
            val key = if (isExplicitCuteShare || isGroupPeer) mac else "fallback_$mac"
            val peer = DiscoveredDevice(
                id = key,
                name = name,
                avatarRes = avatarRes,
                ip = mappedMeta?.lanIp ?: NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP,
                port = mappedMeta?.port ?: TCP_TRANSFER_PORT,
                p2pMacAddress = mac,
                supports5Ghz = true,
                is5GhzActive = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            if (isExplicitCuteShare) {
                discoveredDevicesMap.remove("fallback_$mac")
            }
            discoveredDevicesMap[key] = peer
            changed = true
        }
        if (changed) {
            publishDiscoveredPeers()
        }
    }

    private fun upsertDiscoveredPeer(peer: DiscoveredDevice) {
        if (!peer.p2pMacAddress.isNullOrBlank() && !peer.id.startsWith("fallback_")) {
            macToCuteProfileMap[peer.p2pMacAddress] = CachedCutePeerMeta(
                name = peer.name,
                avatarRes = peer.avatarRes,
                port = peer.port,
                lanIp = peer.ip.takeIf { it != NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP }
            )
            discoveredDevicesMap.remove("fallback_${peer.p2pMacAddress}")
        }
        discoveredDevicesMap[peer.id] = peer
        publishDiscoveredPeers()
    }

    private fun publishDiscoveredPeers() {
        _discoveredPeers.value = mergeDiscoveredPeers(discoveredDevicesMap.values)
    }

    fun stopDiscovery(keepP2pReceiver: Boolean = false) {
        isDiscoveryActive = false
        discoverySetupJob?.cancel()
        discoverySetupJob = null

        peerDiscoveryPollingJob?.cancel()
        peerDiscoveryPollingJob = null

        lanDiscovery.stopDiscovery()
        if (!isReceiverActive && !keepP2pReceiver) {
            lanDiscovery.releaseMulticastLock()
            p2pController.unregisterReceiver()
        }

        if (!keepP2pReceiver) {
            scope.launch(Dispatchers.IO) {
                p2pController.stopScanningOnly()
            }
        }

        discoveredDevicesMap.clear()
        _discoveredPeers.value = emptyList()
    }

    // =========================================================================
    // RECEIVER: ADVERTISE SERVICE + GO GROUP + TCP SERVER + REVERSE HANDSHAKE
    // =========================================================================

    fun startReceiver(
        userProfile: UserProfile,
        onSessionStarted: () -> Unit
    ) {
        stopReceiver(clearFinishedSession = true)
        isReceiverActive = true
        latestReceiverProfile = userProfile
        latestOnSessionStarted = onSessionStarted
        incomingTransferLock.set(false)

        p2pController.ensureReceiverRegistered(
            onPeersUpdated = null,
            onConnectionChanged = { snap -> handleP2pConnectionChanged(snap) }
        )

        // 1. Bind ServerSocket first so we know the exact active port before advertising
        val server = bindServerSocketPreferringDefault() ?: return
        serverSocket = server
        activeListeningPort = server.localPort

        // 2. Start LAN NSD + Multicast UDP Beacons advertising actual bound port
        lanDiscovery.startAdvertising(
            profile = userProfile,
            listeningPort = activeListeningPort,
            getP2pMac = { p2pController.thisDeviceMac.value },
            sanitizeField = ::sanitizeWireField
        )

        // 3. Serialized Wi-Fi Direct Local Service + Group Owner setup
        val safeName = sanitizeWireField(userProfile.name).ifBlank { "Cute Friend" }
        val avatarIdx = CartoonAvatars.avatarIndexOf(userProfile.avatarRes)
        val txtRecord = hashMapOf(
            "name" to safeName,
            "avatar" to userProfile.avatarRes.toString(),
            "avatarIdx" to avatarIdx.toString(),
            "port" to activeListeningPort.toString()
        )
        NetworkUtils.getWlanLocalIpAddress()?.let { wlanIp ->
            txtRecord["lanIp"] = wlanIp
        }

        receiverSetupJob = scope.launch(Dispatchers.IO) {
            p2pController.startReceiverAdvertising(
                serviceName = "CuteShare_$safeName",
                serviceType = SERVICE_TYPE,
                txtRecord = txtRecord
            )
            // Check immediately if a connection/group is already active in client role
            p2pController.connectionSnapshot.value?.let { handleP2pConnectionChanged(it) }
        }

        // 4. Keep-alive loop for OEMs without autonomous GO + reverse connect recovery
        receiverKeepAliveJob = scope.launch(Dispatchers.IO) {
            while (isActive && isReceiverActive) {
                delay(5500L)
                if (!isReceiverActive || isTransferActive()) continue
                p2pController.keepReceiverDiscoverableIfNoGroup()
                val snap = p2pController.queryConnectionSnapshot()
                if (snap?.info?.groupFormed == true && !snap.info.isGroupOwner) {
                    val goIp = snap.info.groupOwnerAddress?.hostAddress
                        ?: NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP
                    if (reverseConnectJob?.isActive != true) {
                        launchReverseConnectToGroupOwner(goIp, userProfile, onSessionStarted)
                    }
                }
            }
        }

        // 5. High-Speed TCP ServerSocket Accept Loop
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
                    if (!isActive || server.isClosed) break
                }
            }
        }
    }

    private fun handleP2pConnectionChanged(snapshot: P2pConnectionSnapshot?) {
        if (!isReceiverActive || isTransferActive()) return
        val info = snapshot?.info ?: return
        val profile = latestReceiverProfile ?: return
        val onStarted = latestOnSessionStarted ?: return

        // If GO role flipped during P2P negotiation, Receiver became Client while Sender became GO.
        // Connect back to Sender's REVERSE_HANDSHAKE_PORT so data flows cleanly without needing ARP.
        if (info.groupFormed && !info.isGroupOwner) {
            val goIp = info.groupOwnerAddress?.hostAddress ?: NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP
            if (reverseConnectJob?.isActive != true) {
                launchReverseConnectToGroupOwner(goIp, profile, onStarted)
            }
        }
    }

    private fun launchReverseConnectToGroupOwner(
        goIp: String,
        userProfile: UserProfile,
        onSessionStarted: () -> Unit
    ) {
        reverseConnectJob?.cancel()
        val safeName = sanitizeWireField(userProfile.name).ifBlank { "Cute Friend" }
        val avatarIdx = CartoonAvatars.avatarIndexOf(userProfile.avatarRes)

        reverseConnectJob = scope.launch(Dispatchers.IO) {
            while (isActive && isReceiverActive && !isTransferActive()) {
                val snap = p2pController.connectionSnapshot.value ?: p2pController.queryConnectionSnapshot()
                if (snap?.info?.groupFormed == true && snap.info.isGroupOwner) break

                val reverseSock = NetworkUtils.connectTcpSocket(
                    context = context,
                    host = goIp,
                    port = REVERSE_HANDSHAKE_PORT,
                    preferP2p = true,
                    timeoutMs = 1500
                )
                if (reverseSock != null) {
                    try {
                        reverseSock.soTimeout = 15_000
                        val outStream = BufferedOutputStream(reverseSock.getOutputStream(), 65536)
                        outStream.write(
                            "CUTESHARE_RECEIVER_READY|$safeName|${userProfile.avatarRes}|$avatarIdx\n"
                                .toByteArray(Charsets.UTF_8)
                        )
                        outStream.flush()

                        handleIncomingWifiDirectConnection(reverseSock, userProfile, onSessionStarted)
                        break
                    } catch (_: Exception) {
                        try { reverseSock.close() } catch (_: Exception) {}
                    }
                }
                delay(550L)
            }
        }
    }

    private fun bindServerSocketPreferringDefault(): ServerSocket? {
        try {
            return ServerSocket(TCP_TRANSFER_PORT).apply {
                reuseAddress = true
                receiveBufferSize = 524288
            }
        } catch (_: Exception) {
            return try {
                ServerSocket(0).apply {
                    reuseAddress = true
                    receiveBufferSize = 524288
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun handleIncomingWifiDirectConnection(
        socket: Socket,
        userProfile: UserProfile,
        onSessionStarted: () -> Unit
    ) = withContext(Dispatchers.IO) {
        var claimedTransfer = false
        try {
            socket.soTimeout = 15_000
            val inStream = BufferedInputStream(socket.getInputStream(), 65536)
            val outStream = BufferedOutputStream(socket.getOutputStream(), 65536)

            val firstLine = readAsciiLine(inStream, maxLen = 256)

            if (firstLine.startsWith("CUTESHARE_PING")) {
                val ip = NetworkUtils.getWlanLocalIpAddress() ?: NetworkUtils.getLocalIpAddress()
                val safeName = sanitizeWireField(userProfile.name).ifBlank { "Cute Friend" }
                val avatarIdx = CartoonAvatars.avatarIndexOf(userProfile.avatarRes)
                val macHint = p2pController.thisDeviceMac.value.orEmpty()
                val resp =
                    "CUTESHARE_PONG|$safeName|${userProfile.avatarRes}|$activeListeningPort|$ip|$avatarIdx|$macHint\n"
                outStream.write(resp.toByteArray(Charsets.UTF_8))
                outStream.flush()
                socket.close()
                return@withContext
            }

            if (!firstLine.startsWith("CUTESHARE_WIFIDIRECT_v1")) {
                socket.close()
                return@withContext
            }

            if (!incomingTransferLock.compareAndSet(false, true)) {
                socket.close()
                return@withContext
            }
            claimedTransfer = true
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
                    val fCategory = try {
                        FileCategory.valueOf(fParts[3])
                    } catch (_: Exception) {
                        FileCategory.DOCUMENTS
                    }

                    incomingFiles.add(
                        SharedFile(
                            id = fId,
                            name = fName,
                            sizeBytes = fSize,
                            formattedSize = formatBytes(fSize),
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
                wifiBand = "Wi-Fi Direct",
                statusMessage = null
            )
            _currentSession.value = session
            _incomingRequest.value = null

            outStream.write("ACCEPT\n".toByteArray(Charsets.UTF_8))
            outStream.flush()

            withContext(Dispatchers.Main) {
                onSessionStarted()
            }

            receiveFilesStream(socket, inStream, outStream, incomingFiles)
        } catch (_: Exception) {
            _currentSession.update { s ->
                if (s != null && !s.isCompleted) {
                    s.copy(
                        isCancelled = true,
                        currentSpeedBytesPerSec = 0L,
                        remainingSeconds = 0L,
                        statusMessage = "Connection closed during transfer",
                        files = s.files.map { f ->
                            if (!f.isFinished) f.copy(status = TransferStatus.CANCELLED) else f
                        }
                    )
                } else s
            }
            try { socket.close() } catch (_: Exception) {}
        } finally {
            if (claimedTransfer) {
                incomingTransferLock.set(false)
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

        fun cancelRemainingItems(failedItemId: String? = null, reason: String? = null) {
            _currentSession.update { s ->
                s?.copy(
                    isCancelled = true,
                    currentSpeedBytesPerSec = 0L,
                    remainingSeconds = 0L,
                    statusMessage = reason ?: s.statusMessage,
                    files = s.files.map { f ->
                        if (f.id == failedItemId || !f.isFinished) {
                            f.copy(status = TransferStatus.CANCELLED)
                        } else f
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

            val dest = DownloadStorageHelper.openDownloadDestination(context, item.name)
            if (dest == null) {
                cancelRemainingItems(item.id, "Could not create file in Download/Cute Share")
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
                        while (_currentSession.value?.isPaused == true &&
                            _currentSession.value?.isCancelled != true
                        ) {
                            delay(200)
                            lastSpeedCheckTime = System.currentTimeMillis()
                        }
                        if (_currentSession.value?.isCancelled == true) break

                        val toRead = (targetSize - fileBytesReceived)
                            .coerceAtMost(buffer.size.toLong())
                            .toInt()
                        val read = inStream.read(buffer, 0, toRead)
                        if (read == -1) break

                        fileOut.write(buffer, 0, read)
                        fileBytesReceived += read
                        totalBytesTransferred += read
                        bytesSinceLastCheck += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSpeedCheckTime
                        if (elapsed >= 150 || fileBytesReceived >= targetSize) {
                            val windowElapsed =
                                if (elapsed > 0) elapsed else (now - fileStartTime).coerceAtLeast(1L)
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
                                                progress = if (targetSize > 0) {
                                                    (fileBytesReceived.toFloat() / targetSize).coerceIn(0f, 1f)
                                                } else 1f
                                            )
                                        } else f
                                    }
                                )
                            }
                        }
                    }
                    fileOut.flush()
                }
                fileCompleted =
                    fileBytesReceived >= targetSize && _currentSession.value?.isCancelled != true
            } finally {
                DownloadStorageHelper.finalizeDownloadDestination(
                    context = context,
                    dest = dest,
                    completed = fileCompleted
                )
            }

            if (!fileCompleted) {
                cancelRemainingItems(item.id)
                break
            }

            completedFilesCount++
            _currentSession.update { s ->
                s?.copy(
                    files = s.files.map { f ->
                        if (f.id == item.id) {
                            f.copy(
                                transferredBytes = fileBytesReceived,
                                progress = 1f,
                                status = TransferStatus.COMPLETED
                            )
                        } else f
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
                remainingSeconds = 0L,
                statusMessage = null
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

    fun stopReceiver(clearFinishedSession: Boolean = false) {
        isReceiverActive = false
        receiverSetupJob?.cancel()
        receiverSetupJob = null
        receiverKeepAliveJob?.cancel()
        receiverKeepAliveJob = null
        reverseConnectJob?.cancel()
        reverseConnectJob = null
        receiverServerJob?.cancel()
        receiverServerJob = null
        incomingTransferLock.set(false)

        lanDiscovery.stopAdvertising()

        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null

        scope.launch(Dispatchers.IO) {
            p2pController.stopReceiverAdvertising()
        }

        if (!isDiscoveryActive) {
            lanDiscovery.releaseMulticastLock()
            p2pController.unregisterReceiver()
        }

        if (clearFinishedSession) {
            val cur = _currentSession.value
            if (cur != null && (cur.isCompleted || cur.isCancelled)) {
                _currentSession.value = null
            }
        }
    }

    // =========================================================================
    // SENDER: SERIALIZED P2P CONNECTION + LAN/NSD + DUAL-ROLE TCP TRANSFER
    // =========================================================================

    fun startOutgoingTransfer(
        targetPeer: DiscoveredDevice,
        files: List<SharedFile>,
        senderProfile: UserProfile
    ) {
        activeTransferJob?.cancel()

        // Halt active discovery scanning cleanly WITHOUT unregistering wifiP2pReceiver
        val cachedTargetMac = resolveTargetMac(targetPeer)
        stopDiscovery(keepP2pReceiver = true)
        p2pController.ensureReceiverRegistered(
            onPeersUpdated = null,
            onConnectionChanged = { snap ->
                if (snap?.info?.groupFormed == true) {
                    updateSessionBand(
                        if (NetworkUtils.findP2pNetwork(context) != null) "Wi-Fi Direct" else "Wi-Fi Direct"
                    )
                }
            }
        )

        val resolvedFiles = files.map { file ->
            val exactSize = resolveAccurateFileSize(file)
            if (exactSize != file.sizeBytes) file.copy(sizeBytes = exactSize) else file
        }

        val items = resolvedFiles.map { file ->
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
            wifiBand = "Wi-Fi Direct",
            statusMessage = "Connecting to ${targetPeer.name}..."
        )
        _currentSession.value = session

        activeTransferJob = scope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            var reverseServer: ServerSocket? = null
            try {
                // 1. Fast direct LAN path when devices share a Wi-Fi network or hotspot
                val hasLanIp = targetPeer.ip.isNotBlank() &&
                    !targetPeer.ip.startsWith("127.") &&
                    targetPeer.ip != NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP

                if (hasLanIp) {
                    updateSessionStatus("Connecting over Wi-Fi LAN...")
                    socket = NetworkUtils.connectTcpSocket(
                        context = context,
                        host = targetPeer.ip,
                        port = targetPeer.port,
                        preferP2p = false,
                        timeoutMs = 1300
                    )
                    if (socket != null) {
                        updateSessionBand("Wi-Fi LAN")
                    }
                }

                // 2. Pure offline / AP-isolated Wi-Fi Direct connection path
                if (socket == null && cachedTargetMac != null &&
                    p2pController.hasWifiDirectPermission() && p2pController.isAvailable()
                ) {
                    val initiated = p2pController.initiatePeerConnection(cachedTargetMac) { status ->
                        updateSessionStatus(status)
                    }
                    if (!initiated) {
                        updateSessionStatus("Establishing Wi-Fi Direct group...")
                    }

                    // Open reverse-handshake server on 8890 immediately so if Sender wins GO,
                    // Receiver can connect back right away without waiting for polling cycle.
                    reverseServer = try {
                        ServerSocket(REVERSE_HANDSHAKE_PORT).apply {
                            reuseAddress = true
                            soTimeout = 550
                            receiveBufferSize = 524288
                        }
                    } catch (_: Exception) {
                        null
                    }

                    val deadline = System.currentTimeMillis() + 35_000L
                    var cycle = 0
                    var retriedConnect = false

                    while (socket == null &&
                        System.currentTimeMillis() < deadline &&
                        isActive &&
                        _currentSession.value?.isCancelled != true
                    ) {
                        cycle++
                        val remainingMs = deadline - System.currentTimeMillis()
                        if (!retriedConnect && remainingMs < 20_000L &&
                            p2pController.connectionSnapshot.value?.info?.groupFormed != true
                        ) {
                            retriedConnect = true
                            updateSessionStatus("Waiting for approval...")
                            p2pController.initiatePeerConnection(cachedTargetMac) {}
                        } else if (cycle == 4 &&
                            p2pController.connectionSnapshot.value?.info?.groupFormed != true
                        ) {
                            updateSessionStatus("Connecting...")
                        }

                        val snap = p2pController.connectionSnapshot.value
                            ?: p2pController.queryConnectionSnapshot()

                        if (snap != null && snap.info.groupFormed) {
                            if (!snap.info.isGroupOwner) {
                                // Normal P2P topology: Receiver is Group Owner
                                updateSessionStatus("Connecting via Wi-Fi Direct link...")
                                val goHost = snap.info.groupOwnerAddress?.hostAddress
                                val hosts = listOfNotNull(
                                    goHost,
                                    NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP,
                                    targetPeer.ip.takeIf { it.isNotBlank() && !it.startsWith("127.") }
                                ).distinct()
                                val ports = listOf(targetPeer.port, TCP_TRANSFER_PORT).distinct()
                                for (host in hosts) {
                                    for (port in ports) {
                                        val s = NetworkUtils.connectTcpSocket(
                                            context = context,
                                            host = host,
                                            port = port,
                                            preferP2p = NetworkUtils.isP2pSubnetIp(host),
                                            timeoutMs = 1100
                                        )
                                        if (s != null) {
                                            socket = s
                                            break
                                        }
                                    }
                                    if (socket != null) break
                                }
                            } else {
                                // Flipped P2P topology: Sender became GO (`192.168.49.1`)!
                                // Wait for Receiver client's reverse TCP connection on REVERSE_HANDSHAKE_PORT
                                updateSessionStatus("Opening P2P reverse channel...")
                                val srv = reverseServer
                                if (srv != null) {
                                    try {
                                        val candidate = srv.accept()
                                        candidate.tcpNoDelay = true
                                        candidate.sendBufferSize = 524288
                                        candidate.receiveBufferSize = 524288
                                        candidate.soTimeout = 4000
                                        val readyLine = readAsciiLine(candidate.getInputStream(), maxLen = 256)
                                        if (readyLine.startsWith("CUTESHARE_RECEIVER_READY")) {
                                            socket = candidate
                                        } else {
                                            try { candidate.close() } catch (_: Exception) {}
                                        }
                                    } catch (_: Exception) {
                                        // Accept timeout (550ms), loop naturally
                                    }
                                }
                            }
                        }

                        // Secondary LAN check every few cycles if LAN link wasn't ready at t=0
                        if (socket == null && hasLanIp && cycle % 3 == 0) {
                            socket = NetworkUtils.connectTcpSocket(
                                context = context,
                                host = targetPeer.ip,
                                port = targetPeer.port,
                                preferP2p = false,
                                timeoutMs = 800
                            )
                            if (socket != null) {
                                updateSessionBand("Wi-Fi LAN")
                            }
                        }

                        if (socket == null && snap?.info?.isGroupOwner != true) {
                            delay(500L)
                        }
                    }
                } else if (socket == null) {
                    // Peer lacks P2P MAC and LAN failed on first try -> retry LAN / GO IP briefly
                    val retryHosts = listOfNotNull(
                        targetPeer.ip.takeIf { it.isNotBlank() && !it.startsWith("127.") },
                        NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP
                    ).distinct()
                    for (attempt in 1..6) {
                        if (!isActive || _currentSession.value?.isCancelled == true || socket != null) break
                        for (host in retryHosts) {
                            socket = NetworkUtils.connectTcpSocket(
                                context = context,
                                host = host,
                                port = targetPeer.port,
                                preferP2p = NetworkUtils.isP2pSubnetIp(host),
                                timeoutMs = 900
                            )
                            if (socket != null) break
                        }
                        if (socket == null) delay(500L)
                    }
                }

                try { reverseServer?.close() } catch (_: Exception) {}
                reverseServer = null

                val connectedSocket = socket
                if (connectedSocket == null || _currentSession.value?.isCancelled == true) {
                    _currentSession.update {
                        it?.copy(
                            isCancelled = true,
                            currentSpeedBytesPerSec = 0L,
                            remainingSeconds = 0L,
                            statusMessage = if (cachedTargetMac == null && !hasLanIp) {
                                "Peer unreachable"
                            } else {
                                "Connection failed"
                            },
                            files = it.files.map { f -> f.copy(status = TransferStatus.CANCELLED) }
                        )
                    }
                    return@launch
                }

                activeSocket = connectedSocket
                connectedSocket.soTimeout = 20_000
                updateSessionStatus("Sending file offer to ${targetPeer.name}...")

                val outStream = BufferedOutputStream(connectedSocket.getOutputStream(), 65536)
                val inStream = BufferedInputStream(connectedSocket.getInputStream(), 65536)

                val safeSenderName = sanitizeWireField(senderProfile.name).ifBlank { "Cute Friend" }
                val senderAvatarIdx = CartoonAvatars.avatarIndexOf(senderProfile.avatarRes)
                val header = StringBuilder()
                header.append("CUTESHARE_WIFIDIRECT_v1\n")
                val totalBytes = resolvedFiles.sumOf { it.sizeBytes }
                header.append(
                    "${UUID.randomUUID().toString().take(8)}|$safeSenderName|${senderProfile.avatarRes}|${resolvedFiles.size}|$totalBytes|$senderAvatarIdx\n"
                )
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
                            remainingSeconds = 0L,
                            statusMessage = "Transfer declined by ${targetPeer.name}",
                            files = it.files.map { f -> f.copy(status = TransferStatus.CANCELLED) }
                        )
                    }
                    return@launch
                }

                // Clear connecting banner once real byte transfer begins
                updateSessionStatus(null)
                connectedSocket.soTimeout = 45_000

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
                            while (_currentSession.value?.isPaused == true &&
                                _currentSession.value?.isCancelled != true
                            ) {
                                delay(200)
                                lastSpeedCheck = System.currentTimeMillis()
                            }
                            if (_currentSession.value?.isCancelled == true) break

                            val toRead = (file.sizeBytes - fileBytesSent)
                                .coerceAtMost(buffer.size.toLong())
                                .toInt()
                            val read = input.read(buffer, 0, toRead)
                            if (read == -1) break

                            outStream.write(buffer, 0, read)
                            fileBytesSent += read
                            totalBytesSent += read
                            bytesSinceLastCheck += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastSpeedCheck
                            if (elapsed >= 150 || fileBytesSent >= file.sizeBytes) {
                                val windowElapsed =
                                    if (elapsed > 0) elapsed else (now - fileStartTime).coerceAtLeast(1L)
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
                                                    progress = if (file.sizeBytes > 0) {
                                                        (fileBytesSent.toFloat() / file.sizeBytes).coerceIn(0f, 1f)
                                                    } else 1f
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
                                if (f.id == file.id) {
                                    f.copy(
                                        transferredBytes = fileBytesSent,
                                        progress = 1f,
                                        status = TransferStatus.COMPLETED
                                    )
                                } else f
                            }
                        )
                    }
                }

                val current = _currentSession.value
                if (current != null && !current.isCancelled) {
                    val completedSession = current.copy(
                        isCompleted = true,
                        currentSpeedBytesPerSec = 0L,
                        remainingSeconds = 0L,
                        statusMessage = null
                    )
                    _currentSession.value = completedSession
                    _transferHistory.update { listOf(completedSession) + it }
                } else if (current != null) {
                    _currentSession.value = current.copy(
                        currentSpeedBytesPerSec = 0L,
                        remainingSeconds = 0L
                    )
                }
            } catch (e: Exception) {
                _currentSession.update {
                    it?.copy(
                        isCancelled = true,
                        currentSpeedBytesPerSec = 0L,
                        remainingSeconds = 0L,
                        statusMessage = e.message?.take(80)
                            ?: "Connection interrupted during transfer",
                        files = it.files.map { f ->
                            if (!f.isFinished) f.copy(status = TransferStatus.CANCELLED) else f
                        }
                    )
                }
            } finally {
                try { reverseServer?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
                if (activeSocket === socket) activeSocket = null
                if (!isReceiverActive && !isDiscoveryActive) {
                    p2pController.unregisterReceiver()
                }
            }
        }
    }

    private fun resolveTargetMac(peer: DiscoveredDevice): String? {
        peer.p2pMacAddress?.takeIf { it.contains(":") }?.let { return it }
        peer.id.takeIf { it.contains(":") }?.let { return it }
        return macToCuteProfileMap.entries.firstOrNull { (_, meta) ->
            meta.name.equals(peer.name, ignoreCase = true) && meta.avatarRes == peer.avatarRes
        }?.key
    }

    private fun updateSessionStatus(message: String?) {
        _currentSession.update { it?.copy(statusMessage = message) }
    }

    private fun updateSessionBand(band: String) {
        _currentSession.update { it?.copy(wifiBand = band) }
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
                if (!item.isFinished) {
                    item.copy(status = if (newPaused) TransferStatus.PAUSED else TransferStatus.TRANSFERRING)
                } else item
            }
            session.copy(isPaused = newPaused, files = updated)
        }
    }

    fun cancelSession() {
        activeTransferJob?.cancel()
        try { activeSocket?.close() } catch (_: Exception) {}
        _currentSession.update { session ->
            if (session == null) return@update null
            val updated = session.files.map {
                if (!it.isFinished) it.copy(status = TransferStatus.CANCELLED) else it
            }
            session.copy(
                isCancelled = true,
                files = updated,
                currentSpeedBytesPerSec = 0L,
                remainingSeconds = 0L,
                statusMessage = "Transfer cancelled"
            )
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
        if (s != null && (s.isCompleted || s.isCancelled)) {
            _transferHistory.update { list ->
                if (list.any { it.id == s.id }) list else listOf(s) + list
            }
        }
        _currentSession.value = null
    }

    fun cleanupAfterFinishedTransfer() {
        if (!isTransferActive()) {
            stopReceiver(clearFinishedSession = true)
            scope.launch(Dispatchers.IO) {
                p2pController.removeGroupIfAny()
            }
        }
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
            lastSeenTimestamp = System.currentTimeMillis() + 120_000L
        )
        discoveredDevicesMap[id] = peer
        publishDiscoveredPeers()
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
        p2pController.unregisterReceiver()
        lanDiscovery.releaseMulticastLock()
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

        try {
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
            val args = arrayOf(file.name, file.sizeBytes.toString())
            context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val resolvedUri = ContentUris.withAppendedId(collection, id)
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
