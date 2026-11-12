package com.example.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.model.CartoonAvatars
import com.example.model.DiscoveredDevice
import com.example.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Coordinates same-network discovery & multi-interface beacons alongside Wi-Fi Direct:
 * 1) Android NsdManager (`_cuteshare._tcp.`) mDNS registration & serialized resolution
 * 2) WifiManager.MulticastLock so Wi-Fi power saving doesn't drop multicast/broadcast beacons
 * 3) UDP Broadcast Beacon across all active wlan/ap/p2p broadcast addresses
 * 4) Fast parallel local subnet TCP `CUTESHARE_PING` probe
 */
class LanDiscoveryHelper(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val nsdManager: NsdManager? = try {
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    } catch (_: Exception) {
        null
    }

    private var multicastLock: WifiManager.MulticastLock? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile
    private var registeredNsdName: String? = null

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var nsdResolveJob: Job? = null
    private var resolveChannel: Channel<NsdServiceInfo>? = null

    private var receiverBeaconJob: Job? = null
    private var udpDiscoveryJob: Job? = null

    @Synchronized
    fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                val wifiManager =
                    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifiManager?.createMulticastLock("CuteShareMulticast")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }

    fun startAdvertising(
        profile: UserProfile,
        listeningPort: Int,
        getP2pMac: () -> String?,
        sanitizeField: (String) -> String
    ) {
        stopAdvertising()
        acquireMulticastLock()

        val safeName = sanitizeField(profile.name).ifBlank { "Cute Friend" }
        val avatarIdx = CartoonAvatars.avatarIndexOf(profile.avatarRes)
        val instanceSuffix = UUID.randomUUID().toString().take(4)
        val desiredNsdName = "CuteShare_${safeName.take(24)}_$instanceSuffix"

        // 1. NsdManager mDNS Registration
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = desiredNsdName
                serviceType = NSD_SERVICE_TYPE
                port = listeningPort
                setAttribute("name", safeName)
                setAttribute("avatar", profile.avatarRes.toString())
                setAttribute("avatarIdx", avatarIdx.toString())
                setAttribute("port", listeningPort.toString())
                getP2pMac()?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }?.let {
                    setAttribute("mac", it)
                }
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    registeredNsdName = info.serviceName
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    registeredNsdName = null
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    registeredNsdName = null
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            registrationListener = listener
            registeredNsdName = desiredNsdName
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {}

        // 2. Multi-interface UDP Beacon Loop
        receiverBeaconJob = scope.launch(Dispatchers.IO) {
            var udpSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket().apply { broadcast = true }
                while (isActive) {
                    val myIp = NetworkUtils.getWlanLocalIpAddress() ?: NetworkUtils.getLocalIpAddress()
                    val macHint = getP2pMac()?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }.orEmpty()
                    val payload =
                        "CUTESHARE_BEACON|$safeName|${profile.avatarRes}|$listeningPort|$myIp|$avatarIdx|$macHint"
                    val bytes = payload.toByteArray(Charsets.UTF_8)
                    val targets = NetworkUtils.getBroadcastAddresses()
                    for (bcast in targets) {
                        try {
                            val packet = DatagramPacket(
                                bytes,
                                bytes.size,
                                bcast,
                                NetworkUtils.UDP_DISCOVERY_PORT
                            )
                            udpSocket.send(packet)
                        } catch (_: Exception) {}
                    }
                    delay(1000L)
                }
            } catch (_: Exception) {
            } finally {
                try { udpSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    fun stopAdvertising() {
        receiverBeaconJob?.cancel()
        receiverBeaconJob = null

        registrationListener?.let { listener ->
            try {
                nsdManager?.unregisterService(listener)
            } catch (_: Exception) {}
        }
        registrationListener = null
        registeredNsdName = null
    }

    fun startDiscovery(
        onPeerDiscovered: (DiscoveredDevice) -> Unit
    ) {
        stopDiscovery()
        acquireMulticastLock()

        // 1. NsdManager Discovery with serialized Resolve queue
        val queue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        resolveChannel = queue

        nsdResolveJob = scope.launch(Dispatchers.IO) {
            val manager = nsdManager ?: return@launch
            for (service in queue) {
                if (!isActive) break
                val resolved = withTimeoutOrNull(2500L) {
                    suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                        try {
                            @Suppress("DEPRECATION")
                            manager.resolveService(service, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                    if (cont.isActive) cont.resume(null)
                                }

                                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                    if (cont.isActive) cont.resume(serviceInfo)
                                }
                            })
                        } catch (_: Exception) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                if (resolved != null) {
                    val hostIp = extractIpv4Host(resolved)
                    if (!hostIp.isNullOrBlank() && hostIp !in NetworkUtils.getAllOwnIpv4Addresses()) {
                        val attrs = resolved.attributes
                        val name = attrString(attrs, "name")
                            ?: WifiP2pController.extractCuteShareNameFromInstance(resolved.serviceName)
                                ?.substringBeforeLast('_')
                            ?: "Cute Friend"
                        val avatarRaw = attrString(attrs, "avatar")
                        val avatarIdx = attrString(attrs, "avatarIdx")
                        val avatarRes = CartoonAvatars.resolveAvatarRes(avatarRaw, avatarIdx, name)
                        val port = resolved.port.takeIf { it > 0 }
                            ?: attrString(attrs, "port")?.toIntOrNull()
                            ?: NetworkUtils.WIFI_DIRECT_DEFAULT_PORT
                        val mac = attrString(attrs, "mac")?.takeIf { it.contains(":") }
                        val id = "nsd_${hostIp.replace('.', '_')}"
                        onPeerDiscovered(
                            DiscoveredDevice(
                                id = id,
                                name = name,
                                avatarRes = avatarRes,
                                ip = hostIp,
                                port = port,
                                p2pMacAddress = mac,
                                supports5Ghz = true,
                                is5GhzActive = true,
                                lastSeenTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }

        try {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    val myName = registeredNsdName
                    if (myName != null && service.serviceName == myName) return
                    if (service.serviceName.startsWith("CuteShare_", ignoreCase = true)) {
                        queue.trySend(service)
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    try {
                        nsdManager?.stopServiceDiscovery(this)
                    } catch (_: Exception) {}
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    try {
                        nsdManager?.stopServiceDiscovery(this)
                    } catch (_: Exception) {}
                }
            }
            discoveryListener = listener
            nsdManager?.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {}

        // 2. UDP Beacon Listener
        udpDiscoveryJob = scope.launch(Dispatchers.IO) {
            var udpSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(NetworkUtils.UDP_DISCOVERY_PORT))
                    soTimeout = 2500
                }
                val buffer = ByteArray(1024)
                while (isActive && !udpSocket.isClosed) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        parseBeaconPayload(
                            payload = text,
                            packetSourceIp = packet.address.hostAddress.orEmpty(),
                            ownIps = NetworkUtils.getAllOwnIpv4Addresses()
                        )?.let(onPeerDiscovered)
                    } catch (_: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { udpSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    fun stopDiscovery() {
        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = null

        nsdResolveJob?.cancel()
        nsdResolveJob = null
        resolveChannel?.close()
        resolveChannel = null

        discoveryListener?.let { listener ->
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) {}
        }
        discoveryListener = null
    }

    /**
     * Fast parallel local subnet TCP `CUTESHARE_PING` scan (covers P2P GO `192.168.49.1`,
     * Hotspot gateway `192.168.43.1`, and neighboring WLAN/P2P hosts in <600ms).
     */
    suspend fun scanLocalSubnetForReceivers(
        onPeerFound: (DiscoveredDevice) -> Unit
    ) = coroutineScope {
        val ownIps = NetworkUtils.getAllOwnIpv4Addresses()
        val candidateIps = linkedSetOf(
            NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP,
            "192.168.43.1"
        )
        for (ownIp in ownIps) {
            val prefix = NetworkUtils.getSubnetPrefix(ownIp) ?: continue
            val host = ownIp.substringAfterLast('.', "0").toIntOrNull() ?: 0
            candidateIps.add("${prefix}1")
            for (delta in 1..10) {
                val up = host + delta
                val down = host - delta
                if (up in 2..254) candidateIps.add("$prefix$up")
                if (down in 2..254) candidateIps.add("$prefix$down")
            }
        }

        val semaphore = Semaphore(10)
        val targets = candidateIps.filter { it !in ownIps }
        targets.map { targetIp ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        val s = NetworkUtils.connectTcpSocket(
                            context = context,
                            host = targetIp,
                            port = NetworkUtils.WIFI_DIRECT_DEFAULT_PORT,
                            preferP2p = NetworkUtils.isP2pSubnetIp(targetIp),
                            timeoutMs = 240
                        ) ?: return@withPermit
                        s.use { sock ->
                            sock.soTimeout = 500
                            val out = BufferedOutputStream(sock.getOutputStream())
                            val inStream = BufferedInputStream(sock.getInputStream())
                            out.write("CUTESHARE_PING\n".toByteArray(Charsets.UTF_8))
                            out.flush()
                            val resp = readAsciiLine(inStream, maxLen = 256)
                            parsePongPayload(resp, targetIp)?.let(onPeerFound)
                        }
                    } catch (_: Exception) {}
                }
            }
        }.awaitAll()
    }

    private fun extractIpv4Host(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val v4 = info.hostAddresses.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            if (v4 != null) return v4.hostAddress
        }
        @Suppress("DEPRECATION")
        return info.host?.hostAddress
    }

    private fun attrString(attrs: Map<String, ByteArray>?, key: String): String? {
        val bytes = attrs?.get(key) ?: return null
        return String(bytes, Charsets.UTF_8).trim().ifEmpty { null }
    }

    companion object {
        const val NSD_SERVICE_TYPE = "_cuteshare._tcp."

        fun parseBeaconPayload(
            payload: String,
            packetSourceIp: String,
            ownIps: Set<String>
        ): DiscoveredDevice? {
            if (!payload.startsWith("CUTESHARE_BEACON|")) return null
            val parts = payload.split("|")
            if (parts.size < 4) return null
            val senderIp = packetSourceIp.ifBlank { parts.getOrNull(4).orEmpty() }
            if (senderIp.isBlank() || senderIp.startsWith("127.") || senderIp in ownIps) return null

            val cuteName = parts[1].ifBlank { "Cute Friend" }
            val avatarRes = CartoonAvatars.resolveAvatarRes(
                rawValue = parts[2],
                indexHint = parts.getOrNull(5),
                fallbackSeed = cuteName
            )
            val port = parts[3].toIntOrNull() ?: NetworkUtils.WIFI_DIRECT_DEFAULT_PORT
            val macHint = parts.getOrNull(6)?.takeIf { it.contains(":") }
            val id = "udp_${senderIp.replace('.', '_')}"
            return DiscoveredDevice(
                id = id,
                name = cuteName,
                avatarRes = avatarRes,
                ip = senderIp,
                port = port,
                p2pMacAddress = macHint,
                supports5Ghz = true,
                is5GhzActive = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        }

        fun parsePongPayload(resp: String, targetIp: String): DiscoveredDevice? {
            if (!resp.startsWith("CUTESHARE_PONG|")) return null
            val parts = resp.split("|")
            if (parts.size < 4) return null
            val name = parts[1].ifBlank { "Cute Friend" }
            val avatarRes = CartoonAvatars.resolveAvatarRes(
                rawValue = parts[2],
                indexHint = parts.getOrNull(5),
                fallbackSeed = name
            )
            val port = parts[3].toIntOrNull() ?: NetworkUtils.WIFI_DIRECT_DEFAULT_PORT
            val macHint = parts.getOrNull(6)?.takeIf { it.contains(":") }
            val id = "lan_${targetIp.replace('.', '_')}"
            return DiscoveredDevice(
                id = id,
                name = name,
                avatarRes = avatarRes,
                ip = targetIp,
                port = port,
                p2pMacAddress = macHint,
                supports5Ghz = true,
                is5GhzActive = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        }

        private fun readAsciiLine(input: java.io.InputStream, maxLen: Int = 256): String {
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
}
