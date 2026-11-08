package com.example.data

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtils {

    const val DISCOVERY_PORT = 42889
    const val DEFAULT_TRANSFER_PORT = 42888

    /**
     * Resolves the primary local IPv4 address across active network interfaces (Wi-Fi, AP Hotspot, Ethernet, etc.)
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            val candidates = mutableListOf<String>()

            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotEmpty() && !host.startsWith("127.")) {
                            // Prioritize wlan/ap interfaces
                            if (iface.name.startsWith("wlan") || iface.name.startsWith("ap") || iface.name.startsWith("rndis")) {
                                return host
                            }
                            candidates.add(host)
                        }
                    }
                }
            }
            if (candidates.isNotEmpty()) {
                return candidates.first()
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    /**
     * Collects all possible subnet broadcast addresses plus 255.255.255.255 for reliable UDP multicast
     */
    fun getBroadcastAddresses(): List<InetAddress> {
        val broadcastList = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue

                for (ifaceAddr in iface.interfaceAddresses) {
                    val broadcast = ifaceAddr.broadcast
                    if (broadcast != null && broadcast is Inet4Address && !broadcastList.contains(broadcast)) {
                        broadcastList.add(broadcast)
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val universal = InetAddress.getByName("255.255.255.255")
            if (!broadcastList.contains(universal)) {
                broadcastList.add(universal)
            }
        } catch (_: Exception) {}

        return broadcastList
    }
}
