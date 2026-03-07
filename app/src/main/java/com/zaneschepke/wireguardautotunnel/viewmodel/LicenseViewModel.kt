package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.wireguardautotunnel.ui.state.LicenseUiState
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class LicenseViewModel(private val fileUtils: FileUtils) :
    ContainerHost<LicenseUiState, Nothing>, ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val container =
        container<LicenseUiState, Nothing>(
            LicenseUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            intent {
                val licenses = fileUtils.readLibraryLicensesFromAssets()
                reduce { state.copy(isLoading = false, licenses = licenses) }
            }
        }
}
