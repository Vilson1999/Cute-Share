package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.icons.AppIcons

enum class Screen(
    val title: String,
    val icon: ImageVector
) {
    ONBOARDING("Profile", Icons.Rounded.Home),
    HOME("Home", Icons.Rounded.Home),
    FILE_PICKER("Share", AppIcons.NearMe),
    PEER_SELECTION("Peers", AppIcons.NearMe),
    RECEIVE_RADAR("Receive", AppIcons.CallReceived),
    TRANSFER("Transfer", AppIcons.SwapHoriz)
}
