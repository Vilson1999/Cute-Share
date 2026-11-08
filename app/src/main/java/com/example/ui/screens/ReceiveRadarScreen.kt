package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CartoonAvatars
import com.example.model.CuteAvatar
import com.example.model.FileCategory
import com.example.model.SharedFile
import com.example.model.UserProfile
import com.example.ui.components.StyledIconBox
import com.example.ui.components.WaveRadarEffect
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun ReceiveRadarScreen(
    userProfile: UserProfile,
    onBackClick: () -> Unit,
    onAcceptTransfer: (List<SharedFile>, CuteAvatar) -> Unit,
    modifier: Modifier = Modifier
) {
    // Selected User Avatar & Glow color from current profile (not cosmic bunny)
    val userAvatarGlow = remember(userProfile.avatarRes) {
        CartoonAvatars.list.find { it.drawableRes == userProfile.avatarRes }?.glowColor ?: Color(0xFF00E5FF)
    }

    var incomingTransferPrompt by remember { mutableStateOf(false) }
    val senderAvatar = remember(userProfile.avatarRes) {
        CartoonAvatars.list.firstOrNull { it.drawableRes != userProfile.avatarRes } ?: CartoonAvatars.list.first()
    }

    // Auto-discover incoming transfer simulation
    LaunchedEffect(Unit) {
        delay(2500)
        incomingTransferPrompt = true
    }

    val avatarScale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "avatar_scale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("receive_radar_screen"),
        contentAlignment = Alignment.Center
    ) {
        val isWide = maxWidth > 600.dp
        val centerAvatarSize = if (isWide) 160.dp else 138.dp

        // Continuous Wave Radar Effect surrounding the user's avatar
        WaveRadarEffect(
            modifier = Modifier.fillMaxSize(),
            waveColor = userAvatarGlow
        )

        // Top Header
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
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
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Balance placeholder space
                Spacer(modifier = Modifier.size(44.dp))
            }
        }

        // Center: User's Profile Photo & Name with wave effect surrounding
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(20.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(centerAvatarSize + 20.dp)
                    .border(
                        width = 2.dp,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                userAvatarGlow.copy(alpha = 0.8f),
                                userAvatarGlow.copy(alpha = 0.2f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .padding(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(centerAvatarSize)
                        .scale(avatarScale)
                        .shadow(
                            elevation = 28.dp,
                            shape = CircleShape,
                            spotColor = userAvatarGlow,
                            ambientColor = userAvatarGlow
                        )
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(3.dp, userAvatarGlow, CircleShape)
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

            Spacer(modifier = Modifier.height(14.dp))

            // User's Device / Display name pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .border(1.dp, userAvatarGlow.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(userAvatarGlow.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Smartphone,
                        contentDescription = "Device",
                        tint = userAvatarGlow,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = userProfile.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Visible to nearby devices",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Incoming transfer alert card
        AnimatedVisibility(
            visible = incomingTransferPrompt,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 10.dp,
                shadowElevation = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .border(1.5.dp, senderAvatar.glowColor.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
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
                            .border(2.dp, senderAvatar.glowColor, CircleShape)
                    ) {
                        Image(
                            painter = painterResource(id = senderAvatar.drawableRes),
                            contentDescription = senderAvatar.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = senderAvatar.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Incoming Transfer",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    StyledIconBox(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Decline",
                        size = 40.dp,
                        iconSize = 20.dp,
                        backgroundColor = Color(0xFF334155),
                        iconTint = Color(0xFFF43F5E),
                        borderColor = Color(0xFFF43F5E).copy(alpha = 0.3f),
                        shape = CircleShape,
                        onClick = { incomingTransferPrompt = false },
                        modifier = Modifier.testTag("decline_transfer_button")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

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
                        onClick = {
                            incomingTransferPrompt = false
                            val incomingFiles = listOf(
                                SharedFile(
                                    id = UUID.randomUUID().toString(),
                                    name = "Received_Package_${System.currentTimeMillis() % 1000}.bin",
                                    sizeBytes = 25_000_000L,
                                    formattedSize = "25 MB",
                                    category = FileCategory.STORAGE,
                                    extension = "BIN",
                                    lastModified = "Just now"
                                )
                            )
                            onAcceptTransfer(incomingFiles, senderAvatar)
                        },
                        modifier = Modifier.testTag("accept_transfer_button")
                    )
                }
            }
        }
    }
}
