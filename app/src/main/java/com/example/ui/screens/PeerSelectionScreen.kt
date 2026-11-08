package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.model.CartoonAvatars
import com.example.model.CuteAvatar
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
    onBackClick: () -> Unit,
    onSelectPeer: (CuteAvatar) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSize = remember(filesToShare) { filesToShare.sumOf { it.sizeBytes } }

    // Search flow animation around sharing user photo
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

    val peers = remember { CartoonAvatars.list }

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

        // Animated Search Flow Canvas radiating outward from the center sharing user
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = minOf(size.width, size.height) * 0.46f

            val waves = listOf(wave1, wave2, wave3, wave4)

            // Radiant glow disk
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.18f),
                        Color(0xFF10B981).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * 0.7f
                ),
                center = center,
                radius = maxRadius * 0.7f
            )

            // Radiating search waves
            waves.forEach { progress ->
                val radius = maxRadius * progress
                val alpha = (1f - progress).coerceIn(0f, 1f) * 0.7f

                // Outer wave line
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx() * (1f - progress * 0.5f))
                )

                // Concentric faint glow ring
                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = alpha * 0.25f),
                    radius = radius * 0.95f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // Clean Top Bar: Back button & sharing summary
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
        }

        // CENTER: Sharing User Profile Photo with radar search flow
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(5f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(108.dp)
                    .scale(pulseUserScale)
                    .border(2.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), CircleShape)
                    .padding(6.dp)
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

            Spacer(modifier = Modifier.height(8.dp))

            // User Name label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = userProfile.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Surrounding: All Cute Share Receive Accounts User Photo Icons at organic positions around sharing user
        val maxRadiusDp = with(density) { (minDim * 0.38f).toDp() }
        val orbitInner = maxRadiusDp * 0.60f
        val orbitOuter = maxRadiusDp * 0.95f

        peers.forEachIndexed { index, peer ->
            val isOuter = index % 2 == 1
            val radius = if (isOuter) orbitOuter else orbitInner
            val angleStep = (2.0 * Math.PI) / peers.size
            val angle = index * angleStep + (if (isOuter) 0.32 else 0.0)

            val offsetX = (radius.value * cos(angle)).dp
            val offsetY = (radius.value * sin(angle)).dp

            PeerAccountBubble(
                peer = peer,
                onClick = { onSelectPeer(peer) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = offsetX, y = offsetY)
                    .zIndex(6f)
            )
        }
    }
}

@Composable
private fun PeerAccountBubble(
    peer: CuteAvatar,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 32.dp),
                onClick = onClick
            )
            .padding(4.dp)
            .testTag("peer_account_${peer.id}")
    ) {
        // Cute 3D Cartoon Avatar Bubble
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .shadow(elevation = 12.dp, shape = CircleShape, spotColor = peer.glowColor)
                .clip(CircleShape)
                .background(Color(0xFF0F172A))
                .border(2.5.dp, peer.glowColor, CircleShape)
                .padding(2.dp)
        ) {
            Image(
                painter = painterResource(id = peer.drawableRes),
                contentDescription = peer.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Cute Peer Name Label
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
        ) {
            Text(
                text = peer.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                maxLines = 1
            )
        }
    }
}
