package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TransferFileItem
import com.example.model.TransferSession
import com.example.model.TransferStatus
import com.example.ui.components.RealTimeTransferMonitor
import com.example.ui.components.StyledIconBox
import com.example.ui.icons.AppIcons

@Composable
fun TransferScreen(
    session: TransferSession?,
    transferHistory: List<TransferSession> = emptyList(),
    onBackClick: () -> Unit,
    onToggleSessionPause: () -> Unit,
    onCancelSession: () -> Unit,
    onToggleFilePause: (String) -> Unit,
    onCancelFile: (String) -> Unit,
    onShareMoreClick: () -> Unit = {},
    onRetryTransfer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("transfer_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter)
        ) {
            // Clean Top Bar with Back Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
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
                    modifier = Modifier.testTag("transfer_back_button")
                )

                // Title Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (session != null) "Active Transfer" else "Transfer History",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Balance placeholder space
                Spacer(modifier = Modifier.size(44.dp))
            }

            if (session != null) {
                // REAL-TIME TRANSFER MONITOR COMPONENT AT TOP
                // (Shows real-time speed, left time, total file size at top, and smooth progress bar)
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    RealTimeTransferMonitor(
                        session = session,
                        onTogglePause = onToggleSessionPause,
                        onCancel = onCancelSession,
                        onRetry = onRetryTransfer
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // List header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Sharing Files (${session.files.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // SHOW LIST OF ALL SHARING FILES with Pause/Resume and Cancel icons
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("sharing_files_list")
                ) {
                    items(session.files, key = { it.id }) { fileItem ->
                        SharingFileListItem(
                            fileItem = fileItem,
                            onTogglePause = { onToggleFilePause(fileItem.id) },
                            onCancel = { onCancelFile(fileItem.id) }
                        )
                    }
                }
            } else {
                // When no active session, show past transfer history or clean empty state
                if (transferHistory.isNotEmpty()) {
                    val pastSession = transferHistory.first()
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        RealTimeTransferMonitor(
                            session = pastSession,
                            onTogglePause = {},
                            onCancel = {}
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Recently Transferred Files",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(pastSession.files, key = { it.id }) { fileItem ->
                            SharingFileListItem(
                                fileItem = fileItem,
                                onTogglePause = {},
                                onCancel = {}
                            )
                        }
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Pill Button (Share More) at bottom center of the screen
        // When scroll, the pill icon hides!
        if (session != null) {
            AnimatedVisibility(
                visible = !isScrolling,
                enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Surface(
                    onClick = onShareMoreClick,
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF10B981),
                    contentColor = Color.White,
                    shadowElevation = 12.dp,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier.testTag("share_more_pill_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 13.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Share More",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Share More",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharingFileListItem(
    fileItem: TransferFileItem,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = fileItem.progress,
        animationSpec = tween(durationMillis = 200),
        label = "file_progress_anim"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
            .testTag("sharing_file_item_${fileItem.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Category icon with dedicated container background
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(fileItem.category.containerColor)
                ) {
                    Icon(
                        imageVector = fileItem.category.icon,
                        contentDescription = fileItem.category.title,
                        tint = fileItem.category.contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileItem.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${fileItem.formattedTransferred} of ${fileItem.formattedSize}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • ${(fileItem.progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (fileItem.status) {
                                TransferStatus.COMPLETED -> Color(0xFF10B981)
                                TransferStatus.CANCELLED -> Color(0xFFF43F5E)
                                TransferStatus.PAUSED -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Pause / Resume and Cancel buttons in EVERY share file item
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isPaused = fileItem.status == TransferStatus.PAUSED
                    val isCompleted = fileItem.status == TransferStatus.COMPLETED

                    // Pause / Resume Icon Button on EVERY file
                    if (!isCompleted) {
                        StyledIconBox(
                            icon = if (isPaused) Icons.Rounded.PlayArrow else AppIcons.Pause,
                            contentDescription = if (isPaused) "Resume file" else "Pause file",
                            size = 38.dp,
                            iconSize = 20.dp,
                            backgroundColor = if (isPaused) Color(0xFF0284C7) else MaterialTheme.colorScheme.surfaceVariant,
                            iconTint = if (isPaused) Color.White else MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            onClick = onTogglePause,
                            modifier = Modifier.testTag("file_pause_button_${fileItem.id}")
                        )
                    } else {
                        // Completed indicator with styled background
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.18f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Completed",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Cancel Icon Button on EVERY file
                    StyledIconBox(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Cancel file",
                        size = 38.dp,
                        iconSize = 18.dp,
                        backgroundColor = Color(0xFFF43F5E).copy(alpha = 0.15f),
                        iconTint = Color(0xFFF43F5E),
                        borderColor = Color(0xFFF43F5E).copy(alpha = 0.35f),
                        shape = CircleShape,
                        onClick = onCancel,
                        modifier = Modifier.testTag("file_cancel_button_${fileItem.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Smooth Individual File Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(
                            when (fileItem.status) {
                                TransferStatus.COMPLETED -> Color(0xFF10B981)
                                TransferStatus.CANCELLED -> Color(0xFFF43F5E)
                                TransferStatus.PAUSED -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                )
            }
        }
    }
}
