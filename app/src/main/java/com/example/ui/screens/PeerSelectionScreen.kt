package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.model.DiscoveredDevice
import com.example.model.SharedFile
import com.example.model.UserProfile
import com.example.model.formatBytes
import com.example.ui.components.StyledIconBox
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
    var showManualIpDialog by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }

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

    val scanRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_rotation"
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
            val startRadius = 52.dp.toPx()
            val maxRadius = minOf(size.width, size.height) * 0.46f
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
                    radius = startRadius * 1.6f
                ),
                center = center,
                radius = startRadius * 1.6f
            )

            // Radiating search waves starting directly around the user photo
            waves.forEach { progress ->
                val radius = startRadius + (maxRadius - startRadius) * progress
                val alpha = (1f - progress).coerceIn(0f, 1f) * 0.75f

                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx() * (1f - progress * 0.5f))
                )

                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = alpha * 0.3f),
                    radius = radius * 0.95f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // Top Bar: Back button, files summary badge, and Scan/IP actions
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Manual IP Connect Button
                StyledIconBox(
                    icon = Icons.Rounded.Link,
                    contentDescription = "Connect by IP",
                    size = 44.dp,
                    iconSize = 22.dp,
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    onClick = { showManualIpDialog = true },
                    modifier = Modifier.testTag("manual_ip_connect_button")
                )

                // Rescan Nearby Devices Button
                StyledIconBox(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Scan nearby devices",
                    size = 44.dp,
                    iconSize = 22.dp,
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = Color(0xFF00E5FF),
                    shape = RoundedCornerShape(12.dp),
                    onClick = onRescan,
                    modifier = Modifier
                        .testTag("rescan_nearby_devices_button")
                        .rotate(if (discoveredPeers.isEmpty()) scanRotation else 0f)
                )
            }
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
                .size(104.dp)
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

        // SCANNING STATUS / PEERS FOUND BADGE: 24% BELOW OF CENTER OF THE SCREEN
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = maxHeight * 0.24f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                .border(
                    width = 1.dp,
                    color = if (discoveredPeers.isEmpty()) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color(0xFF10B981).copy(alpha = 0.6f),
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
                text = if (discoveredPeers.isEmpty()) "Scanning for nearby devices..." else "${discoveredPeers.size} nearby device ready • Tap photo to send",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // REAL DISCOVERED PEERS ORBITING AROUND SENDER RADAR (Always clickable & active)
        if (discoveredPeers.isNotEmpty()) {
            val maxRadiusDp = with(density) { (minDim * 0.35f).toDp() }
            val orbitInner = maxRadiusDp * 0.72f
            val orbitOuter = maxRadiusDp * 0.96f

            discoveredPeers.forEachIndexed { index, peer ->
                val isOuter = index % 2 == 1
                val radius = if (isOuter) orbitOuter else orbitInner
                val angleStep = (2.0 * Math.PI) / discoveredPeers.size
                val angle = index * angleStep + (if (isOuter) 0.38 else 0.0)

                val offsetX = (radius.value * cos(angle)).dp
                val offsetY = (radius.value * sin(angle)).dp

                RealPeerAccountBubble(
                    peer = peer,
                    onClick = { onSelectPeer(peer) },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = offsetX, y = offsetY)
                        .zIndex(7f)
                )
            }
        }

        // DEDICATED NEARBY DEVICES BOTTOM SECTION / DOCK
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .zIndex(8f)
        ) {
            if (discoveredPeers.isNotEmpty()) {
                // High-visibility card row showing all discovered nearby devices
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "NEARBY DEVICES",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF10B981),
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = "${discoveredPeers.size} Found",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(discoveredPeers, key = { it.id }) { peer ->
                                NearbyDeviceDockItem(
                                    peer = peer,
                                    onClick = { onSelectPeer(peer) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Sleek scanning guidance card when searching
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
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Devices,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Waiting for receiver device...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Open Cute Share on the other phone and tap 'Receive'. Both devices must be on the same Wi-Fi or Hotspot.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showManualIpDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connect by IP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = onRescan,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rescan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Manual IP Connect Dialog (for Hotspot AP isolation or direct link)
        if (showManualIpDialog) {
            AlertDialog(
                onDismissRequest = { showManualIpDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Connect by IP Address", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Enter receiver device IP address (e.g. 192.168.43.50):",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = manualIpInput,
                            onValueChange = { manualIpInput = it.trim() },
                            placeholder = { Text("192.168.1.xxx") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("manual_ip_input_field")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (manualIpInput.isNotEmpty()) {
                                onAddManualPeer(manualIpInput)
                                showManualIpDialog = false
                                manualIpInput = ""
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("manual_ip_submit_button")
                    ) {
                        Text("Connect")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualIpDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Radar floating account bubble for discovered receiver devices.
 */
@Composable
private fun RealPeerAccountBubble(
    peer: DiscoveredDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 40.dp),
                onClick = onClick
            )
            .padding(6.dp)
            .testTag("peer_account_${peer.id}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .shadow(elevation = 18.dp, shape = CircleShape, spotColor = Color(0xFF00E5FF))
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
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

/**
 * Dedicated dock item for discovered devices at bottom of screen.
 */
@Composable
private fun NearbyDeviceDockItem(
    peer: DiscoveredDevice,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .widthIn(min = 160.dp, max = 220.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("dock_device_${peer.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFF00E5FF), CircleShape)
            ) {
                Image(
                    painter = painterResource(id = peer.avatarRes),
                    contentDescription = peer.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = peer.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = if (peer.supports5Ghz) Color(0xFF00E5FF) else Color(0xFF10B981),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = peer.bandLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Send",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
