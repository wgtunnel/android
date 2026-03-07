package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.AppUpdate
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.UpdateRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.SupportUiState
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.StringValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class SupportViewModel(
    private val updateRepository: UpdateRepository,
    private val mainDispatcher: CoroutineDispatcher,
    private val globalEffectRepository: GlobalEffectRepository,
) : ContainerHost<SupportUiState, Nothing>, ViewModel() {

    override val container = container<SupportUiState, Nothing>(SupportUiState())

    fun checkForStandaloneUpdate() = intent {
        postSideEffect(
            GlobalSideEffect.Toast(StringValue.StringResource(R.string.checking_for_update))
        )
        reduce { state.copy(isLoading = true) }
        updateRepository
            .checkForUpdate(BuildConfig.VERSION_NAME)
            .onSuccess { update ->
                if (update == null) {
                    postSideEffect(
                        GlobalSideEffect.Toast(
                            StringValue.StringResource(R.string.latest_installed)
                        )
                    )
                } else reduce { state.copy(appUpdate = update.sanitized()) }
            }
            .onFailure {
                postSideEffect(
                    GlobalSideEffect.Toast(StringValue.StringResource(R.string.update_check_failed))
                )
            }
        reduce { state.copy(isLoading = false) }
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    private fun AppUpdate?.sanitized(): AppUpdate? {
        return this?.copy(releaseNotes = releaseNotes.substringBefore(CHANGELOG_START))
    }

    fun viewReleaseNotes() = intent {
        val version =
            if (BuildConfig.VERSION_NAME.contains("nightly")) {
                "nightly"
            } else {
                state.appUpdate?.version?.removePrefix("v")?.trim() ?: ""
            }
        val url = "${Constants.BASE_RELEASE_URL}$version".trim()
        postSideEffect(GlobalSideEffect.LaunchUrl(url))
    }

    fun dismissUpdate() = intent { reduce { state.copy(appUpdate = null) } }

    fun downloadAndInstall() = intent {
        if (
            state.appUpdate == null ||
                state.appUpdate?.apkUrl == null ||
                state.appUpdate?.apkFileName == null
        )
            return@intent
        reduce { state.copy(isLoading = true) }
        updateRepository
            .downloadApk(state.appUpdate!!.apkUrl!!, state.appUpdate!!.apkFileName!!) { progress ->
                handleProgress(progress)
            }
            .onSuccess { postSideEffect(GlobalSideEffect.InstallApk(it)) }
            .onFailure {
                postSideEffect(
                    GlobalSideEffect.Toast(
                        StringValue.StringResource(R.string.update_download_failed)
                    )
                )
            }
    }

    private fun handleProgress(progress: Float) = intent {
        withContext(mainDispatcher) { reduce { state.copy(downloadProgress = progress) } }
    }

    companion object {
        private const val CHANGELOG_START =
            "SHA-256 fingerprint for the 4096-bit signing certificate:"
    }
}
