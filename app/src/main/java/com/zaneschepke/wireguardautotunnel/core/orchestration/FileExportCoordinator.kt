package com.zaneschepke.wireguardautotunnel.core.orchestration

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import com.zaneschepke.wireguardautotunnel.util.StringValue
import timber.log.Timber

class FileExportCoordinator(
    private val context: Context,
    private val fileUtils: FileUtils,
    private val globalEffectRepository: GlobalEffectRepository,
) {

    fun needsWritePermission(uri: Uri?): Boolean {
        if (uri != null || Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) != PackageManager.PERMISSION_GRANTED
    }

    suspend fun execute(export: GlobalSideEffect.ExportFile) {
        val shareFile =
            fileUtils.createNewShareFile(export.fileName).getOrElse {
                postFailure(it)
                return
            }

        try {
            Timber.d(
                "Exporting ${export.fileName} ${if (export.uri == null) "via Downloads" else "via SAF"}"
            )
            export.prepareFile(shareFile)
            fileUtils.exportFile(shareFile, export.uri, export.mimeType).getOrElse {
                postFailure(it)
                return
            }
            val successMessage =
                if (export.uri == null) {
                    StringValue.StringResource(R.string.export_success_downloads)
                } else {
                    export.successMessage
                }
            globalEffectRepository.post(
                GlobalSideEffect.Snackbar(successMessage, ToastType.Success)
            )
            export.onComplete()
        } catch (error: Exception) {
            postFailure(error)
        } finally {
            if (shareFile.exists()) shareFile.delete()
        }
    }

    private suspend fun postFailure(error: Throwable) {
        Timber.e(error, "Failed to export file")
        globalEffectRepository.post(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(
                    R.string.export_failed,
                    ": ${error.localizedMessage}",
                ),
                ToastType.Error,
            )
        )
    }
}
