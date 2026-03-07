package com.zaneschepke.wireguardautotunnel.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.zaneschepke.logcatter.LogReader
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.LoggerUiState
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import com.zaneschepke.wireguardautotunnel.util.StringValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber

class LoggerViewModel(
    private val logReader: LogReader,
    private val fileUtils: FileUtils,
    private val globalEffectRepository: GlobalEffectRepository,
) : ContainerHost<LoggerUiState, Nothing>, ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val container =
        container<LoggerUiState, Nothing>(
            LoggerUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            intent {
                logReader.bufferedLogs.collect { logMessage ->
                    reduce {
                        state.copy(
                            messages =
                                state.messages.toMutableList().apply {
                                    if (size >= MAX_LOG_SIZE) removeAt(0)
                                    add(logMessage)
                                }
                        )
                    }
                }
            }
        }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun exportLogs(uri: Uri?) = intent {
        val result =
            fileUtils.createNewShareFile(
                "${Constants.BASE_LOG_FILE_NAME}_${BuildConfig.VERSION_NAME}_${BuildConfig.FLAVOR}.zip"
            )
        val onFailure = { action: Throwable ->
            Timber.e(action)
            intent {
                postSideEffect(
                    GlobalSideEffect.Toast(
                        StringValue.StringResource(
                            R.string.export_failed,
                            ": ${action.localizedMessage}",
                        )
                    )
                )
            }
            Unit
        }
        result.fold(
            onSuccess = { file ->
                try {
                    logReader.zipLogFiles(file.absolutePath)
                    fileUtils
                        .exportFile(file, uri, FileUtils.ZIP_FILE_MIME_TYPE)
                        .onFailure(onFailure)
                } finally {
                    if (file.exists()) file.delete()
                }
            },
            onFailure = onFailure,
        )
    }

    fun deleteLogs() = intent {
        reduce { state.copy(messages = emptyList()) }
        logReader.deleteAndClearLogs()
    }

    companion object {
        const val MAX_LOG_SIZE = 10_000L
    }
}
