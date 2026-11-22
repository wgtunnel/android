package com.zaneschepke.wireguardautotunnel.ui.screens.settings.amnezia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.banner.InfoNotice
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.rememberRestartToApplyChanges
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.screens.pin.LocalAppLocked
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components.AmneziaSection
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components.AmneziaToolMenuItems
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.viewmodel.GlobalAmneziaViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun GlobalAmneziaScreen(
    viewModel: GlobalAmneziaViewModel = koinViewModel(),
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val uiState by viewModel.collectAsState()
    if (uiState.isLoading) return

    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val requestApply =
        rememberRestartToApplyChanges(
            needsRestart = uiState.tunnelActive && uiState.enabled,
            onSave = viewModel::save,
        )

    sharedViewModel.collectSideEffect { effect ->
        if (effect is LocalSideEffect.SaveChanges) requestApply()
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
    ) {
        InfoNotice(
            title = stringResource(R.string.amnezia_options_saved_until_enabled),
            visible = !uiState.enabled,
        )
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
            ) {
                GroupLabel(stringResource(R.string.amnezia_for_all_tunnels))
                Column {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.quick_actions),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded && !LocalAppLocked.current,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    ) {
                        AmneziaToolMenuItems(
                            compatibilitySet =
                                uiState.interfaceDraft.isAmneziaCompatibilityModeSet(),
                            onToggleCompatibility = {
                                menuExpanded = false
                                viewModel.toggleAmneziaCompat()
                            },
                            onMimic = { mimic ->
                                menuExpanded = false
                                viewModel.onMimic(mimic)
                            },
                        )
                    }
                }
            }
            AmneziaSection(
                interfaceState = uiState.interfaceDraft,
                onInterfaceChange = viewModel::onInterfaceChange,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
