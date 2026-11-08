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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.components.StyledIconBox
import com.example.ui.components.WaveRadarEffect
import kotlinx.coroutines.delay

@Composable
fun ReceiveRadarScreen(
    onBackClick: () -> Unit,
    onAcceptTransfer: (List<SharedFile>, CuteAvatar) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current avatar index out of 10 cartoon avatars
    var currentAvatarIndex by remember { mutableIntStateOf(0) }
    val currentAvatar = CartoonAvatars.getAvatar(currentAvatarIndex)

    var incomingTransferPrompt by remember { mutableStateOf(false) }
    var senderAvatarIndex by remember { mutableIntStateOf(1) }

    // Auto-discover incoming transfer simulation
    LaunchedEffect(currentAvatarIndex) {
        delay(2400)
        senderAvatarIndex = (currentAvatarIndex + 1) % CartoonAvatars.list.size
        incomingTransferPrompt = true
    }

    val senderAvatar = CartoonAvatars.getAvatar(senderAvatarIndex)

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

        // Continuous Wave Radar Effect surrounding the center cartoon avatar
        WaveRadarEffect(
            modifier = Modifier.fillMaxSize(),
            waveColor = currentAvatar.glowColor
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

                // Balance placeholder space
                Spacer(modifier = Modifier.size(44.dp))
            }
        }

        // Center: Big Cute 3D Cartoon Icon with wave effect surrounding
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
                                currentAvatar.glowColor.copy(alpha = 0.8f),
                                currentAvatar.glowColor.copy(alpha = 0.2f)
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
                            spotColor = currentAvatar.glowColor,
                            ambientColor = currentAvatar.glowColor
                        )
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(3.dp, currentAvatar.glowColor, CircleShape)
                        .clickable {
                            currentAvatarIndex = (currentAvatarIndex + 1) % CartoonAvatars.list.size
                        }
                        .testTag("center_cute_avatar")
                ) {
                    Image(
                        painter = painterResource(id = currentAvatar.drawableRes),
                        contentDescription = currentAvatar.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Minimalist device name pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .border(1.dp, currentAvatar.glowColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(currentAvatar.glowColor.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Smartphone,
                        contentDescription = "Device",
                        tint = currentAvatar.glowColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentAvatar.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
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
                    .widthIn(max = 500.dp)
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
                            text = "3 files • 424 MB",
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
                                    id = "in_1",
                                    name = "Drone_Cinematic_4K_Reel.mp4",
                                    sizeBytes = 432_013_312L,
                                    formattedSize = "412 MB",
                                    category = FileCategory.VIDEO,
                                    extension = "MP4",
                                    lastModified = "Incoming"
                                ),
                                SharedFile(
                                    id = "in_2",
                                    name = "Quarterly_Financial_Report.pdf",
                                    sizeBytes = 13_002_342L,
                                    formattedSize = "12.4 MB",
                                    category = FileCategory.DOCUMENTS,
                                    extension = "PDF",
                                    lastModified = "Incoming"
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
