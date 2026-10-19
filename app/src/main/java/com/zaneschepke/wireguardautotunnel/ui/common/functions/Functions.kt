package com.zaneschepke.wireguardautotunnel.ui.common.functions

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import timber.log.Timber

@Composable
fun rememberFileImportLauncherForResult(
    onNoFileExplorer: () -> Unit,
    onData: (data: Uri) -> Unit,
    onCancel: () -> Unit = {},
): () -> Unit {
    val context = LocalContext.current
    val isTv = LocalIsAndroidTV.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) onData(uri) else onCancel()
        }
    return {
        val intent = if (isTv) context.createTvImportIntent() else context.phoneImportIntent()
        if (intent == null) onNoFileExplorer() else launcher.launch(intent)
    }
}

private fun Context.phoneImportIntent(): Intent =
    Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = FileUtils.ALL_FILE_TYPES
    }

// An implicit */* GET_CONTENT is redirected to the photo picker, which loops on Android TV.
private fun Context.createTvImportIntent(): Intent? {
    val launch =
        phoneImportIntent().apply {
            putExtra(Intent.EXTRA_MIME_TYPES, FileUtils.TV_IMPORT_MIME_TYPES)
        }
    val probe = Intent(launch).apply { type = FileUtils.TEXT_MIME_TYPE }
    val real = queryImportActivities(probe).filterNot { it.isBlockedImportHandler() }
    if (real.isEmpty()) return null
    val preferred =
        resolveImportActivity(probe)?.takeUnless { it.isBlockedImportHandler() }?.componentName()
    val chosen = real.firstOrNull { it.componentName() == preferred } ?: real.first()
    return launch.apply { component = chosen.componentName() }
}

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

private fun ResolveInfo.isBlockedImportHandler(): Boolean {
    val name = activityInfo.packageName
    return name.startsWith(FileUtils.GOOGLE_TV_EXPLORER_STUB) ||
        name.startsWith(FileUtils.ANDROID_TV_EXPLORER_STUB) ||
        name == PHOTO_PICKER_PACKAGE ||
        name == PHOTO_PICKER_PACKAGE_ALT
}

private const val PHOTO_PICKER_PACKAGE = "com.google.android.providers.media.module"
private const val PHOTO_PICKER_PACKAGE_ALT = "com.android.providers.media.module"

private fun ResolveInfo.componentName(): ComponentName =
    ComponentName(activityInfo.packageName, activityInfo.name)

@Composable
fun rememberFileExportLauncherForResult(
    mimeType: String = FileUtils.ZIP_FILE_MIME_TYPE,
    onSuccess: (Uri) -> Unit,
    onCanceled: () -> Unit,
    onUnsupported: () -> Unit,
): ManagedActivityResultLauncher<String, Uri?> {
    return rememberLauncherForActivityResult(
        contract =
            object : ActivityResultContracts.CreateDocument(mimeType) {
                override fun createIntent(context: Context, input: String): Intent {
                    val intent =
                        super.createIntent(context, input).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE, input)
                            type = mimeType
                        }

                    // Detect Android TV stub pickers that do nothing
                    val activities =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.packageManager.queryIntentActivities(
                                intent,
                                PackageManager.ResolveInfoFlags.of(
                                    PackageManager.MATCH_DEFAULT_ONLY.toLong()
                                ),
                            )
                        } else {
                            context.packageManager.queryIntentActivities(
                                intent,
                                PackageManager.MATCH_DEFAULT_ONLY,
                            )
                        }

                    val isStubOnly = activities.all {
                        val pkg = it.activityInfo.packageName
                        pkg.startsWith(FileUtils.GOOGLE_TV_EXPLORER_STUB) ||
                            pkg.startsWith(FileUtils.ANDROID_TV_EXPLORER_STUB)
                    }

                    if (isStubOnly) {
                        Timber.w("Detected Android TV stub file picker — export not supported")
                        onUnsupported()
                    }

                    return intent
                }
            }
    ) { uri ->
        if (uri != null) {
            onSuccess(uri)
        } else {
            onCanceled()
        }
    }
}
