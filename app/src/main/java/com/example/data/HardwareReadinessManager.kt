package com.example.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

object HardwareReadinessManager {

    enum class RequiredSetting {
        WIFI,
        BLUETOOTH,
        LOCATION
    }

    internal fun getRequiredPermissions(): Array<String> {
        return when {
            // Android 13 - 17 (API 33+): Wi-Fi Nearby + Bluetooth Nearby runtime permissions
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            // Android 12 / 12L (API 31-32): Bluetooth Nearby + Location + Storage
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            // Android 11 (API 30): Location + Storage
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            // Android 9 & 10 (API 28-29): Location + Read/Write External Storage
            else -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    fun hasAllPermissions(context: Context): Boolean {
        val perms = getRequiredPermissions()
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } && hasAllFilesPermission(context)
    }

    fun hasAllFilesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Environment.isExternalStorageManager()
            } catch (_: Exception) {
                false
            }
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getManageAllFilesAccessIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
    }

    fun getFallbackAllFilesAccessIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun getMissingRequiredSetting(context: Context): RequiredSetting? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiOn = wifiManager?.isWifiEnabled ?: true
        if (!isWifiOn) {
            return RequiredSetting.WIFI
        }

        val btManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = btManager?.adapter
        if (btAdapter != null && !btAdapter.isEnabled) {
            return RequiredSetting.BLUETOOTH
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val isLocationOn = locationManager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: true
            if (!isLocationOn) {
                return RequiredSetting.LOCATION
            }
        }

        return null
    }

    fun isLocationRequired(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifi?.isWifiEnabled ?: true
        } catch (_: Exception) {
            true
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bm = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter
            adapter == null || adapter.isEnabled
        } catch (_: Exception) {
            true
        }
    }

    fun isLocationEnabled(context: Context): Boolean {
        if (!isLocationRequired()) return true
        return try {
            val lm = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
            LocationManagerCompat.isLocationEnabled(lm)
        } catch (_: Exception) {
            true
        }
    }

    fun getRequiredPermissionsForCurrentDevice(): Array<String> = getRequiredPermissions()

    fun getMissingPermissions(context: Context): List<String> {
        return getRequiredPermissions().filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasNearbyWifiPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        if (!isLocationRequired()) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPublicDownloadWritePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getMissingRequiredSettings(context: Context): List<RequiredSetting> {
        val missing = mutableListOf<RequiredSetting>()
        if (!isWifiEnabled(context)) {
            missing.add(RequiredSetting.WIFI)
        }
        if (!isBluetoothEnabled(context)) {
            missing.add(RequiredSetting.BLUETOOTH)
        }
        if (isLocationRequired() && !isLocationEnabled(context)) {
            missing.add(RequiredSetting.LOCATION)
        }
        return missing
    }

    fun isAllHardwareReady(context: Context): Boolean {
        return hasAllPermissions(context) && getMissingRequiredSetting(context) == null
    }

    fun getEnableWifiIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
    }

    fun getEnableBluetoothIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }

    fun getEnableLocationIntent(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }
}
