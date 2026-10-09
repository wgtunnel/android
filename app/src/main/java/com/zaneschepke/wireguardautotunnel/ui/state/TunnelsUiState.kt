package com.zaneschepke.wireguardautotunnel.ui.state

import com.wgtunnel.backend.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelGroup

data class TunnelsUiState(
    val tunnels: List<TunnelConfig> = emptyList(),
    val groups: List<TunnelGroup> = emptyList(),
    val backendStatus: BackendStatus = BackendStatus(),
    val selectedTunnels: List<TunnelConfig> = emptyList(),
    val selectedGroups: List<TunnelGroup> = emptyList(),
    val displayStates: Map<Int, DisplayTunnelState> = emptyMap(),
    val isLoading: Boolean = true,
    val isReorderMode: Boolean = false,
    val reorderScopeGroupId: Int? = null,
    val reorderLatencies: Map<Int, Double> = emptyMap(),
) {
    val isSelectionMode: Boolean
        get() = selectedTunnels.isNotEmpty() || selectedGroups.isNotEmpty()

    val selectedCount: Int
        get() = selectedTunnels.size + selectedGroups.size

    val canUngroup: Boolean
        get() {
            if (selectedTunnels.isEmpty() && selectedGroups.isEmpty()) return false
            if (selectedTunnels.any { it.groupId == null }) return false
            return selectedTunnels.isNotEmpty() || selectedGroups.isNotEmpty()
        }

    val canMoveToGroup: Boolean
        get() = selectedTunnels.isNotEmpty()

    val canCopy: Boolean
        get() = selectedTunnels.size == 1 && selectedGroups.isEmpty()

    val tunnelsForExport: List<TunnelConfig>
        get() =
            (selectedTunnels +
                    selectedGroups.flatMap { group -> tunnels.filter { it.groupId == group.id } })
                .distinctBy { it.id }

    val reorderScopeGroup: TunnelGroup?
        get() = reorderScopeGroupId?.let { id -> groups.firstOrNull { it.id == id } }

    val rows: List<TunnelListRow>
        get() =
            buildTunnelListRows(
                groups = groups,
                tunnels = tunnels,
                activeTunnelIds = backendStatus.activeTunnels.keys,
                collapseGroups = isReorderMode && reorderScopeGroupId == null,
                scopeGroupId = if (isReorderMode) reorderScopeGroupId else null,
            )
}
