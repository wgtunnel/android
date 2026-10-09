package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.UpdateDownloadState
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.UpdateDownloader
import com.zaneschepke.wireguardautotunnel.domain.repository.UpdateRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.SupportUiState
import com.zaneschepke.wireguardautotunnel.util.StringValue
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

class SupportViewModel(
    private val updateRepository: UpdateRepository,
    private val updateDownloader: UpdateDownloader,
    private val globalEffectRepository: GlobalEffectRepository,
) : OrbitContainerHost<SupportUiState, SupportUiState, Nothing>, ViewModel() {

    override val container = orbitContainer<SupportUiState, Nothing>(SupportUiState())

    init {
        observeDownload()
    }

    // The download itself belongs to the system, this only mirrors it into the UI state
    private fun observeDownload() = intent {
        var previous: UpdateDownloadState = UpdateDownloadState.Idle
        updateDownloader.state.collect { next ->
            reduce { state.copy(download = next) }
            if (previous is UpdateDownloadState.Downloading && next is UpdateDownloadState.Failed) {
                postSideEffect(
                    GlobalSideEffect.Snackbar(
                        StringValue.StringResource(R.string.update_download_failed),
                        ToastType.Error,
                    )
                )
            }
            previous = next
        }
    }

    fun checkForStandaloneUpdate(startDownloadIfAvailable: Boolean = false) = intent {
        if (!startDownloadIfAvailable) {
            postSideEffect(
                GlobalSideEffect.Snackbar(
                    StringValue.StringResource(R.string.checking_for_update),
                    ToastType.Info,
                )
            )
        }
        reduce { state.copy(isLoading = true) }
        updateRepository
            .checkForUpdate(BuildConfig.VERSION_NAME)
            .onSuccess { update ->
                if (update == null) {
                    reduce { state.copy(isLoading = false, appUpdate = null) }
                    if (!startDownloadIfAvailable) {
                        postSideEffect(
                            GlobalSideEffect.Snackbar(
                                StringValue.StringResource(R.string.latest_installed),
                                ToastType.Info,
                            )
                        )
                    }
                } else {
                    reduce { state.copy(appUpdate = update, isLoading = false) }
                    if (startDownloadIfAvailable) {
                        downloadAndInstall()
                    }
                }
            }
            .onFailure {
                reduce { state.copy(isLoading = false) }
                postSideEffect(
                    GlobalSideEffect.Snackbar(
                        StringValue.StringResource(R.string.update_check_failed),
                        ToastType.Error,
                    )
                )
            }
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun viewReleaseNotes() = intent {
        val url = state.appUpdate?.releaseUrl ?: return@intent
        postSideEffect(GlobalSideEffect.LaunchUrl(url))
    }

    // Only hides the dialog, a running download carries on and reports when it is done
    fun dismissUpdate() = intent { reduce { state.copy(appUpdate = null, isLoading = false) } }

    fun cancelDownload() = intent { updateDownloader.cancel() }

    // From the update row while a download runs or is ready, no network check needed
    fun showActiveDownload() = intent {
        val update = updateDownloader.activeUpdate()
        if (update != null) {
            reduce { state.copy(appUpdate = update) }
        } else {
            checkForStandaloneUpdate()
        }
    }

    fun downloadAndInstall() = intent {
        val update = state.appUpdate
        if (update == null || update.apkUrl.isNullOrBlank() || update.apkFileName.isNullOrBlank()) {
            postSideEffect(
                GlobalSideEffect.Snackbar(
                    StringValue.StringResource(R.string.update_download_failed),
                    ToastType.Error,
                )
            )
            return@intent
        }

        when (val download = state.download) {
            // Already running, a repeated tap must not start another
            is UpdateDownloadState.Downloading -> return@intent
            is UpdateDownloadState.Completed ->
                if (download.file.name == update.apkFileName && download.file.exists()) {
                    postSideEffect(GlobalSideEffect.InstallApk(download.file))
                    return@intent
                }
            else -> Unit
        }

        updateDownloader.start(update).onFailure {
            postSideEffect(
                GlobalSideEffect.Snackbar(
                    StringValue.StringResource(R.string.update_download_failed),
                    ToastType.Error,
                )
            )
        }
    }
}
