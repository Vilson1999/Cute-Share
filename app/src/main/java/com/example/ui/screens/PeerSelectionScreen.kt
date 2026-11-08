package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.model.DiscoveredDevice
import com.example.model.SharedFile
import com.example.model.UserProfile
import com.example.model.formatBytes
import com.example.ui.components.StyledIconBox
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PeerSelectionScreen(
    userProfile: UserProfile,
    filesToShare: List<SharedFile>,
    discoveredPeers: List<DiscoveredDevice> = emptyList(),
    onBackClick: () -> Unit,
    onSelectPeer: (DiscoveredDevice) -> Unit,
    onRescan: () -> Unit = {},
    onAddManualPeer: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalSize = remember(filesToShare) { filesToShare.sumOf { it.sizeBytes } }

    var showHelpTip by remember { mutableStateOf(false) }
    LaunchedEffect(discoveredPeers) {
        if (discoveredPeers.isEmpty()) {
            delay(3500)
            showHelpTip = true
        } else {
            showHelpTip = false
        }
    }

    // Search flow animation radiating from around sharing user photo
    val infiniteTransition = rememberInfiniteTransition(label = "search_flow")

    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "search_wave1"
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, delayMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "search_wave2"
    )

    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, delayMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "search_wave3"
    )

    val wave4 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, delayMillis = 1950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "search_wave4"
    )

    val pulseUserScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "user_pulse"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("peer_selection_screen"),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val minDim = minOf(widthPx, heightPx)

        // Animated Search Flow Canvas radiating outward directly around the center sharing user
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val startRadius = 54.dp.toPx()
            val maxRadius = minOf(size.width, size.height) * 0.48f
            val waves = listOf(wave1, wave2, wave3, wave4)

            // Radiant glow disk directly around user photo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.22f),
                        Color(0xFF10B981).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = startRadius * 1.5f
                ),
                center = center,
                radius = startRadius * 1.5f
            )

            // Radiating search waves starting directly around the user photo
            waves.forEach { progress ->
                val radius = startRadius + (maxRadius - startRadius) * progress
                val alpha = (1f - progress).coerceIn(0f, 1f) * 0.72f

                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx() * (1f - progress * 0.5f))
                )

                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = alpha * 0.25f),
                    radius = radius * 0.95f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // Top Bar: Back button, files summary badge, and Scan Nearby button (No IP address shown)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .zIndex(10f)
        ) {
            StyledIconBox(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                size = 44.dp,
                iconSize = 22.dp,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                iconTint = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(12.dp),
                onClick = onBackClick,
                modifier = Modifier.testTag("peer_selection_back_button")
            )

            // File selection summary badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NearMe,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${filesToShare.size} files • ${formatBytes(totalSize)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Rescan Nearby Devices Button with styled icon background (No IP shown)
            StyledIconBox(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Scan nearby devices",
                size = 44.dp,
                iconSize = 22.dp,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                iconTint = Color(0xFF00E5FF),
                shape = RoundedCornerShape(12.dp),
                onClick = onRescan,
                modifier = Modifier.testTag("rescan_nearby_devices_button")
            )
        }

        // USER NAME: MOVED UP 50% (37.5% UP OF CENTER) - NO IP SHOWN
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -(maxHeight * 0.375f))
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .zIndex(5f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.NearMe,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = userProfile.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // CENTER: Sharing User Profile Photo with radar waves radiating around it
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .size(108.dp)
                .scale(pulseUserScale)
                .border(2.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), CircleShape)
                .padding(6.dp)
                .zIndex(5f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(elevation = 24.dp, shape = CircleShape, spotColor = Color(0xFF00E5FF))
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(3.dp, Color(0xFF00E5FF), CircleShape)
                    .testTag("center_sharing_user_photo")
            ) {
                Image(
                    painter = painterResource(id = userProfile.avatarRes),
                    contentDescription = "My Profile Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // SCANNING STATUS / PEERS FOUND BADGE: 25% BELOW OF CENTER OF THE SCREEN
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = maxHeight * 0.25f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                .border(
                    width = 1.dp,
                    color = if (discoveredPeers.isEmpty()) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color(0xFF10B981).copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (discoveredPeers.isEmpty()) Color(0xFF00E5FF) else Color(0xFF10B981))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (discoveredPeers.isEmpty()) "Scanning for nearby devices..." else "${discoveredPeers.size} nearby device(s) ready • Tap to send",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (discoveredPeers.isEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .clickable { onRescan() }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Rescan",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // REAL DISCOVERED PEERS ORBITING AROUND SENDER (Shows Avatar, Name, 5GHz/2.4GHz Band - NO IP SHOWN)
        if (discoveredPeers.isNotEmpty()) {
            val maxRadiusDp = with(density) { (minDim * 0.38f).toDp() }
            val orbitInner = maxRadiusDp * 0.65f
            val orbitOuter = maxRadiusDp * 0.95f

            discoveredPeers.forEachIndexed { index, peer ->
                val isOuter = index % 2 == 1
                val radius = if (isOuter) orbitOuter else orbitInner
                val angleStep = (2.0 * Math.PI) / discoveredPeers.size
                val angle = index * angleStep + (if (isOuter) 0.32 else 0.0)

                val offsetX = (radius.value * cos(angle)).dp
                val offsetY = (radius.value * sin(angle)).dp

                RealPeerAccountBubble(
                    peer = peer,
                    onClick = { onSelectPeer(peer) },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = offsetX, y = offsetY)
                        .zIndex(6f)
                )
            }
        }

        // Bottom Helper Card if no receiver is visible yet
        AnimatedVisibility(
            visible = showHelpTip && discoveredPeers.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .zIndex(7f)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Receiver phone must open 'Receive' screen with Cute Share running on Wi-Fi or Hotspot.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onRescan() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rescan Nearby Devices", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RealPeerAccountBubble(
    peer: DiscoveredDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 36.dp),
                onClick = onClick
            )
            .padding(6.dp)
            .testTag("peer_account_${peer.id}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(62.dp)
                .shadow(elevation = 16.dp, shape = CircleShape, spotColor = Color(0xFF00E5FF))
                .clip(CircleShape)
                .background(Color(0xFF0F172A))
                .border(2.5.dp, Color(0xFF00E5FF), CircleShape)
                .padding(2.dp)
        ) {
            Image(
                painter = painterResource(id = peer.avatarRes),
                contentDescription = peer.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = peer.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Band Badge: 5 GHz or 2.4 GHz (NO IP ADDRESS SHOWN)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (peer.supports5Ghz) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = if (peer.supports5Ghz) Color(0xFF00E5FF) else Color(0xFF10B981),
                        modifier = Modifier.size(8.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = peer.bandLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (peer.supports5Ghz) Color(0xFF0284C7) else Color(0xFF059669)
                    )
                }
            }
        }
    }
}
