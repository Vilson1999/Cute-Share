package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TransferSession

@Composable
fun RealTimeTransferMonitor(
    session: TransferSession,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = session.overallProgress,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "monitor_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .testTag("real_time_transfer_monitor")
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // TOP ROW: 3 Key Metrics required (Real Time Speed, Left Time, Total File Size)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Real Time Speed at top
                MonitorMetricCard(
                    icon = Icons.Rounded.Speed,
                    title = "SPEED",
                    value = session.formattedSpeed,
                    iconTint = Color(0xFF10B981),
                    iconBg = Color(0xFF10B981).copy(alpha = 0.16f),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Left Time at top
                MonitorMetricCard(
                    icon = Icons.Rounded.Timer,
                    title = "LEFT TIME",
                    value = session.formattedRemainingTime,
                    iconTint = Color(0xFFF59E0B),
                    iconBg = Color(0xFFF59E0B).copy(alpha = 0.16f),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Total File Size at top
                MonitorMetricCard(
                    icon = Icons.Rounded.FolderZip,
                    title = "TOTAL SIZE",
                    value = session.formattedTotalSize,
                    iconTint = Color(0xFF00E5FF),
                    iconBg = Color(0xFF00E5FF).copy(alpha = 0.16f),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Smooth Progress Bar with gradient & shimmer
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${session.formattedTransferredSize} of ${session.formattedTotalSize}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "${(session.overallProgress * 100).toInt()}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Smooth Bar Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    // Filled Progress
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF10B981),
                                        Color(0xFF00E5FF),
                                        Color(0xFF8B5CF6)
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Peer Device Info & Quick Control Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Peer device avatar and name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Image(
                            painter = painterResource(id = session.avatarRes),
                            contentDescription = session.peerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = session.peerName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Direction badge
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (session.isOutgoing) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF00E5FF).copy(alpha = 0.2f)
                                    )
                            ) {
                                Icon(
                                    imageVector = if (session.isOutgoing) Icons.Rounded.NearMe else Icons.AutoMirrored.Rounded.CallReceived,
                                    contentDescription = null,
                                    tint = if (session.isOutgoing) Color(0xFF10B981) else Color(0xFF00E5FF),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            // 5GHz / 2.4GHz Wi-Fi Band badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (session.wifiBand.startsWith("5")) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = session.wifiBand,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (session.wifiBand.startsWith("5")) Color(0xFF0284C7) else Color(0xFF059669)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = when {
                                session.isCompleted -> "All files transferred"
                                session.isCancelled -> "Transfer cancelled"
                                session.isPaused -> "Paused"
                                else -> "${session.files.count { it.status == com.example.model.TransferStatus.COMPLETED }}/${session.files.size} completed"
                            },
                            fontSize = 12.sp,
                            color = when {
                                session.isCompleted -> Color(0xFF10B981)
                                session.isCancelled -> Color(0xFFF43F5E)
                                session.isPaused -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Pause / Resume and Cancel buttons with new design icon backgrounds
                if (!session.isCompleted && !session.isCancelled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pause / Resume Button
                        StyledIconBox(
                            icon = if (session.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (session.isPaused) "Resume" else "Pause",
                            size = 38.dp,
                            iconSize = 20.dp,
                            backgroundColor = if (session.isPaused) Color(0xFF0284C7) else MaterialTheme.colorScheme.surface,
                            iconTint = if (session.isPaused) Color.White else MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            onClick = onTogglePause,
                            modifier = Modifier.testTag("monitor_pause_resume_button")
                        )

                        // Cancel Button
                        StyledIconBox(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Cancel transfer",
                            size = 38.dp,
                            iconSize = 18.dp,
                            backgroundColor = Color(0xFFF43F5E).copy(alpha = 0.15f),
                            iconTint = Color(0xFFF43F5E),
                            borderColor = Color(0xFFF43F5E).copy(alpha = 0.3f),
                            shape = CircleShape,
                            onClick = onCancel,
                            modifier = Modifier.testTag("monitor_cancel_button")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(iconBg)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(12.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = title,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
