package com.zaneschepke.wireguardautotunnel.viewmodel

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dokar.sonner.ToastType
import com.wgtunnel.parser.Config
import com.wgtunnel.parser.ConfigParseException
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.AutoTunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelBackendCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelGroup
import com.zaneschepke.wireguardautotunnel.domain.repository.AppStateRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.SelectedTunnelsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelGroupRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelsSelection
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.NotificationPendingAction
import com.zaneschepke.wireguardautotunnel.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.service.autotunnel.AutoTunnelStateHolder
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState
import com.zaneschepke.wireguardautotunnel.ui.state.GlobalAppUiState
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelsUiState
import com.zaneschepke.wireguardautotunnel.ui.state.moveDisplayedRows
import com.zaneschepke.wireguardautotunnel.ui.state.nextChildPosition
import com.zaneschepke.wireguardautotunnel.ui.state.nextRootPosition
import com.zaneschepke.wireguardautotunnel.ui.state.sortGroupChildrenByName
import com.zaneschepke.wireguardautotunnel.ui.state.sortRootByName
import com.zaneschepke.wireguardautotunnel.ui.state.ungroupKeepingOrder
import com.zaneschepke.wireguardautotunnel.ui.state.uniqueDisplayName
import com.zaneschepke.wireguardautotunnel.ui.theme.Theme
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import com.zaneschepke.wireguardautotunnel.util.LocaleUtil
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.QuickConfig
import com.zaneschepke.wireguardautotunnel.util.extensions.TunnelName
import com.zaneschepke.wireguardautotunnel.util.extensions.asExportFileName
import com.zaneschepke.wireguardautotunnel.util.extensions.asFileExportName
import com.zaneschepke.wireguardautotunnel.util.extensions.asStringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.isFileAccessDenied
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import timber.log.Timber
import xyz.teamgravity.pin_lock_compose.PinManager

