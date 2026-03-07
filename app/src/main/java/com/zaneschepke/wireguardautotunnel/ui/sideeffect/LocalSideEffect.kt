package com.zaneschepke.wireguardautotunnel.ui.sideeffect

import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig

sealed class LocalSideEffect {
    data object Sort : LocalSideEffect()
    data object SortByLatency : LocalSideEffect()
    data class LatencySortFinished(
        val tunnels: List<TunnelConfig>,
        val latencies: Map<Int, Double>,
    ) : LocalSideEffect()

    data object SaveChanges : LocalSideEffect()

    sealed class Sheet : LocalSideEffect() {

        data object ImportTunnels : Sheet()

        data object ExportTunnels : Sheet()

        data object LoggerActions : Sheet()
    }

    sealed class Modal : LocalSideEffect() {
        data object QR : Modal()

        data object DeleteTunnels : Modal()

        data object SelectTunnel : Modal()
    }

    sealed class SelectedTunnels : LocalSideEffect() {
        data object SelectAll : SelectedTunnels()

        data object Copy : SelectedTunnels()
    }
}
