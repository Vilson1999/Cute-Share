package com.example.ui.components

import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.HardwareReadinessManager
import com.example.ui.icons.AppIcons

@Composable
fun PermissionsHandler(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onPermissionsResult: (Boolean) -> Unit
) {
    val context = LocalContext.current

    var hasAllPerms by remember(showDialog) { mutableStateOf(HardwareReadinessManager.hasAllPermissions(context)) }
    var hasAllFilesPerm by remember(showDialog) { mutableStateOf(HardwareReadinessManager.hasAllFilesPermission(context)) }
    var hasNearbyWifiPerm by remember(showDialog) { mutableStateOf(HardwareReadinessManager.hasNearbyWifiPermission(context)) }
    var hasBluetoothPerm by remember(showDialog) { mutableStateOf(HardwareReadinessManager.hasBluetoothPermissions(context)) }
    var hasLocPerm by remember(showDialog) { mutableStateOf(HardwareReadinessManager.hasLocationPermission(context)) }

    fun refreshStates() {
        hasAllPerms = HardwareReadinessManager.hasAllPermissions(context)
        hasAllFilesPerm = HardwareReadinessManager.hasAllFilesPermission(context)
        hasNearbyWifiPerm = HardwareReadinessManager.hasNearbyWifiPermission(context)
        hasBluetoothPerm = HardwareReadinessManager.hasBluetoothPermissions(context)
        hasLocPerm = HardwareReadinessManager.hasLocationPermission(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, showDialog) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && showDialog) {
                refreshStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        context.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("has_asked_permissions", true)
        }
        onPermissionsResult(HardwareReadinessManager.hasAllPermissions(context))
        onDismiss()
    }

    fun launchAllFilesSettingsIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !HardwareReadinessManager.hasAllFilesPermission(context)
        ) {
            return try {
                allFilesLauncher.launch(HardwareReadinessManager.getManageAllFilesAccessIntent(context))
                true
            } catch (_: Exception) {
                try {
                    allFilesLauncher.launch(HardwareReadinessManager.getFallbackAllFilesAccessIntent())
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        return false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        context.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("has_asked_permissions", true)
        }

        if (!launchAllFilesSettingsIfNeeded()) {
            onPermissionsResult(HardwareReadinessManager.hasAllPermissions(context))
            onDismiss()
        }
    }

    LaunchedEffect(showDialog, hasAllPerms) {
        if (showDialog && hasAllPerms) {
            onPermissionsResult(true)
            onDismiss()
        }
    }

    if (showDialog && !hasAllPerms) {
        AlertDialog(
            onDismissRequest = {
                context.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE).edit {
                    putBoolean("has_asked_permissions", true)
                }
                onDismiss()
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("permissions_dialog"),
            title = {
                Text(
                    text = "Permissions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (!hasAllFilesPerm) {
                        PermissionItemRow(
                            icon = AppIcons.FolderOpen,
                            iconColor = Color(0xFFF59E0B),
                            title = "All Files Access"
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNearbyWifiPerm) {
                        PermissionItemRow(
                            icon = AppIcons.NearMe,
                            iconColor = Color(0xFFA855F7),
                            title = "Nearby Devices"
                        )
                    }

                    if (!hasBluetoothPerm) {
                        PermissionItemRow(
                            icon = AppIcons.Bluetooth,
                            iconColor = Color(0xFF3B82F6),
                            title = "Bluetooth"
                        )
                    }

                    if (HardwareReadinessManager.isLocationRequired() && !hasLocPerm) {
                        PermissionItemRow(
                            icon = Icons.Rounded.LocationOn,
                            iconColor = Color(0xFFF59E0B),
                            title = "Location"
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        context.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE).edit {
                            putBoolean("has_asked_permissions", true)
                        }
                        val missingRuntime = HardwareReadinessManager.getMissingPermissions(context)
                        if (missingRuntime.isNotEmpty()) {
                            permissionLauncher.launch(missingRuntime.toTypedArray())
                        } else if (!launchAllFilesSettingsIfNeeded()) {
                            onPermissionsResult(HardwareReadinessManager.hasAllPermissions(context))
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("grant_permissions_button")
                ) {
                    Text("Allow", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        context.getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE).edit {
                            putBoolean("has_asked_permissions", true)
                        }
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
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.18f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
