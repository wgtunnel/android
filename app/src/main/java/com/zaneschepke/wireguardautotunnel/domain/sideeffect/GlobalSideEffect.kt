package com.zaneschepke.wireguardautotunnel.domain.sideeffect

import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.util.StringValue
import java.io.File

sealed class GlobalSideEffect {

    data class Snackbar(
        val message: StringValue,
        val type: ToastType,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
        val durationMs: Long? = null,
    ) : GlobalSideEffect()

    data object PopBackStack : GlobalSideEffect()

    data class LaunchUrl(val url: String) : GlobalSideEffect()

    data object ConfigChanged : GlobalSideEffect()

    data class RequestVpnPermission(val requestingMode: TunnelMode, val config: TunnelConfig?) :
        GlobalSideEffect()

    data class InstallApk(val apk: File) : GlobalSideEffect()
}
