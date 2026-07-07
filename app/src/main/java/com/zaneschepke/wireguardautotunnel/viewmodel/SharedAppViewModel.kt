package com.zaneschepke.wireguardautotunnel.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelBackendCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.AppStateRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.SelectedTunnelsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.parser.ConfigParseException
import com.zaneschepke.wireguardautotunnel.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.service.autotunnel.AutoTunnelStateHolder
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState
import com.zaneschepke.wireguardautotunnel.ui.state.GlobalAppUiState
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelsUiState
import com.zaneschepke.wireguardautotunnel.ui.theme.Theme
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import com.zaneschepke.wireguardautotunnel.util.LocaleUtil
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.QuickConfig
import com.zaneschepke.wireguardautotunnel.util.extensions.TunnelName
import com.zaneschepke.wireguardautotunnel.util.extensions.asStringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.saveTunnelsUniquely
import com.zaneschepke.wireguardautotunnel.util.network.NetworkUtils
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import xyz.teamgravity.pin_lock_compose.PinManager

class SharedAppViewModel(
    private val appStateRepository: AppStateRepository,
    private val serviceManager: ServiceManager,
    private val tunnelCoordinator: TunnelCoordinator,
    private val globalEffectRepository: GlobalEffectRepository,
    private val tunnelRepository: TunnelRepository,
    private val settingsRepository: GeneralSettingRepository,
    private val autoTunnelStateHolder: AutoTunnelStateHolder,
    private val selectedTunnelsRepository: SelectedTunnelsRepository,
    private val tunnelBackendCoordinator: TunnelBackendCoordinator,
    private val httpClient: HttpClient,
    private val fileUtils: FileUtils,
    private val networkUtils: NetworkUtils,
) : ContainerHost<GlobalAppUiState, LocalSideEffect>, ViewModel() {

    val globalSideEffect = globalEffectRepository.flow

    val tunnelsUiState =
        combine(
                tunnelRepository.userTunnelsFlow,
                tunnelCoordinator.backendStatus,
                selectedTunnelsRepository.flow,
            ) { tunnels, backendStatus, selectedTuns ->
                val sortedTunnels = tunnels.sortedBy { it.position }

                val displayStates =
                    backendStatus.activeTunnels.mapValues { (_, activeTunnel) ->
                        DisplayTunnelState.from(activeTunnel)
                    }

                TunnelsUiState(
                    tunnels = sortedTunnels,
                    backendStatus = backendStatus,
                    displayStates = displayStates,
                    selectedTunnels = selectedTuns,
                    isLoading = false,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), TunnelsUiState())

    override val container =
        container<GlobalAppUiState, LocalSideEffect>(
            GlobalAppUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5_000L },
        ) {
            intent {
                combine(
                        tunnelRepository.userTunnelsFlow
                            .map { tuns -> tuns.associate { it.id to it.name } }
                            .distinctUntilChanged(),
                        settingsRepository.flow,
                        autoTunnelStateHolder.active,
                        tunnelsUiState
                            .map { Pair(it.isLoading, it.selectedTunnels.size) }
                            .distinctUntilChanged(),
                        appStateRepository.flow,
                    ) { tunNames, settings, autoTunnelActive, (loading, selectedTunCount), appState
                        ->
                        state.copy(
                            theme = settings.theme,
                            tunnelMode = settings.tunnelMode,
                            locale = settings.locale ?: LocaleUtil.OPTION_PHONE_LANGUAGE,
                            tunnelNames = tunNames,
                            alreadyDonated = settings.alreadyDonated,
                            isAutoTunnelActive = autoTunnelActive,
                            isLocationDisclosureShown = appState.isLocationDisclosureShown,
                            isBatteryOptimizationShown = appState.isBatteryOptimizationDisableShown,
                            shouldShowDonationSnackbar = appState.shouldShowDonationSnackbar,
                            selectedTunnelCount = selectedTunCount,
                            pinLockEnabled = settings.isPinLockEnabled,
                            isScreenRecordingProtectionEnabled =
                                settings.screenRecordingSecurityEnabled,
                            isAppLoaded = !loading,
                        )
                    }
                    .collect { newState -> reduce { newState } }
            }
        }

    fun startTunnel(tunnelConfig: TunnelConfig) = intent {
        if (state.tunnelMode == TunnelMode.VPN) {
            if (!serviceManager.hasVpnPermission())
                return@intent postSideEffect(
                    GlobalSideEffect.RequestVpnPermission(TunnelMode.VPN, tunnelConfig)
                )
        }
        tunnelCoordinator.startTunnel(tunnelConfig)
    }

    fun postSideEffect(localSideEffect: LocalSideEffect) = intent {
        postSideEffect(localSideEffect)
    }

    fun setLocationDisclosureShown() = intent {
        appStateRepository.setLocationDisclosureShown(true)
    }

    fun setTheme(theme: Theme) = intent { settingsRepository.updateTheme(theme) }

    fun setLocale(locale: String) = intent {
        settingsRepository.updateLocale(locale)
        postSideEffect(GlobalSideEffect.ConfigChanged)
    }

    fun setPinLockEnabled(enabled: Boolean) = intent {
        if (!enabled) PinManager.clearPin()
        settingsRepository.updatePinLockEnabled(enabled)
    }

    fun stopTunnel(tunnelConfig: TunnelConfig) = intent {
        tunnelCoordinator.stopTunnel(tunnelConfig.id)
    }

    fun setAppMode(mode: TunnelMode) = intent {
        if (mode == TunnelMode.VPN || mode == TunnelMode.LOCK_DOWN) {
            if (!serviceManager.hasVpnPermission()) {
                return@intent postSideEffect(GlobalSideEffect.RequestVpnPermission(mode, null))
            }
        }

        tunnelBackendCoordinator.changeMode(mode)
    }

    fun setShouldShowDonationSnackbar(to: Boolean) = intent {
        appStateRepository.setShouldShowDonationSnackbar(to)
    }

    fun showSnackMessage(message: StringValue, type: ToastType) = intent {
        postGlobalSideEffect(GlobalSideEffect.Snackbar(message, type))
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun authenticated() = intent { reduce { state.copy(isPinVerified = true) } }

    suspend fun postGlobalSideEffect(sideEffect: GlobalSideEffect) {
        globalEffectRepository.post(sideEffect)
    }

    fun disableBatteryOptimizationsShown() = intent {
        appStateRepository.setBatteryOptimizationDisableShown(true)
    }

    fun saveSortChanges(tunnels: List<TunnelConfig>) = intent {
        tunnelRepository.saveAll(tunnels.mapIndexed { index, conf -> conf.copy(position = index) })
        postSideEffect(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(R.string.config_changes_saved),
                ToastType.Success,
            )
        )
        postSideEffect(GlobalSideEffect.PopBackStack)
    }

    fun sortByLatency(tunnels: List<TunnelConfig>) = intent {
        postSideEffect(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(R.string.pinging_servers),
                ToastType.Info,
            )
        )
        val sortedResult =
            withContext(Dispatchers.IO) {
                tunnels
                    .map { tunnel ->
                        async {
                            val config =
                                try {
                                    tunnel.getConfig()
                                } catch (e: Exception) {
                                    null
                                }
                            val endpoint = config?.peers?.firstOrNull()?.host
                            if (endpoint != null) {
                                val latency =
                                    try {
                                        val stats = networkUtils.pingWithStats(endpoint, 3)
                                        if (stats.isReachable) stats.rttAvg else Double.MAX_VALUE
                                    } catch (_: Exception) {
                                        Double.MAX_VALUE
                                    }
                                tunnel to latency
                            } else {
                                tunnel to Double.MAX_VALUE
                            }
                        }
                    }
                    .awaitAll()
                    .sortedBy { it.second }
            }
        val sortedTunnels = sortedResult.map { it.first }
        val latencies = sortedResult.associate { it.first.id to it.second }
        postSideEffect(LocalSideEffect.LatencySortFinished(sortedTunnels, latencies))
    }

    fun importTunnelConfigs(configs: Map<QuickConfig, TunnelName>) = intent {
        try {
            val tunnelConfigs = configs.map { (config, name) ->
                TunnelConfig.tunnelConfFromQuick(config, name)
            }
            tunnelRepository.saveTunnelsUniquely(tunnelConfigs, state.tunnelNames.map { it.value })
        } catch (e: Exception) {
            if (e is ConfigParseException) {
                postSideEffect(GlobalSideEffect.Snackbar(e.asStringValue(), ToastType.Error))
            } else {
                postSideEffect(
                    GlobalSideEffect.Snackbar(
                        StringValue.StringResource(R.string.config_error),
                        ToastType.Error,
                    )
                )
            }
        }
    }

    fun importFromClipboard(conf: String) {
        importTunnelConfigs(mapOf(conf to null))
    }

    fun importFromQr(conf: String) = intent { importFromClipboard(conf) }

    fun promptWgImport(url: String) = intent { reduce { state.copy(pendingWgImportUrl = url) } }

    fun dismissWgImport() = intent { reduce { state.copy(pendingWgImportUrl = null) } }

    fun importFromUrl(url: String) = intent {
        reduce { state.copy(pendingWgImportUrl = null) }
        try {
            httpClient.prepareGet(url).execute { response ->
                if (response.status.value !in 200..299) {
                    throw IOException("Server returned error: ${response.status.value}")
                }

                val body = response.bodyAsText()

                val contentType = response.contentType()
                val isHtml =
                    (contentType?.match(ContentType.Text.Html) == true) ||
                        body.trimStart().let {
                            it.startsWith("<!DOCTYPE", ignoreCase = true) ||
                                it.startsWith("<html", ignoreCase = true)
                        }

                if (isHtml) {
                    postSideEffect(
                        GlobalSideEffect.Snackbar(
                            StringValue.StringResource(R.string.error_invalid_config_url),
                            ToastType.Error,
                        )
                    )
                    return@execute
                }

                importFromClipboard(body)
            }
        } catch (e: Exception) {
            Timber.e(e)
            postSideEffect(
                GlobalSideEffect.Snackbar(
                    StringValue.StringResource(R.string.error_download_failed),
                    ToastType.Error,
                )
            )
        }
    }

    fun importFromUri(uri: Uri) = intent {
        fileUtils
            .readConfigsFromUri(uri)
            .onSuccess { configs -> importTunnelConfigs(configs) }
            .onFailure {
                val message =
                    when (it) {
                        is IOException -> StringValue.StringResource(R.string.error_download_failed)
                        else -> StringValue.StringResource(R.string.error_file_extension)
                    }
                postSideEffect(GlobalSideEffect.Snackbar(message, ToastType.Error))
            }
    }

    fun toggleSelectAllTunnels() = intent {
        if (state.selectedTunnelCount != state.tunnelNames.size) {
            val tunnels = tunnelRepository.getAll()
            selectedTunnelsRepository.set(tunnels)
            return@intent
        }
        selectedTunnelsRepository.clear()
    }

    fun clearSelectedTunnels() = intent { selectedTunnelsRepository.clear() }

    fun toggleSelectedTunnel(tunnelId: Int) = intent {
        val (selectedTuns, tunnels) = tunnelsUiState.value.run { Pair(selectedTunnels, tunnels) }
        val selected =
            selectedTuns.toMutableList().apply {
                val removed = removeIf { it.id == tunnelId }
                if (!removed) addAll(tunnels.filter { it.id == tunnelId })
            }
        selectedTunnelsRepository.set(selected)
    }

    fun deleteSelectedTunnels() = intent {
        val activeTunIds =
            tunnelCoordinator.backendStatus.firstOrNull()?.activeTunnels?.map { it.key }
        val selectedTuns = tunnelsUiState.value.selectedTunnels
        if (selectedTuns.any { activeTunIds?.contains(it.id) == true })
            return@intent postSideEffect(
                GlobalSideEffect.Snackbar(
                    StringValue.StringResource(R.string.delete_active_message),
                    ToastType.Error,
                )
            )
        tunnelRepository.delete(selectedTuns)
        clearSelectedTunnels()
    }

    fun copySelectedTunnel() = intent {
        val selected = tunnelsUiState.value.selectedTunnels.firstOrNull() ?: return@intent
        val copy = TunnelConfig.tunnelConfFromQuick(selected.quickConfig, selected.name)
        tunnelRepository.saveTunnelsUniquely(listOf(copy), state.tunnelNames.map { it.value })
        clearSelectedTunnels()
    }

    fun exportSelectedTunnels(uri: Uri?) = intent {
        val selectedTunnels = tunnelsUiState.value.selectedTunnels
        val files = createConfFiles(selectedTunnels)

        val shareFileName = createExportFileName(selectedTunnels.size)

        val onFailure = { action: Throwable ->
            intent {
                postSideEffect(
                    GlobalSideEffect.Snackbar(
                        StringValue.StringResource(
                            R.string.export_failed,
                            ": ${action.localizedMessage}",
                        ),
                        ToastType.Error,
                    )
                )
            }
            Unit
        }

        fileUtils
            .createNewShareFile(shareFileName)
            .onSuccess {
                try {
                    fileUtils.zipAll(it, files).onFailure(onFailure)
                    fileUtils.exportFile(it, uri, FileUtils.ZIP_FILE_MIME_TYPE).onFailure(onFailure)
                } finally {
                    if (it.exists()) it.delete()
                }
                postSideEffect(
                    GlobalSideEffect.Snackbar(
                        StringValue.StringResource(R.string.export_success),
                        ToastType.Success,
                    )
                )
                clearSelectedTunnels()
            }
            .onFailure(onFailure)
    }

    private fun createExportFileName(tunnelCount: Int): String {
        val timestamp =
            java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))

        return when (tunnelCount) {
            1 -> "WGTunnel_Export_$timestamp.zip"
            else -> "WGTunnel_Export_${timestamp}_${tunnelCount}_Tunnels.zip"
        }
    }

    fun setScreenRecordingSecurity(to: Boolean) = intent {
        settingsRepository.updateScreenRecordingSecurity(to)
    }

    suspend fun createConfFiles(tunnels: Collection<TunnelConfig>): List<File> =
        tunnels.mapNotNull { config ->
            if (config.quickConfig.isNotBlank()) {
                fileUtils.createFile(config.name, config.quickConfig)
            } else null
        }
}
