package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun PermissionsHandler(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onPermissionsResult: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val permissionsToRequest = buildList {
        // Storage & Media permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // Location for Wi-Fi hotspot & local device radar discovery
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Mark that permissions have been asked once
        context.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("has_asked_permissions", true)
            .apply()

        val anyGranted = results.values.any { it }
        onPermissionsResult(anyGranted)
        onDismiss()
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                context.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("has_asked_permissions", true)
                    .apply()
                onDismiss()
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("permissions_dialog"),
            title = {
                Text(
                    text = "Allow Sharing Permissions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Cute Share needs these permissions to discover nearby phones and transfer files ultra-fast:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    PermissionItemRow(
                        icon = Icons.Rounded.Folder,
                        iconColor = Color(0xFF38BDF8),
                        title = "Storage & Files",
                        desc = "Select and save transferred photos, videos, and files"
                    )

                    PermissionItemRow(
                        icon = Icons.Rounded.Wifi,
                        iconColor = Color(0xFF10B981),
                        title = "Wi-Fi & Local Network",
                        desc = "Direct high-speed wireless transfer up to 50 MB/s"
                    )

                    PermissionItemRow(
                        icon = Icons.Rounded.Bluetooth,
                        iconColor = Color(0xFFA855F7),
                        title = "Bluetooth & Nearby",
                        desc = "Instantly discover and pair with nearby devices"
                    )

                    PermissionItemRow(
                        icon = Icons.Rounded.LocationOn,
                        iconColor = Color(0xFFF59E0B),
                        title = "Location (Android requirement)",
                        desc = "Required by Android OS for Wi-Fi hotspot and radar"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        context.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("has_asked_permissions", true)
                            .apply()
                        permissionLauncher.launch(permissionsToRequest)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("grant_permissions_button")
                ) {
                    Text("Grant Permissions", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        context.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("has_asked_permissions", true)
                            .apply()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("dismiss_permissions_button")
                ) {
                    Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun PermissionItemRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.18f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
