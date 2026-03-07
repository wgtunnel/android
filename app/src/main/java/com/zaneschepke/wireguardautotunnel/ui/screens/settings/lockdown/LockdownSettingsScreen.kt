package com.zaneschepke.wireguardautotunnel.ui.screens.settings.lockdown

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.viewmodel.LockdownViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun LockdownSettingsScreen(
    viewModel: LockdownViewModel = koinViewModel(),
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {

    val uiState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (uiState.isLoading) return

    var metered by remember { mutableStateOf(uiState.lockdownSettings.metered) }
    var dualStack by remember { mutableStateOf(uiState.lockdownSettings.dualStack) }
    var bypassLan by remember { mutableStateOf(uiState.lockdownSettings.bypassLan) }

    sharedViewModel.collectSideEffect {
        if (it is LocalSideEffect.SaveChanges) viewModel.setShowSaveModal(true)
    }

    if (uiState.showSaveModal) {
        InfoDialog(
            onDismiss = { viewModel.setShowSaveModal(false) },
            onAttest = {
                viewModel.setLockdownSettings(
                    uiState.lockdownSettings.copy(
                        metered = metered,
                        dualStack = dualStack,
                        bypassLan = bypassLan,
                    )
                )
            },
            title = stringResource(R.string.save_changes),
            body = {
                Text(
                    stringResource(
                        R.string.restart_message_template,
                        stringResource(R.string.kill_switch),
                    )
                )
            },
            confirmText = stringResource(R.string._continue),
        )
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Column {
            GroupLabel(
                stringResource(R.string.configuration),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Lan, contentDescription = null) },
                title = stringResource(R.string.allow_lan_traffic),
                description = {
                    Text(
                        text = stringResource(R.string.bypass_lan_for_kill_switch),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                MaterialTheme.colorScheme.outline
                            ),
                    )
                },
                trailing = { ThemedSwitch(checked = bypassLan, onClick = { bypassLan = it }) },
                onClick = { bypassLan = !bypassLan },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.DataUsage, contentDescription = null) },
                    title = stringResource(R.string.metered_tunnel),
                    trailing = { ThemedSwitch(checked = metered, onClick = { metered = it }) },
                    onClick = { metered = !metered },
                )
            }
            SurfaceRow(
                leading = {
                    Icon(ImageVector.vectorResource(R.drawable.host), contentDescription = null)
                },
                title = stringResource(R.string.dual_stack),
                description = { DescriptionText(stringResource(R.string.dual_stack_description)) },
                trailing = { ThemedSwitch(checked = dualStack, onClick = { dualStack = it }) },
                onClick = { dualStack = !dualStack },
            )
        }
    }
}
