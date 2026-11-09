package com.example.data

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    const val WIFI_DIRECT_DEFAULT_PORT = 8888
    const val WIFI_DIRECT_GROUP_OWNER_IP = "192.168.49.1"

    /**
     * Resolves the primary local IPv4 address across active network interfaces (Wi-Fi Direct p2p, wlan, etc.)
     * Prioritizes p2p interface for direct device-to-device transfers.
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
                            val name = iface.name.lowercase()
                            // Prioritize Wi-Fi Direct p2p and wlan interfaces
                            if (name.startsWith("p2p") || name.startsWith("wlan") || name.startsWith("ap")) {
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
}
