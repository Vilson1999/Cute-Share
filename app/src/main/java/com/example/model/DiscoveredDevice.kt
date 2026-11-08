package com.example.model

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val avatarRes: Int,
    val ip: String,
    val port: Int,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    val isFresh: Boolean
        get() = (System.currentTimeMillis() - lastSeenTimestamp) < 4000L
}
