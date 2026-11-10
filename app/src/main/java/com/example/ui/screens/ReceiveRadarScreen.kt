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
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.P2PTransferManager
import com.example.model.CartoonAvatars
import com.example.model.UserProfile
import com.example.model.formatBytes
import com.example.ui.components.StyledIconBox
import com.example.ui.components.WaveRadarEffect

@Composable
fun ReceiveRadarScreen(
    userProfile: UserProfile,
    incomingRequest: P2PTransferManager.IncomingTransferRequest? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userAvatarGlow = remember(userProfile.avatarRes) {
        CartoonAvatars.list.find { it.drawableRes == userProfile.avatarRes }?.glowColor ?: Color(0xFF00E5FF)
    }

    // Gentle breathing pulse animation for the central user profile photo
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_breath")
    val avatarPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_pulse"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("receive_radar_screen"),
        contentAlignment = Alignment.Center
    ) {
        val isWide = maxWidth > 600.dp
        val avatarSize = if (isWide) 160.dp else 140.dp
        val outerContainerSize = avatarSize + 24.dp
        val centerPhotoRadiusDp = outerContainerSize / 2

        // Wave Radar Effect originating directly around the center user profile photo
        WaveRadarEffect(
            modifier = Modifier.fillMaxSize(),
            waveColor = userAvatarGlow,
            centerPhotoRadiusDp = centerPhotoRadiusDp
        )

        // Top Header: Back button on the left, Centered title, clean spacer on the right
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Back Button with styled icon background
            StyledIconBox(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                size = 44.dp,
                iconSize = 22.dp,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                iconTint = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(12.dp),
                onClick = onBackClick,
                modifier = Modifier.testTag("receive_back_button")
            )

            Text(
                text = "Ready to Receive",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Balance placeholder to keep title centered
            Spacer(modifier = Modifier.size(44.dp))
        }



        // USER PROFILE PHOTO: AT EXACT CENTER OF SCREEN (with radar waves around it)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .size(outerContainerSize)
                .scale(avatarPulse)
                .border(
                    width = 2.dp,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            userAvatarGlow.copy(alpha = 0.85f),
                            userAvatarGlow.copy(alpha = 0.25f)
                        )
                    ),
                    shape = CircleShape
                )
                .padding(10.dp)
        ) {
            // Central User Profile Photo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(avatarSize)
                    .shadow(
                        elevation = 28.dp,
                        shape = CircleShape,
                        spotColor = userAvatarGlow,
                        ambientColor = userAvatarGlow
                    )
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(3.5.dp, userAvatarGlow, CircleShape)
                    .testTag("center_cute_avatar")
            ) {
                Image(
                    painter = painterResource(id = userProfile.avatarRes),
                    contentDescription = userProfile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // WAITING FOR SENDER TEXT: 25% DOWN OF CENTER OF THE SCREEN
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = maxHeight * 0.25f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(userAvatarGlow)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Waiting for sender...",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Real Incoming Transfer Alert Card (appears smoothly when sender initiates connection)
        AnimatedVisibility(
            visible = incomingRequest != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            if (incomingRequest != null) {
                val req = incomingRequest
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 10.dp,
                    shadowElevation = 20.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color(0xFF00E5FF), CircleShape)
                        ) {
                            Image(
                                painter = painterResource(id = req.sender.avatarRes),
                                contentDescription = req.sender.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = req.sender.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Wants to share ${req.files.size} file(s) (${formatBytes(req.totalSize)})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Decline Button with styled icon background
                        StyledIconBox(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Decline",
                            size = 40.dp,
                            iconSize = 20.dp,
                            backgroundColor = Color(0xFF334155),
                            iconTint = Color(0xFFF43F5E),
                            borderColor = Color(0xFFF43F5E).copy(alpha = 0.3f),
                            shape = CircleShape,
                            onClick = req.declineAction,
                            modifier = Modifier.testTag("decline_transfer_button")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Accept Button with styled icon background
                        StyledIconBox(
                            icon = Icons.Rounded.Check,
                            contentDescription = "Accept",
                            size = 44.dp,
                            iconSize = 22.dp,
                            backgroundColor = Color(0xFF00E5FF),
                            iconTint = Color(0xFF0F172A),
                            borderColor = Color.White.copy(alpha = 0.4f),
                            shape = CircleShape,
                            elevation = 6.dp,
                            onClick = req.acceptAction,
                            modifier = Modifier.testTag("accept_transfer_button")
                        )
                    }
                }
            }
        }
    }
}
