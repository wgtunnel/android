package com.zaneschepke.wireguardautotunnel.ui.screens.settings.proxy.compoents

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.CustomBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.SheetOption
import com.zaneschepke.wireguardautotunnel.util.extensions.asIcon
import com.zaneschepke.wireguardautotunnel.util.extensions.asTitleString
import kotlin.enums.enumEntries

@Composable
fun AppModeBottomSheet(
    onAppModeChange: (TunnelMode) -> Unit,
    tunnelMode: TunnelMode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    CustomBottomSheet(
        enumEntries<TunnelMode>().map {
            val icon = it.asIcon()
            SheetOption(
                icon,
                label = it.asTitleString(context),
                onClick = {
                    onDismiss()
                    onAppModeChange(it)
                },
                description =
                    when (it) {
                        TunnelMode.VPN -> stringResource(R.string.vpn_desc)
                        TunnelMode.PROXY -> stringResource(R.string.local_proxy_desc)
                        TunnelMode.LOCK_DOWN -> stringResource(R.string.lockdown_desc)
                    },
                selected = tunnelMode == it,
            )
        }
    ) {
        onDismiss()
    }
}
