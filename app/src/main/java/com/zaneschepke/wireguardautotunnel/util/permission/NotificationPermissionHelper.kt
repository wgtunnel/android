package com.zaneschepke.wireguardautotunnel.util.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object NotificationPermissionHelper {

    fun shouldRequestPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    fun isPermissionGranted(context: Context): Boolean {
        return if (shouldRequestPermission()) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
