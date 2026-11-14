internal fun getRequiredPermissions(): Array<String> {
    return when {
        // Android 13 - 17 (API 33+): Wi-Fi Nearby + Bluetooth Nearby runtime permissions
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        // Android 12 / 12L (API 31-32): Bluetooth Nearby + Location for Wi-Fi P2P
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // Android 10 & 11 (API 29-30): Location runtime permission (BLUETOOTH is install-time, Scoped Storage for Downloads)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // Android 9 (API 28): Location + WRITE_EXTERNAL_STORAGE for Downloads
        else -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}

private fun hasAllPermissions(context: Context): Boolean {
    val perms = getRequiredPermissions()
    return perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private enum class RequiredSetting {
    WIFI,
    BLUETOOTH,
    LOCATION
}

private fun getMissingRequiredSetting(context: Context): RequiredSetting? {
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