package com.zaneschepke.networkmonitor.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor.Companion.ANDROID_UNKNOWN_SSID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Suppress("DEPRECATION")
fun WifiManager.getCurrentSecurityType(): WifiSecurityType? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        WifiSecurityType.from(connectionInfo.currentSecurityType)
    } else {
        null
    }
}

@Suppress("DEPRECATION")
suspend fun WifiManager?.getWifiSsid(): String {
    return withContext(Dispatchers.IO) {
        try {
            this@getWifiSsid?.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotEmpty() }
                ?: ANDROID_UNKNOWN_SSID
        } catch (e: Exception) {
            Timber.e(e)
            ANDROID_UNKNOWN_SSID
        }
    }
}

fun NetworkCapabilities.getWifiSsid(): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val info: WifiInfo
        if (transportInfo is WifiInfo) {
            info = transportInfo as WifiInfo
            return info.ssid.removeSurrounding("\"").trim()
        }
    }
    return ANDROID_UNKNOWN_SSID
}

fun LocationManager.isLocationServicesEnabled(): Boolean {
    return try {
        val isGpsEnabled = isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        isGpsEnabled || isNetworkEnabled
    } catch (e: Exception) {
        Timber.e(e, "Error checking location services")
        false
    }
}

fun Context.hasRequiredLocationPermissions(): Boolean {
    val fineLocationGranted =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    val backgroundLocationGranted =
        if (
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) &&
                // exclude Android TV on Q as background location is not required on this
                // version
                !(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
                    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
        ) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No need for ACCESS_BACKGROUND_LOCATION on Android P or Android TV on Q
        }
    return fineLocationGranted && backgroundLocationGranted
}

fun Context.isAirplaneModeOn(): Boolean {
    return Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
}
