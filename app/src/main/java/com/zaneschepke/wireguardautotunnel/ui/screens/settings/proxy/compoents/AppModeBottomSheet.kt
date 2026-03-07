package com.zaneschepke.wireguardautotunnel.ui.screens.settings.proxy.compoents

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.CustomBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.SheetOption
import com.zaneschepke.wireguardautotunnel.util.extensions.asIcon
import com.zaneschepke.wireguardautotunnel.util.extensions.asTitleString
import com.zaneschepke.wireguardautotunnel.util.extensions.description

@Composable
fun AppModeBottomSheet(
    onAppModeChange: (AppMode) -> Unit,
    appMode: AppMode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isTv = LocalIsAndroidTV.current

    CustomBottomSheet(
        enumValues<AppMode>()
            .filterNot { isTv && it == AppMode.KERNEL }
            .map {
                val icon = it.asIcon()
                SheetOption(
                    icon,
                    label = it.asTitleString(context),
                    onClick = {
                        onDismiss()
                        onAppModeChange(it)
                    },
                    selected = appMode == it,
                    description = it.description(context),
                )
            }
    ) {
        onDismiss()
    }
}
