package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.CustomBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.SheetOption
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.SplitBehavior

@Composable
fun SplitBehaviorOptions(
    activeBehavior: SplitBehavior,
    onBehaviorChange: (SplitBehavior) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    if (showSheet) {
        SplitBehaviorBottomSheet(
            activeBehavior = activeBehavior,
            onBehaviorChange = onBehaviorChange,
            onDismiss = { showSheet = false },
        )
    }
    SurfaceRow(
        leading = { Icon(activeBehavior.icon(), contentDescription = null) },
        trailing = { modifier ->
            IconButton(onClick = { showSheet = true }, modifier) {
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.select),
                )
            }
        },
        title = stringResource(R.string.split_mode),
        description = { DescriptionText(activeBehavior.label()) },
        onClick = { showSheet = true },
        modifier = modifier,
    )
}

@Composable
private fun SplitBehaviorBottomSheet(
    activeBehavior: SplitBehavior,
    onBehaviorChange: (SplitBehavior) -> Unit,
    onDismiss: () -> Unit,
) {
    CustomBottomSheet(
        SplitBehavior.entries.map { behavior ->
            SheetOption(
                leadingIcon = behavior.icon(),
                label = behavior.label(),
                onClick = {
                    onDismiss()
                    onBehaviorChange(behavior)
                },
                description = behavior.description(),
                selected = activeBehavior == behavior,
            )
        },
        title = stringResource(R.string.split_mode),
        description = stringResource(R.string.split_mode_desc),
    ) {
        onDismiss()
    }
}

@Composable
private fun SplitBehavior.label(): String =
    stringResource(
        when (this) {
            SplitBehavior.ALL -> R.string.split_tunnel_all_label
            SplitBehavior.INCLUDE -> R.string.split_tunnel_include_label
            SplitBehavior.EXCLUDE -> R.string.split_tunnel_exclude_label
        }
    )

@Composable
private fun SplitBehavior.description(): String =
    stringResource(
        when (this) {
            SplitBehavior.ALL -> R.string.split_tunnel_all_desc
            SplitBehavior.INCLUDE -> R.string.split_tunnel_include_desc
            SplitBehavior.EXCLUDE -> R.string.split_tunnel_exclude_desc
        }
    )

private fun SplitBehavior.icon(): ImageVector =
    when (this) {
        SplitBehavior.ALL -> Icons.Outlined.Apps
        SplitBehavior.INCLUDE -> Icons.Outlined.VpnKey
        SplitBehavior.EXCLUDE -> Icons.Outlined.LockOpen
    }