class SharedAppViewModel(
    private val appStateRepository: AppStateRepository,
    private val serviceManager: ServiceManager,
    private val tunnelCoordinator: TunnelCoordinator,
    private val autoTunnelCoordinator: AutoTunnelCoordinator,
    private val globalEffectRepository: GlobalEffectRepository,
    private val tunnelRepository: TunnelRepository,
    private val tunnelGroupRepository: TunnelGroupRepository,
    private val settingsRepository: GeneralSettingRepository,
    private val autoTunnelStateHolder: AutoTunnelStateHolder,
    private val selectedTunnelsRepository: SelectedTunnelsRepository,
    private val tunnelBackendCoordinator: TunnelBackendCoordinator,
    private val httpClient: HttpClient,
    private val fileUtils: FileUtils,
    private val networkUtils: NetworkUtils,
) : OrbitContainerHost<GlobalAppUiState, GlobalAppUiState, LocalSideEffect>, ViewModel() {

    val globalSideEffect = globalEffectRepository.flow

    private val _supportAutoUpdateRequests = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val supportAutoUpdateRequests = _supportAutoUpdateRequests.asSharedFlow()

    fun requestSupportAutoUpdate(startDownload: Boolean) {
        _supportAutoUpdateRequests.tryEmit(startDownload)
    }

    // Sticky, the Support screen may not be composed yet when this is requested
    private val _showUpdateStatusRequested = MutableStateFlow(false)
    val showUpdateStatusRequested = _showUpdateStatusRequested.asStateFlow()

    fun requestShowUpdateStatus() {
        _showUpdateStatusRequested.value = true
    }

    fun consumeShowUpdateStatus() {
        _showUpdateStatusRequested.value = false
    }

    private val reorderDraft = MutableStateFlow<ReorderDraft?>(null)

    val tunnelsUiState =
        combine(
                tunnelRepository.userTunnelsFlow,
                tunnelGroupRepository.flow,
                tunnelCoordinator.backendStatus,
                selectedTunnelsRepository.flow,
                reorderDraft,
            ) { tunnels, groups, backendStatus, selection, draft ->
                val displayStates =
                    backendStatus.activeTunnels.mapValues { (_, activeTunnel) ->
                        DisplayTunnelState.from(activeTunnel)
                    }

                TunnelsUiState(
                    tunnels = draft?.tunnels ?: tunnels,
                    groups = draft?.groups ?: groups,
                    backendStatus = backendStatus,
                    displayStates = displayStates,
                    selectedTunnels = selection.tunnels,
                    selectedGroups = selection.groups,
                    isLoading = false,
                    isReorderMode = draft != null,
                    reorderScopeGroupId = draft?.scopeGroupId,
                    reorderLatencies = draft?.latencies.orEmpty(),
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), TunnelsUiState())

    override val container =
        orbitContainer<GlobalAppUiState, LocalSideEffect>(
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
                            .map {
                                TunnelsChrome(
                                    loading = it.isLoading,
                                    selectedTunCount = it.selectedCount,
                                    isReorderMode = it.isReorderMode,
                                    reorderScopeTitle = it.reorderScopeGroup?.name,
                                    hasGroups = it.groups.isNotEmpty(),
                                    canReorder = it.tunnels.size + it.groups.size > 1,
                                )
                            }
                            .distinctUntilChanged(),
                        appStateRepository.flow,
                    ) { tunNames, settings, autoTunnelActive, chrome, appState ->
                        state.copy(
                            theme = settings.theme,
                            tunnelMode = settings.tunnelMode,
                            tunnelNames = tunNames,
                            alreadyDonated = settings.alreadyDonated,
                            isAutoTunnelActive = autoTunnelActive,
                            isLocationDisclosureShown = appState.isLocationDisclosureShown,
                            isBatteryOptimizationShown = appState.isBatteryOptimizationDisableShown,
                            shouldShowDonationSnackbar = appState.shouldShowDonationSnackbar,
                            selectedTunnelCount = chrome.selectedTunCount,
                            isReorderMode = chrome.isReorderMode,
                            reorderScopeTitle = chrome.reorderScopeTitle,
                            hasGroups = chrome.hasGroups,
                            canReorder = chrome.canReorder,
                            pinLockEnabled = settings.isPinLockEnabled,
                            isScreenRecordingProtectionEnabled =
                                settings.screenRecordingSecurityEnabled,
                            isAppLoaded = !chrome.loading,
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
        if (
            !serviceManager.hasNotificationPermission() &&
                !appStateRepository.isNotificationPermissionRequested()
        ) {
            appStateRepository.setNotificationPermissionRequested(true)
            return@intent postSideEffect(
                GlobalSideEffect.RequestNotificationPermission(
                    NotificationPendingAction.StartTunnel(tunnelConfig)
                )
            )
        }
        tunnelCoordinator.startTunnel(tunnelConfig)
    }

    fun toggleAutoTunnel() = intent { autoTunnelCoordinator.toggle() }

    fun postSideEffect(localSideEffect: LocalSideEffect) = intent {
        postSideEffect(localSideEffect)
    }

    fun setLocationDisclosureShown() = intent {
        appStateRepository.setLocationDisclosureShown(true)
    }

    fun setTheme(theme: Theme) = intent { settingsRepository.updateTheme(theme) }

    fun setLocale(locale: String) = intent {
        // pre-T the stored value is reapplied on startup, on T+ the system persists it
        if (!LocaleUtil.isSystemManaged) settingsRepository.updateLocale(locale)
        withContext(Dispatchers.Main) { LocaleUtil.changeLocale(locale) }
    }

    fun syncLocale() = intent {
        val stored = settingsRepository.flow.firstOrNull()?.locale ?: return@intent
        if (stored == LocaleUtil.OPTION_PHONE_LANGUAGE) return@intent
        if (LocaleUtil.isSystemManaged) {
            // one-time handoff to the system, without overriding a locale set in system settings
            if (AppCompatDelegate.getApplicationLocales().isEmpty) {
                withContext(Dispatchers.Main) { LocaleUtil.changeLocale(stored) }
            }
            settingsRepository.updateLocale(LocaleUtil.OPTION_PHONE_LANGUAGE)
        } else {
            withContext(Dispatchers.Main) { LocaleUtil.changeLocale(stored) }
        }
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

    fun enterReorderMode() = intent {
        val ui = tunnelsUiState.value
        clearSelectedTunnels()
        reorderDraft.value =
            ReorderDraft(
                tunnels = ui.tunnels,
                groups = ui.groups,
                scopeGroupId = null,
                originalTunnels = ui.tunnels,
                originalGroups = ui.groups,
            )
    }

    fun cancelReorder() = intent { reorderDraft.value = null }

    fun exitReorderScope() = intent {
        reorderDraft.update { draft -> draft?.copy(scopeGroupId = null, sortAscending = null) }
    }

    fun enterReorderScope(groupId: Int) = intent {
        reorderDraft.update { draft -> draft?.copy(scopeGroupId = groupId, sortAscending = null) }
    }

    fun applyReorderDraft(groups: List<TunnelGroup>, tunnels: List<TunnelConfig>) {
        reorderDraft.update { draft -> draft?.copy(groups = groups, tunnels = tunnels) }
    }

    fun moveReorder(fromIndex: Int, toIndex: Int) {
        val draft = reorderDraft.value ?: return
        val (groups, tunnels) =
            moveDisplayedRows(
                draft.groups,
                draft.tunnels,
                fromIndex,
                toIndex,
                draft.scopeGroupId,
            )
        applyReorderDraft(groups, tunnels)
    }

    fun saveReorder() = intent {
        val draft = reorderDraft.value ?: return@intent
        tunnelGroupRepository.saveAll(draft.groups)
        tunnelRepository.saveAll(draft.tunnels)
        reorderDraft.value = null
        postSideEffect(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(R.string.config_changes_saved),
                ToastType.Success,
            )
        )
    }

    fun sortReorderByName() = intent {
        val draft = reorderDraft.value ?: return@intent
        val nextAscending =
            when (draft.sortAscending) {
                null -> true
                true -> false
                false -> null
            }
        if (nextAscending == null) {
            reorderDraft.value =
                draft.copy(
                    tunnels = draft.originalTunnels,
                    groups = draft.originalGroups,
                    sortAscending = null,
                )
            return@intent
        }
        val (groups, tunnels) =
            if (draft.scopeGroupId != null) {
                draft.groups to
                    sortGroupChildrenByName(draft.tunnels, draft.scopeGroupId, nextAscending)
            } else {
                sortRootByName(draft.groups, draft.tunnels, nextAscending)
            }
        reorderDraft.value =
            draft.copy(tunnels = tunnels, groups = groups, sortAscending = nextAscending)
    }

    fun sortReorderByLatency() = intent {
        val draft = reorderDraft.value ?: return@intent
        postSideEffect(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(R.string.pinging_servers),
                ToastType.Info,
            )
        )
        val scopeId = draft.scopeGroupId
        val targets =
            if (scopeId != null) draft.tunnels.filter { it.groupId == scopeId }
            else draft.tunnels.filter { it.groupId == null }
        // Skipped for group headers, only leaf tunnels are ever pinged
        val results = pingSorted(targets)
        val ordered = results.map { it.first }
        val tunnels =
            if (scopeId != null) {
                val others = draft.tunnels.filter { it.groupId != scopeId }
                others + ordered.mapIndexed { index, tunnel -> tunnel.copy(position = index) }
            } else {
                val grouped = draft.tunnels.filter { it.groupId != null }
                val slots =
                    draft.tunnels
                        .filter { it.groupId == null }
                        .sortedBy { it.position }
                        .map { it.position }
                val rewritten = ordered.mapIndexed { index, tunnel ->
                    tunnel.copy(position = slots.getOrElse(index) { tunnel.position })
                }
                grouped + rewritten
            }
        val latencies = draft.latencies + results.associate { (tunnel, ms) -> tunnel.id to ms }
        reorderDraft.value =
            draft.copy(tunnels = tunnels, sortAscending = null, latencies = latencies)
    }

    fun createGroup(name: String) = intent {
        val ui = tunnelsUiState.value
        val unique = uniqueDisplayName(name, ui.groups.map { it.name }, "Group")
        tunnelGroupRepository.save(
            TunnelGroup(
                name = unique,
                position = nextRootPosition(ui.groups, ui.tunnels),
                expanded = true,
            )
        )
    }

    fun createGroupAndMoveSelected(name: String) = intent {
        val ui = tunnelsUiState.value
        val selected = ui.selectedTunnels
        if (selected.isEmpty()) return@intent
        val unique = uniqueDisplayName(name, ui.groups.map { it.name }, "Group")
        val groupId =
            tunnelGroupRepository.save(
                TunnelGroup(
                    name = unique,
                    position = nextRootPosition(ui.groups, ui.tunnels),
                    expanded = true,
                )
            )
        moveToGroup(groupId, selected, ui.tunnels)
    }

    fun renameGroup(group: TunnelGroup, name: String) = intent {
        val unique =
            uniqueDisplayName(
                name,
                tunnelsUiState.value.groups.filter { it.id != group.id }.map { it.name },
                "Group",
            )
        tunnelGroupRepository.save(group.copy(name = unique))
    }

    fun toggleGroupExpanded(group: TunnelGroup) = intent {
        tunnelGroupRepository.setExpanded(group.id, !group.expanded)
    }

    fun ungroup(group: TunnelGroup) = intent {
        val ui = tunnelsUiState.value
        val (groups, tunnels) = ungroupKeepingOrder(ui.groups, ui.tunnels, group.id)
        tunnelRepository.saveAll(tunnels)
        tunnelGroupRepository.saveAll(groups)
        tunnelGroupRepository.delete(group)
        selectedTunnelsRepository.clear()
    }

    fun deleteGroupAndTunnels(group: TunnelGroup) = intent {
        val children = tunnelsUiState.value.tunnels.filter { it.groupId == group.id }
        val activeTunIds =
            tunnelCoordinator.backendStatus.firstOrNull()?.activeTunnels?.map { it.key }
        if (children.any { activeTunIds?.contains(it.id) == true })
            return@intent postSideEffect(
                GlobalSideEffect.Snackbar(
                    StringValue.StringResource(R.string.delete_active_message),
                    ToastType.Error,
                )
            )
        if (children.isNotEmpty()) tunnelRepository.delete(children)
        tunnelGroupRepository.delete(group)
        selectedTunnelsRepository.clear()
    }

    fun moveSelectedToGroup(groupId: Int) = intent {
        val ui = tunnelsUiState.value
        if (ui.selectedTunnels.isEmpty()) return@intent
        moveToGroup(groupId, ui.selectedTunnels, ui.tunnels)
    }

    private suspend fun moveToGroup(
        groupId: Int,
        selected: List<TunnelConfig>,
        all: List<TunnelConfig>,
    ) {
        var next = nextChildPosition(all, groupId)
        val ids = selected.map { it.id }.toSet()
        tunnelRepository.saveAll(
            all.map { tunnel ->
                if (tunnel.id in ids) tunnel.copy(groupId = groupId, position = next++) else tunnel
            }
        )
        clearSelectedTunnels()
    }

    fun ungroupSelected() = intent {
        val ui = tunnelsUiState.value
        if (!ui.canUngroup) return@intent
        var groups = ui.groups
        var tunnels = ui.tunnels
        ui.selectedGroups.forEach { group ->
            val result = ungroupKeepingOrder(groups, tunnels, group.id)
            groups = result.first
            tunnels = result.second
            tunnelGroupRepository.delete(group)
        }
        val stillGrouped =
            ui.selectedTunnels.mapNotNull { selected ->
                tunnels.firstOrNull { it.id == selected.id && it.groupId != null }
            }
        if (stillGrouped.isNotEmpty()) {
            var next = nextRootPosition(groups, tunnels)
            val ids = stillGrouped.map { it.id }.toSet()
            tunnels = tunnels.map { tunnel ->
                if (tunnel.id in ids) tunnel.copy(groupId = null, position = next++) else tunnel
            }
        }
        tunnelRepository.saveAll(tunnels)
        tunnelGroupRepository.saveAll(groups)
        clearSelectedTunnels()
    }

    fun exportGroup(group: TunnelGroup, uri: Uri?) = intent {
        val children = tunnelsUiState.value.tunnels.filter { it.groupId == group.id }
        if (children.isEmpty()) {
            postSideEffect(
                GlobalSideEffect.Snackbar(
                    StringValue.StringResource(R.string.no_tunnels_in_group),
                    ToastType.Warning,
                )
            )
            return@intent
        }
        val (fileName, mimeType) = group.asExportFileName()
        postSideEffect(
            GlobalSideEffect.ExportFile(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                successMessage = StringValue.StringResource(R.string.export_success),
                prepareFile = { shareFile ->
                    // Always a zip, even for one tunnel, so the group name survives the export
                    val files = createConfFiles(children)
                    try {
                        fileUtils.zipAll(shareFile, files).getOrThrow()
                    } finally {
                        files.forEach { file -> if (file.exists()) file.delete() }
                    }
                },
                onComplete = {},
            )
        )
    }

    private suspend fun pingSorted(tunnels: List<TunnelConfig>): List<Pair<TunnelConfig, Double>> {
        return withContext(Dispatchers.IO) {
            tunnels
                .map { tunnel ->
                    async {
                        val config =
                            try {
                                tunnel.getConfig()
                            } catch (_: Exception) {
                                null
                            }
                        val endpoint = config?.peers?.firstOrNull()?.host
                        val latency =
                            if (endpoint != null) {
                                try {
                                    // 3 pings, a single dropped packet on an otherwise reachable
                                    // server would otherwise show as unreachable. Each tunnel is
                                    // pinged concurrently, so this doesn't multiply by tunnel count
                                    val stats = networkUtils.pingWithStats(endpoint, 3)
                                    if (stats.isReachable) stats.rttAvg else Double.MAX_VALUE
                                } catch (_: Exception) {
                                    Double.MAX_VALUE
                                }
                            } else {
                                Double.MAX_VALUE
                            }
                        tunnel to latency
                    }
                }
                .awaitAll()
                .sortedBy { it.second }
        }
    }

    fun importTunnelConfigs(configs: Map<QuickConfig, TunnelName>) = intent {
        try {
            val ui = tunnelsUiState.value
            val next = nextRootPosition(ui.groups, ui.tunnels)
            val tunnelConfigs =
                configs.entries.mapIndexed { index, (quick, name) ->
                    val config = Config.parseQuickString(quick)
                    config.validate()
                    TunnelConfig.fromConfig(config, name).copy(position = next + index)
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
            .onFailure { error ->
                val message =
                    when {
                        // Broken Android TV and legacy pickers can return file:// without a read
                        // grant
                        error.isFileAccessDenied() ->
                            StringValue.StringResource(R.string.error_no_file_explorer)
                        error is IOException ->
                            StringValue.StringResource(R.string.error_download_failed)
                        else -> StringValue.StringResource(R.string.error_file_extension)
                    }
                postSideEffect(GlobalSideEffect.Snackbar(message, ToastType.Error))
            }
    }

    fun toggleSelectAllTunnels() = intent {
        val ui = tunnelsUiState.value
        val allSelected =
            ui.selectedTunnels.size == ui.tunnels.size && ui.selectedGroups.size == ui.groups.size
        if (allSelected) {
            selectedTunnelsRepository.clear()
        } else {
            selectedTunnelsRepository.set(
                TunnelsSelection(tunnels = ui.tunnels, groups = ui.groups)
            )
        }
    }

    fun clearSelectedTunnels() = intent { selectedTunnelsRepository.clear() }

    fun toggleSelectedTunnel(tunnelId: Int) = intent {
        val ui = tunnelsUiState.value
        val selectedTuns =
            ui.selectedTunnels.toMutableList().apply {
                val removed = removeIf { it.id == tunnelId }
                if (!removed) addAll(ui.tunnels.filter { it.id == tunnelId })
            }
        selectedTunnelsRepository.set(
            TunnelsSelection(tunnels = selectedTuns, groups = ui.selectedGroups)
        )
    }

    fun toggleSelectedGroup(groupId: Int) = intent {
        val ui = tunnelsUiState.value
        val selectedGroups =
            ui.selectedGroups.toMutableList().apply {
                val removed = removeIf { it.id == groupId }
                if (!removed) addAll(ui.groups.filter { it.id == groupId })
            }
        selectedTunnelsRepository.set(
            TunnelsSelection(tunnels = ui.selectedTunnels, groups = selectedGroups)
        )
    }

    fun deleteSelectedTunnels() = intent {
        val ui = tunnelsUiState.value
        val activeTunIds =
            tunnelCoordinator.backendStatus.firstOrNull()?.activeTunnels?.map { it.key }.orEmpty()
        val selectedTuns = ui.selectedTunnels
        if (selectedTuns.any { activeTunIds.contains(it.id) })
            return@intent postSideEffect(
                GlobalSideEffect.Snackbar(
                    StringValue.StringResource(R.string.delete_active_message),
                    ToastType.Error,
                )
            )
        if (selectedTuns.isNotEmpty()) tunnelRepository.delete(selectedTuns)
        var groups = ui.groups
        var tunnels = ui.tunnels.filter { tun -> selectedTuns.none { it.id == tun.id } }
        ui.selectedGroups.forEach { group ->
            val result = ungroupKeepingOrder(groups, tunnels, group.id)
            groups = result.first
            tunnels = result.second
            tunnelGroupRepository.delete(group)
        }
        if (ui.selectedGroups.isNotEmpty()) {
            tunnelRepository.saveAll(tunnels)
            tunnelGroupRepository.saveAll(groups)
        }
        clearSelectedTunnels()
    }

    fun copySelectedTunnel() = intent {
        val selected = tunnelsUiState.value.selectedTunnels.firstOrNull() ?: return@intent
        val config = selected.getConfig()
        val ui = tunnelsUiState.value
        val copy =
            TunnelConfig.fromConfig(config, selected.name)
                .copy(
                    groupId = selected.groupId,
                    position =
                        selected.groupId?.let { nextChildPosition(ui.tunnels, it) }
                            ?: nextRootPosition(ui.groups, ui.tunnels),
                )
        tunnelRepository.saveTunnelsUniquely(listOf(copy), state.tunnelNames.map { it.value })
        clearSelectedTunnels()
    }

    fun exportSelectedTunnels(uri: Uri?) = intent {
        val ui = tunnelsUiState.value
        val selectedTunnels =
            (ui.selectedTunnels +
                    ui.selectedGroups.flatMap { group ->
                        ui.tunnels.filter { it.groupId == group.id }
                    })
                .distinctBy { it.id }
        if (selectedTunnels.isEmpty()) return@intent

        val (fileName, mimeType) = selectedTunnels.asFileExportName()
        postSideEffect(
            GlobalSideEffect.ExportFile(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                successMessage = StringValue.StringResource(R.string.export_success),
                prepareFile = { shareFile ->
                    if (selectedTunnels.size == 1) {
                        shareFile.writeText(selectedTunnels.first().quickConfig)
                        if (shareFile.length() == 0L) {
                            throw IOException("Failed to create config file")
                        }
                    } else {
                        val files = createConfFiles(selectedTunnels)
                        try {
                            fileUtils.zipAll(shareFile, files).getOrThrow()
                        } finally {
                            files.forEach { file -> if (file.exists()) file.delete() }
                        }
                    }
                },
                onComplete = { clearSelectedTunnels() },
            )
        )
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

private data class ReorderDraft(
    val tunnels: List<TunnelConfig>,
    val groups: List<TunnelGroup>,
    val scopeGroupId: Int?,
    val sortAscending: Boolean? = null,
    val originalTunnels: List<TunnelConfig>,
    val originalGroups: List<TunnelGroup>,
    val latencies: Map<Int, Double> = emptyMap(),
)

private data class TunnelsChrome(
    val loading: Boolean,
    val selectedTunCount: Int,
    val isReorderMode: Boolean,
    val reorderScopeTitle: String?,
    val hasGroups: Boolean,
    val canReorder: Boolean,
)
