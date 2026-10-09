package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TunnelsSelection(
    val tunnels: List<TunnelConfig> = emptyList(),
    val groups: List<TunnelGroup> = emptyList(),
) {
    val count: Int
        get() = tunnels.size + groups.size

    val isEmpty: Boolean
        get() = count == 0
}

class SelectedTunnelsRepository {
    private val _selection = MutableStateFlow(TunnelsSelection())
    val flow = _selection.asStateFlow()

    fun clear() {
        _selection.update { TunnelsSelection() }
    }

    fun set(selection: TunnelsSelection) {
        _selection.update { selection }
    }
}
