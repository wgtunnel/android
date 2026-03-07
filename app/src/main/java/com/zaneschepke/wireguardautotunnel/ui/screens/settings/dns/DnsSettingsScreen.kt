package com.zaneschepke.wireguardautotunnel.ui.screens.settings.dns

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.data.model.DnsProtocol
import com.zaneschepke.wireguardautotunnel.data.model.DnsProvider
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.SwitchWithDivider
import com.zaneschepke.wireguardautotunnel.ui.common.dropdown.LabelledDropdown
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.util.extensions.capitalize
import com.zaneschepke.wireguardautotunnel.viewmodel.DnsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun DnsSettingsScreen(viewModel: DnsViewModel = koinViewModel()) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val dnsUiState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (dnsUiState.isLoading) return
    val locale = Locale.current.platformLocale

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Column {
            GroupLabel(stringResource(R.string.endpoint), Modifier.padding(horizontal = 16.dp))
            LabelledDropdown(
                title = stringResource(R.string.dns_protocol),
                leading = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                currentValue = dnsUiState.dnsSettings.dnsProtocol,
                onSelected = { selected -> selected?.let { viewModel.setDnsProtocol(it) } },
                options = DnsProtocol.entries,
                optionToString = { (it ?: DnsProtocol.SYSTEM).asString(context) },
            )
            AnimatedVisibility(dnsUiState.dnsSettings.dnsProtocol != DnsProtocol.SYSTEM) {
                LabelledDropdown(
                    title = stringResource(R.string.dns_provider),
                    leading = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                    currentValue =
                        dnsUiState.dnsSettings.dnsEndpoint?.let { DnsProvider.fromAddress(it) }
                            ?: DnsProvider.CLOUDFLARE,
                    onSelected = { selected -> selected?.let { viewModel.setDnsProvider(it) } },
                    options = DnsProvider.entries,
                    optionToString = { it?.name ?: DnsProvider.CLOUDFLARE.name },
                )
            }
        }
        Column {
            GroupLabel(
                stringResource(R.string.tunnel).capitalize(locale),
                Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = {
                    Icon(ImageVector.vectorResource(R.drawable.host), contentDescription = null)
                },
                title = stringResource(R.string.global_dns_servers),
                trailing = { modifier ->
                    SwitchWithDivider(
                        checked = dnsUiState.dnsSettings.isGlobalTunnelDnsEnabled,
                        onClick = { viewModel.setGlobalTunnelDnsEnabled(it) },
                        modifier = modifier,
                    )
                },
                onClick = {
                    dnsUiState.globalConfig?.let { navController.push(Route.ConfigGlobal(it.id)) }
                },
            )
        }
    }
}
