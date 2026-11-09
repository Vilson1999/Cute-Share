package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.data.HardwareReadinessManager

@Composable
fun HardwareReadinessDialog(
    actionName: String = "Share",
    onAllReady: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var isWifiOn by remember { mutableStateOf(HardwareReadinessManager.isWifiEnabled(context)) }
    var isBluetoothOn by remember { mutableStateOf(HardwareReadinessManager.isBluetoothEnabled(context)) }
    var isLocationOn by remember { mutableStateOf(HardwareReadinessManager.isLocationEnabled(context)) }
    var hasNearbyWifiPerm by remember { mutableStateOf(HardwareReadinessManager.hasNearbyWifiPermission(context)) }
    var hasBluetoothPerm by remember { mutableStateOf(HardwareReadinessManager.hasBluetoothPermissions(context)) }
    var hasLocPerm by remember { mutableStateOf(HardwareReadinessManager.hasLocationPermission(context)) }

    fun refreshStates() {
        isWifiOn = HardwareReadinessManager.isWifiEnabled(context)
        isBluetoothOn = HardwareReadinessManager.isBluetoothEnabled(context)
        isLocationOn = HardwareReadinessManager.isLocationEnabled(context)
        hasNearbyWifiPerm = HardwareReadinessManager.hasNearbyWifiPermission(context)
        hasBluetoothPerm = HardwareReadinessManager.hasBluetoothPermissions(context)
        hasLocPerm = HardwareReadinessManager.hasLocationPermission(context)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshStates()
    }

    val wifiSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStates()
    }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStates()
    }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStates()
    }

    LaunchedEffect(Unit) {
        refreshStates()
    }

    fun autoEnableAll() {
        // 1. Request missing permissions
        val missingPermissions = mutableListOf<String>()
        if (!hasLocPerm) {
            missingPermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!hasNearbyWifiPerm && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            missingPermissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (!hasBluetoothPerm) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                missingPermissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
                missingPermissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
                missingPermissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                missingPermissions.add(android.Manifest.permission.BLUETOOTH)
                missingPermissions.add(android.Manifest.permission.BLUETOOTH_ADMIN)
            }
        }
        if (missingPermissions.isNotEmpty()) {
            permissionsLauncher.launch(missingPermissions.toTypedArray())
        }

        // 2. Auto-enable Bluetooth
        if (!isBluetoothOn) {
            try {
                bluetoothLauncher.launch(HardwareReadinessManager.getEnableBluetoothIntent())
            } catch (_: Exception) {}
        }

        // 3. Auto-enable Wi-Fi
        if (!isWifiOn) {
            try {
                wifiSettingsLauncher.launch(HardwareReadinessManager.getEnableWifiIntent())
            } catch (_: Exception) {}
        }

        // 4. Prompt Location if disabled
        if (!isLocationOn) {
            try {
                locationSettingsLauncher.launch(HardwareReadinessManager.getEnableLocationIntent())
            } catch (_: Exception) {}
        }
    }

    val isCompletelyReady = isWifiOn && isBluetoothOn && isLocationOn && hasNearbyWifiPerm && hasBluetoothPerm && hasLocPerm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WifiTethering,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Direct Share Setup",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Auto-enabling hardware for $actionName",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Cute Share uses your device's own Wi-Fi Direct. Turn on these features for high-speed direct sharing:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                HardwareStatusItem(
                    title = "Wi-Fi Direct",
                    subtitle = "Direct device connection (no router needed)",
                    icon = Icons.Rounded.Wifi,
                    iconColor = Color(0xFF00E5FF),
                    isReady = isWifiOn,
                    onEnable = {
                        try {
                            wifiSettingsLauncher.launch(HardwareReadinessManager.getEnableWifiIntent())
                        } catch (_: Exception) {}
                    }
                )

                HardwareStatusItem(
                    title = "Bluetooth",
                    subtitle = "Nearby peer advertising, pairing and transfer",
                    icon = Icons.Rounded.Bluetooth,
                    iconColor = Color(0xFF38BDF8),
                    isReady = isBluetoothOn && hasBluetoothPerm,
                    onEnable = {
                        if (!hasBluetoothPerm) {
                            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                arrayOf(
                                    android.Manifest.permission.BLUETOOTH_SCAN,
                                    android.Manifest.permission.BLUETOOTH_CONNECT,
                                    android.Manifest.permission.BLUETOOTH_ADVERTISE
                                )
                            } else {
                                arrayOf(
                                    android.Manifest.permission.BLUETOOTH,
                                    android.Manifest.permission.BLUETOOTH_ADMIN
                                )
                            }
                            permissionsLauncher.launch(perms)
                        } else {
                            try {
                                bluetoothLauncher.launch(HardwareReadinessManager.getEnableBluetoothIntent())
                            } catch (_: Exception) {}
                        }
                    }
                )

                HardwareStatusItem(
                    title = "Nearby Devices",
                    subtitle = "Required to scan and connect with nearby phones",
                    icon = Icons.Rounded.NearMe,
                    iconColor = Color(0xFFA855F7),
                    isReady = hasNearbyWifiPerm,
                    onEnable = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            permissionsLauncher.launch(arrayOf(android.Manifest.permission.NEARBY_WIFI_DEVICES))
                        }
                    }
                )

                HardwareStatusItem(
                    title = "Location Services",
                    subtitle = "Required by Android for Wi-Fi Direct P2P",
                    icon = Icons.Rounded.LocationOn,
                    iconColor = Color(0xFF10B981),
                    isReady = isLocationOn && hasLocPerm,
                    onEnable = {
                        try {
                            locationSettingsLauncher.launch(HardwareReadinessManager.getEnableLocationIntent())
                        } catch (_: Exception) {}
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCompletelyReady) {
                        onAllReady()
                    } else {
                        autoEnableAll()
                        onAllReady()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("auto_turn_on_all_button")
            ) {
                Text(
                    text = if (isCompletelyReady) "Continue" else "Auto Turn On & Ready",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_hardware_setup_button")
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun HardwareStatusItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    isReady: Boolean,
    onEnable: () -> Unit
) {
    Surface(
        onClick = onEnable,
        shape = RoundedCornerShape(12.dp),
        color = if (isReady) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isReady) Color(0xFF10B981).copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isReady) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Ready",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(15.dp)
                    )
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Turn On",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
