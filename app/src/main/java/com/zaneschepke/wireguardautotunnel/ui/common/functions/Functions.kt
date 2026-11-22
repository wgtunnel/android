package com.zaneschepke.wireguardautotunnel.ui.common.functions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.FileManagerChooserSheet
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
    var chooserTargets by remember { mutableStateOf<List<FileImportTarget>?>(null) }
    var launchingChosenTarget by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) onData(uri) else onCancel()
        }
    chooserTargets?.let { targets ->
        FileManagerChooserSheet(
            targets = targets,
            onDismiss = {
                chooserTargets = null
                if (!launchingChosenTarget) onCancel()
                launchingChosenTarget = false
            },
            onChoose = { target ->
                launchingChosenTarget = true
                chooserTargets = null
                launcher.launch(target.toImportIntent())
            },
        )
    }
    return {
        // Leanback always asks. A phone asks only when */* would open the photo picker or a stub.
        if (isTv || context.implicitFileImportIsBlocked()) {
            val targets = context.listFileImportTargets()
            if (targets.isEmpty()) {
                onNoFileExplorer()
            } else {
                launchingChosenTarget = false
                chooserTargets = targets
            }
        } else {
            launcher.launch(phoneImportIntent())
        }
    }
}

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
                                    PackageManager.MATCH_DEFAULT_ONLY.toLong(),
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
            },
    ) { uri ->
        if (uri != null) {
            onSuccess(uri)
        } else {
            onCanceled()
        }
    }
}
