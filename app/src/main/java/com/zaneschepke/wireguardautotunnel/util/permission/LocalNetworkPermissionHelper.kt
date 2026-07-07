package com.zaneschepke.wireguardautotunnel.util.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object LocalNetworkPermissionHelper {

    fun shouldRequestPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
    }

    fun isPermissionGranted(context: Context): Boolean {
        return if (shouldRequestPermission()) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
