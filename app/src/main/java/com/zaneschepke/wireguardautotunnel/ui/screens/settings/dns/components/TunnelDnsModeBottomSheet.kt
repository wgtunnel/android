package com.zaneschepke.wireguardautotunnel.ui.screens.settings.dns.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsMode
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.CustomBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.SheetOption
import com.zaneschepke.wireguardautotunnel.util.extensions.asIcon
import kotlin.enums.enumEntries

@Composable
fun TunnelDnsModeBottomSheet(
    onTunnelDnsModeChange: (TunnelDnsMode) -> Unit,
    tunnelDnsMode: TunnelDnsMode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    CustomBottomSheet(
        enumEntries<TunnelDnsMode>().map {
            val icon = it.asIcon()
            SheetOption(
                icon,
                label = it.asString(context),
                onClick = {
                    onDismiss()
                    onTunnelDnsModeChange(it)
                },
                description = it.asDescription(context),
                selected = tunnelDnsMode == it,
            )
        }
    ) {
        onDismiss()
    }
}
