package com.zaneschepke.wireguardautotunnel.ui.sideeffect

sealed class LocalSideEffect {
    data object SaveChanges : LocalSideEffect()

    data object ShowSensitive : LocalSideEffect()

    data object CopyToClipboard : LocalSideEffect()

    sealed class Sheet : LocalSideEffect() {

        data object AddMenu : Sheet()

        data object ImportTunnels : Sheet()

        data object MoveToGroup : Sheet()

        data object SelectionActions : Sheet()

        data object ReorderActions : Sheet()

        data object LoggerActions : Sheet()
    }

    data object LaunchExportPicker : LocalSideEffect()

    sealed class Modal : LocalSideEffect() {
        data object QR : Modal()

        data object SelectTunnel : Modal()

        data object RecoveryDetails : Modal()
    }

    sealed class SelectedTunnels : LocalSideEffect() {
        data object SelectAll : SelectedTunnels()

        data object Copy : SelectedTunnels()
    }
}
