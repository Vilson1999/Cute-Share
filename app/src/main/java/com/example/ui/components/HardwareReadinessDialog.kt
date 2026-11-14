package com.example.ui.components

import android.Manifest
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
import androidx.compose.material3.Surface
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.HardwareReadinessManager
import com.example.ui.icons.AppIcons

private enum class ReadinessAutoStep {
    RUNTIME_PERMS,
    ALL_FILES,
    WIFI,
    BLUETOOTH,
    LOCATION
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun HardwareReadinessDialog(
    actionName: String = "Share",
    onAllReady: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var missingSetting by remember { mutableStateOf(HardwareReadinessManager.getMissingRequiredSetting(context)) }
    var hasAllPerms by remember { mutableStateOf(HardwareReadinessManager.hasAllPermissions(context)) }
    var hasAllFilesPerm by remember { mutableStateOf(HardwareReadinessManager.hasAllFilesPermission(context)) }
    var hasNearbyWifiPerm by remember { mutableStateOf(HardwareReadinessManager.hasNearbyWifiPermission(context)) }
    var hasBluetoothPerm by remember { mutableStateOf(HardwareReadinessManager.hasBluetoothPermissions(context)) }
    var hasLocPerm by remember { mutableStateOf(HardwareReadinessManager.hasLocationPermission(context)) }
    var isWifiOn by remember { mutableStateOf(HardwareReadinessManager.isWifiEnabled(context)) }
    var isBluetoothOn by remember { mutableStateOf(HardwareReadinessManager.isBluetoothEnabled(context)) }
    var isLocationOn by remember { mutableStateOf(HardwareReadinessManager.isLocationEnabled(context)) }
    val locationRequired = HardwareReadinessManager.isLocationRequired()

    var activeAutoStep by remember { mutableStateOf<ReadinessAutoStep?>(null) }

    fun refreshStates() {
        missingSetting = HardwareReadinessManager.getMissingRequiredSetting(context)
        hasAllPerms = HardwareReadinessManager.hasAllPermissions(context)
        hasAllFilesPerm = HardwareReadinessManager.hasAllFilesPermission(context)
        hasNearbyWifiPerm = HardwareReadinessManager.hasNearbyWifiPermission(context)
        hasBluetoothPerm = HardwareReadinessManager.hasBluetoothPermissions(context)
        hasLocPerm = HardwareReadinessManager.hasLocationPermission(context)
        isWifiOn = HardwareReadinessManager.isWifiEnabled(context)
        isBluetoothOn = HardwareReadinessManager.isBluetoothEnabled(context)
        isLocationOn = HardwareReadinessManager.isLocationEnabled(context)
    }

    lateinit var launchStep: (ReadinessAutoStep?) -> Unit

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshStates()
        val prev = activeAutoStep
        if (prev == ReadinessAutoStep.RUNTIME_PERMS) {
            launchStep(ReadinessAutoStep.RUNTIME_PERMS)
        }
    }

    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStates()
        val prev = activeAutoStep
        if (prev == ReadinessAutoStep.ALL_FILES) {
            launchStep(ReadinessAutoStep.ALL_FILES)
        }
    }

    val wifiSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStates()
        val prev = activeAutoStep
        if (prev == ReadinessAutoStep.WIFI) {
            launchStep(ReadinessAutoStep.WIFI)
        }
    }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStates()
        val prev = activeAutoStep
        if (prev == ReadinessAutoStep.BLUETOOTH) {
            launchStep(ReadinessAutoStep.BLUETOOTH)
        }
    }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStates()
        activeAutoStep = null
    }

    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                allFilesSettingsLauncher.launch(HardwareReadinessManager.getManageAllFilesAccessIntent(context))
            } catch (_: Exception) {
                try {
                    allFilesSettingsLauncher.launch(HardwareReadinessManager.getFallbackAllFilesAccessIntent())
                } catch (_: Exception) {
                    activeAutoStep = null
                }
            }
        } else {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    launchStep = { completedStep ->
        if (HardwareReadinessManager.isAllHardwareReady(context)) {
            activeAutoStep = null
        } else {
            val steps = ReadinessAutoStep.entries
            val startIdx = if (completedStep == null) 0 else steps.indexOf(completedStep) + 1
            var launched = false
            for (i in startIdx until steps.size) {
                when (steps[i]) {
                    ReadinessAutoStep.RUNTIME_PERMS -> {
                        val missingPerms = HardwareReadinessManager.getMissingPermissions(context)
                        if (missingPerms.isNotEmpty()) {
                            activeAutoStep = ReadinessAutoStep.RUNTIME_PERMS
                            permissionsLauncher.launch(missingPerms.toTypedArray())
                            launched = true
                            break
                        }
                    }
                    ReadinessAutoStep.ALL_FILES -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            !HardwareReadinessManager.hasAllFilesPermission(context)
                        ) {
                            activeAutoStep = ReadinessAutoStep.ALL_FILES
                            requestAllFilesAccess()
                            launched = true
                            break
                        }
                    }
                    ReadinessAutoStep.WIFI -> {
                        if (!HardwareReadinessManager.isWifiEnabled(context)) {
                            activeAutoStep = ReadinessAutoStep.WIFI
                            try {
                                wifiSettingsLauncher.launch(HardwareReadinessManager.getEnableWifiIntent())
                                launched = true
                            } catch (_: Exception) {
                                activeAutoStep = null
                            }
                            break
                        }
                    }
                    ReadinessAutoStep.BLUETOOTH -> {
                        if (!HardwareReadinessManager.isBluetoothEnabled(context)) {
                            activeAutoStep = ReadinessAutoStep.BLUETOOTH
                            try {
                                bluetoothLauncher.launch(HardwareReadinessManager.getEnableBluetoothIntent())
                                launched = true
                            } catch (_: Exception) {
                                activeAutoStep = null
                            }
                            break
                        }
                    }
                    ReadinessAutoStep.LOCATION -> {
                        if (HardwareReadinessManager.isLocationRequired() &&
                            !HardwareReadinessManager.isLocationEnabled(context)
                        ) {
                            activeAutoStep = ReadinessAutoStep.LOCATION
                            try {
                                locationSettingsLauncher.launch(HardwareReadinessManager.getEnableLocationIntent())
                                launched = true
                            } catch (_: Exception) {
                                activeAutoStep = null
                            }
                            break
                        }
                    }
                }
            }
            if (!launched) {
                activeAutoStep = null
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        refreshStates()
    }

    val isCompletelyReady = hasAllPerms && missingSetting == null

    LaunchedEffect(isCompletelyReady) {
        if (isCompletelyReady) {
            activeAutoStep = null
            onAllReady()
        }
    }

    if (isCompletelyReady) {
        return
    }

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
                        imageVector = AppIcons.WifiTethering,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Permissions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Only ask/display items that are NOT currently granted or turned on
                if (!hasAllFilesPerm) {
                    HardwareStatusItem(
                        title = "All Files Access",
                        icon = AppIcons.FolderOpen,
                        iconColor = Color(0xFFF59E0B),
                        onEnable = {
                            activeAutoStep = null
                            requestAllFilesAccess()
                        }
                    )
                }

                if (!isWifiOn) {
                    HardwareStatusItem(
                        title = "Wi-Fi Direct",
                        icon = AppIcons.Wifi,
                        iconColor = Color(0xFF00E5FF),
                        onEnable = {
                            activeAutoStep = null
                            try {
                                wifiSettingsLauncher.launch(HardwareReadinessManager.getEnableWifiIntent())
                            } catch (_: Exception) {}
                        }
                    )
                }

                if (!(isBluetoothOn && hasBluetoothPerm)) {
                    HardwareStatusItem(
                        title = "Bluetooth",
                        icon = AppIcons.Bluetooth,
                        iconColor = Color(0xFF38BDF8),
                        onEnable = {
                            activeAutoStep = null
                            if (!hasBluetoothPerm) {
                                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_SCAN,
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                        Manifest.permission.BLUETOOTH_ADVERTISE
                                    )
                                } else {
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH,
                                        Manifest.permission.BLUETOOTH_ADMIN
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
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNearbyWifiPerm) {
                    HardwareStatusItem(
                        title = "Nearby Devices",
                        icon = AppIcons.NearMe,
                        iconColor = Color(0xFFA855F7),
                        onEnable = {
                            activeAutoStep = null
                            permissionsLauncher.launch(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES))
                        }
                    )
                }

                if (locationRequired && !(isLocationOn && hasLocPerm)) {
                    HardwareStatusItem(
                        title = "Location Services",
                        icon = Icons.Rounded.LocationOn,
                        iconColor = Color(0xFF10B981),
                        onEnable = {
                            activeAutoStep = null
                            if (!hasLocPerm) {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                try {
                                    locationSettingsLauncher.launch(HardwareReadinessManager.getEnableLocationIntent())
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { launchStep(null) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("auto_turn_on_all_button")
            ) {
                Text(
                    text = "Turn On",
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
    icon: ImageVector,
    iconColor: Color,
    onEnable: () -> Unit
) {
    Surface(
        onClick = onEnable,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
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

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

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
