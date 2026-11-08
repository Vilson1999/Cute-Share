package com.example.model

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val avatarRes: Int,
    val ip: String,
    val port: Int,
    val supports5Ghz: Boolean = true,
    val is5GhzActive: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    val isFresh: Boolean
        get() = (System.currentTimeMillis() - lastSeenTimestamp) < 4000L

    val bandLabel: String
        get() = if (supports5Ghz) "5 GHz" else "2.4 GHz"
}
