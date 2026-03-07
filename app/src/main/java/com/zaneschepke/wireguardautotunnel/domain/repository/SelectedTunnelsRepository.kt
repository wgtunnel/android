package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SelectedTunnelsRepository {
    private val _selectedTunnelsFlow = MutableStateFlow<List<TunnelConfig>>(emptyList())
    val flow = _selectedTunnelsFlow.asStateFlow()

    fun add(tunnelConfig: TunnelConfig) {
        _selectedTunnelsFlow.update { it.toMutableList().apply { add(tunnelConfig) } }
    }

    fun remove(tunnelConfig: TunnelConfig) {
        _selectedTunnelsFlow.update { it.toMutableList().apply { remove(tunnelConfig) } }
    }

    fun clear() {
        _selectedTunnelsFlow.update { emptyList() }
    }

    fun set(tunnelConfigs: List<TunnelConfig>) {
        _selectedTunnelsFlow.update { tunnelConfigs }
    }
}
