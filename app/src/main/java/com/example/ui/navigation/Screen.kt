package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val title: String,
    val icon: ImageVector
) {
    ONBOARDING("Profile", Icons.Rounded.Home),
    HOME("Home", Icons.Rounded.Home),
    FILE_PICKER("Share", Icons.Rounded.NearMe),
    PEER_SELECTION("Peers", Icons.Rounded.NearMe),
    RECEIVE_RADAR("Receive", Icons.AutoMirrored.Rounded.CallReceived),
    TRANSFER("Transfer", Icons.Rounded.SwapHoriz)
}
