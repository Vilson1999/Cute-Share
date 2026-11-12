package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

object NetworkUtils {

    const val WIFI_DIRECT_DEFAULT_PORT = 8888
    const val UDP_DISCOVERY_PORT = 8889
    const val REVERSE_HANDSHAKE_PORT = 8890
    const val WIFI_DIRECT_GROUP_OWNER_IP = "192.168.49.1"

    /**
     * Resolves the local IPv4 address bound specifically to a Wi-Fi Direct (`p2p*`) interface.
     */
    fun getP2pLocalIpAddress(): String? = findFirstInterfaceIp { name ->
        name.startsWith("p2p")
    }

    /**
     * Resolves the local IPv4 address bound to standard Wi-Fi or Hotspot interfaces (`wlan*`, `ap*`, `swlan*`).
     */
    fun getWlanLocalIpAddress(): String? = findFirstInterfaceIp { name ->
        name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")
    }

    /**
     * Resolves the primary local IPv4 address across active network interfaces (Wi-Fi Direct p2p, wlan, etc.)
     * Prioritizes p2p interface for direct device-to-device transfers.
     */
    fun getLocalIpAddress(): String {
        getP2pLocalIpAddress()?.let { return it }
        getWlanLocalIpAddress()?.let { return it }
        findFirstInterfaceIp { true }?.let { return it }
        return "127.0.0.1"
    }

    /**
     * Returns all active non-loopback IPv4 addresses on this device so multi-homed
     * devices (connected to both Wi-Fi router and P2P) filter out their own broadcasts.
     */
    fun getAllOwnIpv4Addresses(): Set<String> {
        val result = linkedSetOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                            result.add(host)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * Enumerates directed subnet broadcast addresses on active Wi-Fi / P2P / Hotspot interfaces,
     * plus `192.168.49.255` and `255.255.255.255`.
     */
    fun getBroadcastAddresses(): List<InetAddress> {
        val targets = LinkedHashMap<String, InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                if (!(name.startsWith("p2p") || name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan"))) {
                    continue
                }
                for (ifAddr in iface.interfaceAddresses) {
                    val bcast = ifAddr.broadcast
                    if (bcast is Inet4Address) {
                        val host = bcast.hostAddress
                        if (!host.isNullOrBlank()) {
                            targets[host] = bcast
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        listOf(WIFI_DIRECT_GROUP_OWNER_IP.substringBeforeLast('.') + ".255", "255.255.255.255").forEach { fallback ->
            if (!targets.containsKey(fallback)) {
                try {
                    targets[fallback] = InetAddress.getByName(fallback)
                } catch (_: Exception) {}
            }
        }
        return targets.values.toList()
    }

    fun getSubnetPrefix(ip: String): String? {
        val lastDot = ip.lastIndexOf('.')
        return if (lastDot != -1) ip.substring(0, lastDot + 1) else null
    }

    fun isP2pSubnetIp(ip: String): Boolean = ip.startsWith("192.168.49.")

    fun findP2pNetwork(context: Context): Network? {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            @Suppress("DEPRECATION")
            for (network in cm.allNetworks) {
                val linkProps = cm.getLinkProperties(network) ?: continue
                val ifName = linkProps.interfaceName?.lowercase().orEmpty()
                val hasP2pAddr = linkProps.linkAddresses.any {
                    it.address?.hostAddress?.startsWith("192.168.49.") == true
                }
                if (ifName.startsWith("p2p") || hasP2pAddr) {
                    return network
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun findWlanNetwork(context: Context): Network? {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            @Suppress("DEPRECATION")
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network)
                val linkProps = cm.getLinkProperties(network)
                val ifName = linkProps?.interfaceName?.lowercase().orEmpty()
                val isWifiTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                if ((isWifiTransport || ifName.startsWith("wlan") || ifName.startsWith("ap") || ifName.startsWith("swlan")) &&
                    !ifName.startsWith("p2p")
                ) {
                    return network
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Opens and connects a TCP Socket while explicitly routing traffic over the target interface/Network
     * (`p2p*` for Wi-Fi Direct or `wlan*` for router/hotspot LAN) so Android Cellular Data or VPN
     * never hijacks local P2P/LAN sockets.
     */
    fun connectTcpSocket(
        context: Context,
        host: String,
        port: Int,
        preferP2p: Boolean = isP2pSubnetIp(host),
        timeoutMs: Int = 1500
    ): Socket? {
        val targetIsP2p = preferP2p || isP2pSubnetIp(host)
        val p2pNetwork = if (targetIsP2p) findP2pNetwork(context) else null
        val p2pLocalIp = if (targetIsP2p) getP2pLocalIpAddress() else null
        val wlanNetwork = if (!targetIsP2p) findWlanNetwork(context) else null
        val wlanLocalIp = if (!targetIsP2p) getWlanLocalIpAddress() else null

        val strategies = buildList<() -> Socket> {
            if (targetIsP2p) {
                if (p2pNetwork != null && p2pLocalIp != null) {
                    add {
                        Socket().apply {
                            p2pNetwork.bindSocket(this)
                            bind(InetSocketAddress(InetAddress.getByName(p2pLocalIp), 0))
                        }
                    }
                }
                if (p2pLocalIp != null) {
                    add {
                        Socket().apply {
                            bind(InetSocketAddress(InetAddress.getByName(p2pLocalIp), 0))
                        }
                    }
                }
                if (p2pNetwork != null) {
                    add {
                        Socket().apply {
                            p2pNetwork.bindSocket(this)
                        }
                    }
                }
            } else {
                val targetPrefix = getSubnetPrefix(host)
                val wlanPrefix = wlanLocalIp?.let { getSubnetPrefix(it) }
                if (wlanNetwork != null && wlanLocalIp != null && targetPrefix == wlanPrefix) {
                    add {
                        Socket().apply {
                            wlanNetwork.bindSocket(this)
                            bind(InetSocketAddress(InetAddress.getByName(wlanLocalIp), 0))
                        }
                    }
                }
                if (wlanNetwork != null) {
                    add {
                        Socket().apply {
                            wlanNetwork.bindSocket(this)
                        }
                    }
                }
                if (wlanLocalIp != null && targetPrefix == wlanPrefix) {
                    add {
                        Socket().apply {
                            bind(InetSocketAddress(InetAddress.getByName(wlanLocalIp), 0))
                        }
                    }
                }
            }
            // Final OS-default routing fallback
            add { Socket() }
        }

        val remote = InetSocketAddress(host, port)
        for (factory in strategies) {
            var candidate: Socket? = null
            try {
                candidate = factory().apply {
                    tcpNoDelay = true
                    sendBufferSize = 524288
                    receiveBufferSize = 524288
                }
                candidate.connect(remote, timeoutMs)
                return candidate
            } catch (_: Exception) {
                try { candidate?.close() } catch (_: Exception) {}
            }
        }
        return null
    }

    private inline fun findFirstInterfaceIp(predicate: (String) -> Boolean): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                if (!predicate(name)) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress.orEmpty()
                        if (host.isNotEmpty() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
