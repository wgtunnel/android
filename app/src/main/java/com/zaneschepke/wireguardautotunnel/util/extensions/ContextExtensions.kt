package com.zaneschepke.wireguardautotunnel.util.extensions

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.zaneschepke.wireguardautotunnel.MainActivity
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.service.tile.AutoTunnelControlTile
import com.zaneschepke.wireguardautotunnel.core.service.tile.TunnelControlTile
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.TunnelApp
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import java.io.File
import java.io.InputStream
import java.util.*
import kotlin.system.exitProcess
import timber.log.Timber

fun Context.openWebUrl(url: String): Result<Unit> {
    return kotlin
        .runCatching {
            val webpage: Uri = url.toUri()
            val intent =
                Intent(Intent.ACTION_VIEW, webpage).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            startActivity(intent)
        }
        .onFailure { showToast(R.string.no_browser_detected) }
}

fun Context.isBatteryOptimizationsDisabled(): Boolean {
    val pm = getSystemService(POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(packageName)
}

fun Context.launchNotificationSettings() {
    if (isRunningOnTv()) return launchAppSettings()
    val settingsIntent: Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    this.startActivity(settingsIntent)
}

fun Context.hasSAFSupport(mimeType: String): Boolean {
    val intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
        }
    val activitiesToResolveIntent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    Timber.d(
        "Found ${activitiesToResolveIntent.size} activities: ${activitiesToResolveIntent.map { it.activityInfo.packageName }}"
    )

    return if (activitiesToResolveIntent.isEmpty()) {
        Timber.w("No activities found to handle SAF intent")
        false
    } else if (
        isRunningOnTv() &&
            activitiesToResolveIntent.all {
                val name = it.activityInfo.packageName
                name.startsWith(FileUtils.GOOGLE_TV_EXPLORER_STUB) ||
                    name.startsWith(FileUtils.ANDROID_TV_EXPLORER_STUB)
            }
    ) {
        Timber.w("Only stub file explorers found on TV")
        false
    } else {
        true
    }
}

fun Context.launchShareFile(file: File) {
    FileProvider.getUriForFile(this, getString(R.string.provider), file)
    val shareIntent =
        Intent().apply {
            action = Intent.ACTION_SEND
            type = FileUtils.ALL_FILE_TYPES
            putExtra(Intent.EXTRA_STREAM, file)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooserIntent =
        Intent.createChooser(shareIntent, "").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    this.startActivity(chooserIntent)
}

fun Context.showToast(resId: Int) {
    Toast.makeText(this, this.getString(resId), Toast.LENGTH_LONG).show()
}

fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

fun Context.launchSupportEmail() {
    val intent =
        Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.my_email)))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(
            Intent.createChooser(intent, getString(R.string.email_chooser)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } else {
        showToast(R.string.no_email_detected)
    }
}

fun Context.isRunningOnTv(): Boolean {
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

fun Context.launchVpnSettings(): Result<Unit> {
    return kotlin.runCatching {
        val intent =
            Intent(Constants.VPN_SETTINGS_PACKAGE).apply { setFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        startActivity(intent)
    }
}

fun Context.getInputStreamFromUri(uri: Uri): InputStream? {
    return this.applicationContext.contentResolver.openInputStream(uri)
}

fun Context.launchLocationServicesSettings(): Result<Unit> {
    return kotlin.runCatching {
        val intent =
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        startActivity(intent)
    }
}

fun Context.launchSettings(): Result<Unit> {
    return kotlin.runCatching {
        val intent =
            Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        startActivity(intent)
    }
}

fun Context.launchAppSettings() {
    kotlin
        .runCatching {
            val intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            startActivity(intent)
        }
        .onFailure {
            val fallback = Intent(Settings.ACTION_SETTINGS)
            startActivity(fallback)
        }
}

fun Context.requestTunnelTileServiceStateUpdate() =
    runCatching {
            TileService.requestListeningState(
                this,
                ComponentName(this, TunnelControlTile::class.java),
            )
        }
        .onFailure { Timber.w(it) }

fun Context.requestAutoTunnelTileServiceUpdate() =
    runCatching {
            TileService.requestListeningState(
                this,
                ComponentName(this, AutoTunnelControlTile::class.java),
            )
        }
        .onFailure { Timber.w(it) }

fun Context.getAllInternetCapablePackages(): List<PackageInfo> {
    val permissions = arrayOf(Manifest.permission.INTERNET)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackagesHoldingPermissions(
            permissions,
            PackageManager.PackageInfoFlags.of(0L),
        )
    } else {
        packageManager.getPackagesHoldingPermissions(permissions, 0)
    }
}

fun Context.getSplitTunnelApps(): List<TunnelApp> {
    val packages = getAllInternetCapablePackages()
    return packages
        .filter { it.applicationInfo != null }
        .map { pkg ->
            TunnelApp(
                packageManager.getApplicationLabel(pkg.applicationInfo!!).toString(),
                pkg.packageName,
            )
        }
}

fun Context.canInstallPackages(): Boolean {
    return packageManager.canRequestPackageInstalls()
}

fun Context.requestInstallPackagesPermission() {
    val intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = "package:${packageName}".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    startActivity(intent)
}

fun Context.installApk(apkFile: File) {
    val apkUri = FileProvider.getUriForFile(this, getString(R.string.provider), apkFile)
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    startActivity(intent)
}

fun Context.launchPlayStoreListing() {
    val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("com.android.vending")
        }

    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        openWebUrl("https://play.google.com/store/apps/details?id=$packageName")
    }
}

fun Context.launchPlayStoreReview() {
    val uri = Uri.parse("market://details?id=$packageName&showAllReviews=true")
    val intent =
        Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("com.android.vending")
        }
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        openWebUrl("https://play.google.com/store/apps/details?id=$packageName&showAllReviews=true")
    }
}

fun Activity.setScreenBrightness(brightness: Float) {
    window.attributes = window.attributes.apply { screenBrightness = brightness }
}

fun MainActivity.restartApp() {
    Intent(this, MainActivity::class.java).also {
        startActivity(it)
        exitProcess(0)
    }
}

fun PackageManager.getFriendlyAppName(packageName: String, appInfo: ApplicationInfo): String {
    val label =
        try {
            getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }

    // probably a package, parse it
    return if (label.count { it == '.' } > 1) {
        val lastSegment = label.substringAfterLast(".", label)
        lastSegment.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    } else {
        label
    }
}
