package com.zaneschepke.wireguardautotunnel.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.dokar.sonner.ToastType
import com.zaneschepke.logcatter.LogReader
import com.zaneschepke.logcatter.model.LogMessage
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.LoggerUiState
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.toUserFriendlyTimestamp
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

class LoggerViewModel(
    private val logReader: LogReader,
    private val globalEffectRepository: GlobalEffectRepository,
) : OrbitContainerHost<LoggerUiState, LoggerUiState, Nothing>, ViewModel() {

    // accumulator shared between the producer and sampler coroutines of the batching pipeline
    private val logBuffer = ArrayDeque<LogMessage>()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override val container =
        orbitContainer<LoggerUiState, Nothing>(
            LoggerUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            intent {
                logReader.bufferedLogs
                    .onEach { logMessage ->
                        synchronized(logBuffer) {
                            if (logBuffer.size >= MAX_LOG_SIZE) logBuffer.removeFirst()
                            logBuffer.addLast(logMessage)
                        }
                    }
                    .sample(BATCH_INTERVAL)
                    .collect {
                        val snapshot = synchronized(logBuffer) { logBuffer.toList() }
                        reduce { state.copy(messages = snapshot, isLoading = false) }
                    }
            }
            intent {
                delay(300.milliseconds)
                if (state.isLoading) {
                    reduce { state.copy(isLoading = false) }
                }
            }
        }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun exportLogs(uri: Uri?) = intent {
        val timestamp = Instant.now().toUserFriendlyTimestamp()
        postSideEffect(
            GlobalSideEffect.ExportFile(
                uri = uri,
                fileName =
                    "${Constants.BASE_LOG_FILE_NAME}_${timestamp}_${BuildConfig.VERSION_NAME}_${BuildConfig.FLAVOR}.zip",
                mimeType = FileUtils.ZIP_FILE_MIME_TYPE,
                successMessage = StringValue.StringResource(R.string.log_export_success),
                prepareFile = { file -> logReader.zipLogFiles(file.absolutePath) },
            )
        )
    }

    fun deleteLogs() = intent {
        synchronized(logBuffer) { logBuffer.clear() }
        reduce { state.copy(messages = emptyList()) }
        logReader.deleteAndClearLogs()
        postSideEffect(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(R.string.stored_logs_deleted),
                ToastType.Success,
            )
        )
    }

    companion object {
        const val MAX_LOG_SIZE = 10_000L
        private val BATCH_INTERVAL = 200.milliseconds
    }
}
