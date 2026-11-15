package com.zaneschepke.wireguardautotunnel.ui.common.functions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.zaneschepke.wireguardautotunnel.util.FileUtils

data class FileImportTarget(
    val packageName: String,
    val activityName: String,
    val label: String,
    val action: String,
) {
    fun componentName(): ComponentName = ComponentName(packageName, activityName)

    fun toImportIntent(): Intent =
        Intent(action).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            component = componentName()
            type = FileUtils.ALL_FILE_TYPES
            putExtra(Intent.EXTRA_MIME_TYPES, FileUtils.TV_IMPORT_MIME_TYPES)
        }
}

fun isBlockedImportPackage(packageName: String): Boolean =
    packageName.startsWith(FileUtils.GOOGLE_TV_EXPLORER_STUB) ||
        packageName.startsWith(FileUtils.ANDROID_TV_EXPLORER_STUB) ||
        packageName == PHOTO_PICKER_PACKAGE ||
        packageName == PHOTO_PICKER_PACKAGE_ALT

/** Drops TV stubs and the photo picker. */
fun selectFileImportTargets(activities: List<FileImportTarget>): List<FileImportTarget> {
    val chosen = LinkedHashMap<Pair<String, String>, FileImportTarget>()
    val ordered = activities.sortedBy { target ->
        if (target.action == Intent.ACTION_OPEN_DOCUMENT) 1 else 0
    }
    for (target in ordered) {
        if (isBlockedImportPackage(target.packageName)) continue
        chosen[target.packageName to target.activityName] = target
    }
    return chosen.values.sortedWith(
        compareBy({ it.label.lowercase() }, { it.packageName }, { it.activityName }),
    )
}

fun phoneImportIntent(): Intent =
    Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = FileUtils.ALL_FILE_TYPES
    }

/** True when the default GET_CONTENT handler is missing, a TV stub, or the photo picker. */
fun Context.implicitFileImportIsBlocked(): Boolean {
    val resolved = resolveImportActivity(phoneImportIntent()) ?: return true
    val packageName = resolved.activityInfo?.packageName ?: return true
    return isBlockedImportPackage(packageName)
}

fun Context.listFileImportTargets(): List<FileImportTarget> {
    val packageManager = packageManager
    val discovered =
        listOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT).flatMap { action ->
            queryImportActivities(importDiscoveryIntent(action)).mapNotNull { info ->
                info.toFileImportTarget(action, packageManager)
            }
        }
    return selectFileImportTargets(discovered)
}

private fun importDiscoveryIntent(action: String): Intent =
    Intent(action).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = FileUtils.ALL_FILE_TYPES
    }

private fun ResolveInfo.toFileImportTarget(
    action: String,
    packageManager: PackageManager,
): FileImportTarget? {
    val activity = activityInfo ?: return null
    val packageName = activity.packageName ?: return null
    val activityName = activity.name ?: return null
    val label = loadLabel(packageManager).toString().trim().ifBlank { packageName }
    return FileImportTarget(
        packageName = packageName,
        activityName = activityName,
        label = label,
        action = action,
    )
}

private const val PHOTO_PICKER_PACKAGE = "com.google.android.providers.media.module"
private const val PHOTO_PICKER_PACKAGE_ALT = "com.android.providers.media.module"

private fun Context.queryImportActivities(intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        )
    } else {
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }

private fun Context.resolveImportActivity(intent: Intent): ResolveInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.resolveActivity(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )
    } else {
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
