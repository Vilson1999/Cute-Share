package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserProfile
import com.example.ui.components.StyledIconBox
import com.example.ui.theme.ThemeMode

@Composable
fun HomeScreen(
    userProfile: UserProfile,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onOpenProfile: () -> Unit,
    onShareClick: () -> Unit,
    onReceiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("home_screen"),
        contentAlignment = Alignment.Center
    ) {
        val isWide = maxWidth > 600.dp
        val buttonSize = if (isWide) 160.dp else 138.dp
        val iconSize = if (isWide) 64.dp else 56.dp

        // Ambient radial background glow
        Box(
            modifier = Modifier
                .size(340.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF10B981).copy(alpha = 0.08f),
                            Color(0xFF00E5FF).copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Top Header: App Logo (Left) and Theme Switcher + User Profile Icon (Right)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Clean Logo / App Name badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Text(
                    text = "Cute Share",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Top Right Corner Actions: Theme Switcher & User Profile Icon Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Theme Switcher Button
                StyledIconBox(
                    icon = when (themeMode) {
                        ThemeMode.SYSTEM -> Icons.Rounded.SettingsBrightness
                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                    },
                    contentDescription = "Switch theme: ${themeMode.title}",
                    size = 42.dp,
                    iconSize = 22.dp,
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(12.dp),
                    onClick = onToggleTheme,
                    modifier = Modifier.testTag("theme_switcher_button")
                )

                // User Profile Icon Button on home top right corner to change or reset user profile
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, Color(0xFF00E5FF), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = onOpenProfile
                        )
                        .testTag("home_user_profile_button")
                ) {
                    Image(
                        painter = painterResource(id = userProfile.avatarRes),
                        contentDescription = "Change or Reset Profile (${userProfile.name})",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }
        }

        // Center 2 Big Circle Buttons: Green for Share, Blue for Receive
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(24.dp)
        ) {
            if (isWide) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BigCircleShareButton(
                        size = buttonSize,
                        iconSize = iconSize,
                        scale = pulseScale,
                        onClick = onShareClick
                    )
                    BigCircleReceiveButton(
                        size = buttonSize,
                        iconSize = iconSize,
                        scale = pulseScale,
                        onClick = onReceiveClick
                    )
                }
            } else {
                BigCircleShareButton(
                    size = buttonSize,
                    iconSize = iconSize,
                    scale = pulseScale,
                    onClick = onShareClick
                )

                Spacer(modifier = Modifier.height(44.dp))

                BigCircleReceiveButton(
                    size = buttonSize,
                    iconSize = iconSize,
                    scale = pulseScale,
                    onClick = onReceiveClick
                )
            }
        }
    }
}

@Composable
private fun BigCircleShareButton(
    size: Dp,
    iconSize: Dp,
    scale: Float,
    onClick: () -> Unit
) {
    // Outer ambient glowing ring for Share (Green)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size + 24.dp)
            .scale(scale)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF10B981).copy(alpha = 0.5f),
                        Color(0xFF059669).copy(alpha = 0.15f)
                    )
                ),
                shape = CircleShape
            )
            .padding(12.dp)
    ) {
        // Green Gradient Share Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .shadow(elevation = 20.dp, shape = CircleShape, spotColor = Color(0xFF10B981))
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF34D399),
                            Color(0xFF10B981),
                            Color(0xFF059669)
                        )
                    )
                )
                .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.White),
                    onClick = onClick
                )
                .testTag("home_share_button")
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize + 22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.NearMe,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun BigCircleReceiveButton(
    size: Dp,
    iconSize: Dp,
    scale: Float,
    onClick: () -> Unit
) {
    // Outer ambient glowing ring for Receive (Blue)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size + 24.dp)
            .scale(scale)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.5f),
                        Color(0xFF0284C7).copy(alpha = 0.15f)
                    )
                ),
                shape = CircleShape
            )
            .padding(12.dp)
    ) {
        // Blue Gradient Receive Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .shadow(elevation = 20.dp, shape = CircleShape, spotColor = Color(0xFF0284C7))
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF38BDF8),
                            Color(0xFF0284C7),
                            Color(0xFF0369A1)
                        )
                    )
                )
                .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.White),
                    onClick = onClick
                )
                .testTag("home_receive_button")
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize + 22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.CallReceived,
                    contentDescription = "Receive",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
