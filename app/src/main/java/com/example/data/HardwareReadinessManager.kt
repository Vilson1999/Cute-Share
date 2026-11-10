package com.example.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

object HardwareReadinessManager {

    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifi?.isWifiEnabled == true
        } catch (_: Exception) {
            true
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bm?.adapter?.isEnabled == true
        } catch (_: Exception) {
            true
        }
    }

    fun isLocationEnabled(context: Context): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
            LocationManagerCompat.isLocationEnabled(lm)
        } catch (_: Exception) {
            true
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
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPublicDownloadWritePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isAllHardwareReady(context: Context): Boolean {
        return isWifiEnabled(context) && isBluetoothEnabled(context) && isLocationEnabled(context) &&
                hasNearbyWifiPermission(context) && hasBluetoothPermissions(context) &&
                hasLocationPermission(context) && hasPublicDownloadWritePermission(context)
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
