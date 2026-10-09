package com.zaneschepke.wireguardautotunnel.ui.screens.settings.dns

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.networkmonitor.PrivateDnsMode
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.constants.TunnelDns
import com.zaneschepke.wireguardautotunnel.domain.enums.BootstrapDnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.enums.ForeignDnsPolicy
import com.zaneschepke.wireguardautotunnel.domain.enums.SplitDnsSuffixTarget
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsMode
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsProtocol
import com.zaneschepke.wireguardautotunnel.ui.common.banner.WarningBanner
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.rememberRestartToApplyChanges
import com.zaneschepke.wireguardautotunnel.ui.common.dropdown.LabeledDropdown
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.dns.components.TunnelDnsModeBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.util.extensions.launchNetworkAndInternetSettings
import com.zaneschepke.wireguardautotunnel.viewmodel.DnsViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun DnsSettingsScreen(
    viewModel: DnsViewModel = koinViewModel(),
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.collectAsState()
    var showAppModeSheet by rememberSaveable { mutableStateOf(false) }

    if (uiState.isLoading) return

    val requestApply =
        rememberRestartToApplyChanges(
            needsRestart = uiState.hasActiveTunnel,
            onSave = viewModel::save,
        )

    sharedViewModel.collectSideEffect { effect ->
        when (effect) {
            is LocalSideEffect.SaveChanges -> requestApply()
            else -> Unit
        }
    }

    if (showAppModeSheet) {
        TunnelDnsModeBottomSheet(
            onTunnelDnsModeChange = viewModel::setTunnelDnsMode,
            uiState.dnsSettings.tunnelDnsMode,
        ) {
            showAppModeSheet = false
        }
    }

    val privateDnsEnabled =
        uiState.systemDnsInfo?.privateDnsMode?.let { it != PrivateDnsMode.OFF } == true
    val tunnelDnsActive = uiState.dnsSettings.tunnelDnsMode != TunnelDnsMode.Off

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
    ) {
        WarningBanner(
            title = stringResource(R.string.private_dns_enabled_warning),
            visible = privateDnsEnabled && tunnelDnsActive,
            trailing = {
                TextButton({ context.launchNetworkAndInternetSettings() }) {
                    Text(
                        stringResource(R.string.fix),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            onClick = { context.launchNetworkAndInternetSettings() },
        )
        Column {
            GroupLabel(stringResource(R.string.system), Modifier.padding(horizontal = 16.dp))

            SurfaceRow(
                leading = { Icon(Icons.Outlined.NetworkCheck, contentDescription = null) },
                title = stringResource(R.string.current_system_dns),
                description = {
                    val dnsInfo = uiState.systemDnsInfo

                    val descriptionText =
                        if (dnsInfo == null) {
                            stringResource(R.string.no_system_dns_information)
                        } else {
                            when (dnsInfo.privateDnsMode) {
                                PrivateDnsMode.OFF -> {
                                    if (dnsInfo.servers.isNotEmpty()) {
                                        stringResource(
                                            R.string.system_dns_servers,
                                            dnsInfo.servers.joinToString(", "),
                                        )
                                    } else {
                                        stringResource(R.string.no_system_dns_detected)
                                    }
                                }

                                PrivateDnsMode.AUTOMATIC -> {
                                    buildString {
                                        append(stringResource(R.string.private_dns_automatic))

                                        append("\n")

                                        append(
                                            if (dnsInfo.servers.isNotEmpty()) {
                                                stringResource(
                                                    R.string.system_dns_servers,
                                                    dnsInfo.servers.joinToString(", "),
                                                )
                                            } else {
                                                stringResource(R.string.no_system_dns_detected)
                                            }
                                        )
                                    }
                                }

                                PrivateDnsMode.HOSTNAME -> {
                                    stringResource(
                                        R.string.private_dns_hostname,
                                        dnsInfo.privateDnsHostname
                                            ?: stringResource(R.string.unknown),
                                    )
                                }
                            }
                        }

                    DescriptionText(descriptionText)
                },
            )
        }
        Column {
            GroupLabel(
                stringResource(R.string.peer_resolution),
                Modifier.padding(horizontal = 16.dp),
            )
            LabeledDropdown(
                title = stringResource(R.string.resolution_method),
                leading = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                currentValue = uiState.dnsSettings.bootstrapDnsProtocol,
                onSelected = { selected ->
                    selected?.let { viewModel.setBootstrapDnsProtocol(it) }
                },
                options = BootstrapDnsProtocol.entries,
                optionToString = { (it ?: BootstrapDnsProtocol.SYSTEM).asString(context) },
            )
            AnimatedVisibility(
                uiState.dnsSettings.bootstrapDnsProtocol != BootstrapDnsProtocol.SYSTEM
            ) {
                ConfigurationTextBox(
                    modifier =
                        Modifier.padding(horizontal = 16.dp).padding(top = 8.dp).fillMaxWidth(),
                    hint = stringResource(R.string.dns_endpoint_hint),
                    label = stringResource(R.string.dns_endpoint_label),
                    value = uiState.dnsSettings.bootstrapDnsEndpoint ?: "",
                    isError = uiState.bootstrapEndpointError != null,
                    onValueChange = viewModel::setBootstrapDnsEndpoint,
                )
            }
        }
        Column {
            GroupLabel(stringResource(R.string.tunnel_dns), Modifier.padding(horizontal = 16.dp))
            SurfaceRow(
                leading = {
                    Icon(ImageVector.vectorResource(R.drawable.sdk), contentDescription = null)
                },
                trailing = { modifier ->
                    IconButton(onClick = { showAppModeSheet = true }, modifier) {
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = stringResource(R.string.select),
                        )
                    }
                },
                title = stringResource(R.string.tunnel_dns_mode),
                description = {
                    DescriptionText(
                        stringResource(
                            R.string.current_template,
                            uiState.dnsSettings.tunnelDnsMode.asString(context),
                        )
                    )
                },
                onClick = { showAppModeSheet = true },
            )
            AnimatedVisibility(
                uiState.dnsSettings.tunnelDnsMode in
                    setOf(TunnelDnsMode.Encrypted, TunnelDnsMode.Split, TunnelDnsMode.AllLocal)
            ) {
                Column {
                    val isSplitMode = uiState.dnsSettings.tunnelDnsMode.isSplitMode()
                    val showCustomServer =
                        !isSplitMode ||
                            !(uiState.dnsSettings.useTunnelDnsServersInSplit &&
                                uiState.dnsSettings.tunnelDnsProtocol == TunnelDnsProtocol.Plain)
                    if (uiState.dnsSettings.tunnelDnsMode != TunnelDnsMode.AllLocal) {
                        LabeledDropdown(
                            title = stringResource(R.string.protocol),
                            leading = { Icon(Icons.Outlined.Router, contentDescription = null) },
                            currentValue = uiState.dnsSettings.tunnelDnsProtocol,
                            onSelected = { selected ->
                                selected?.let { viewModel.setTunnelDnsProtocol(it) }
                            },
                            options =
                                if (isSplitMode) TunnelDnsProtocol.entries
                                else
                                    TunnelDnsProtocol.entries.filter {
                                        it != TunnelDnsProtocol.Plain
                                    },
                            optionToString = { (it ?: TunnelDnsProtocol.Doh).asString(context) },
                        )
                    }
                    if (
                        isSplitMode &&
                            uiState.dnsSettings.tunnelDnsProtocol == TunnelDnsProtocol.Plain
                    ) {
                        SurfaceRow(
                            leading = { Icon(Icons.Outlined.Route, null) },
                            title = stringResource(R.string.use_tunnel_dns_servers),
                            trailing = {
                                ThemedSwitch(
                                    checked = uiState.dnsSettings.useTunnelDnsServersInSplit,
                                    onClick = viewModel::setUseTunnelDnsServersInSplit,
                                )
                            },
                            onClick = {
                                viewModel.setUseTunnelDnsServersInSplit(
                                    !uiState.dnsSettings.useTunnelDnsServersInSplit
                                )
                            },
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (
                            showCustomServer &&
                                uiState.dnsSettings.tunnelDnsMode != TunnelDnsMode.AllLocal
                        ) {
                            ConfigurationTextBox(
                                modifier =
                                    Modifier.padding(horizontal = 16.dp)
                                        .padding(top = 8.dp)
                                        .fillMaxWidth(),
                                hint = stringResource(R.string.dns_endpoint_hint),
                                label = stringResource(R.string.dns_endpoint_label),
                                value = uiState.dnsSettings.tunnelDnsEndpoint ?: "",
                                isError = uiState.tunnelEndpointError != null,
                                onValueChange = viewModel::setTunnelDnsEndpoint,
                            )
                        }
                        AnimatedVisibility(isSplitMode) {
                            Column {
                                ConfigurationTextBox(
                                    modifier =
                                        Modifier.padding(horizontal = 16.dp)
                                            .padding(top = 8.dp)
                                            .fillMaxWidth(),
                                    hint = TunnelDns.DEFAULT_SPLIT_SUFFIXES.joinToString(),
                                    label = stringResource(R.string.domain_suffixes),
                                    value = uiState.dnsSettings.localSuffixes ?: "",
                                    isError = uiState.localSuffixesError != null,
                                    onValueChange = viewModel::setLocalDomainSuffixes,
                                )
                                LabeledDropdown(
                                    title = stringResource(R.string.split_suffix_target),
                                    leading = {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.AltRoute,
                                            contentDescription = null,
                                        )
                                    },
                                    currentValue = uiState.dnsSettings.splitSuffixTarget,
                                    onSelected = { selected ->
                                        selected?.let { viewModel.setSplitSuffixTarget(it) }
                                    },
                                    options = SplitDnsSuffixTarget.entries,
                                    optionToString = {
                                        (it ?: SplitDnsSuffixTarget.System).asString(context)
                                    },
                                )
                                DescriptionText(
                                    uiState.dnsSettings.splitSuffixTarget.asDescription(context),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                    LabeledDropdown(
                        title = stringResource(R.string.transit_dns_policy),
                        leading = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                        currentValue = uiState.dnsSettings.foreignDnsPolicy,
                        onSelected = { selected ->
                            selected?.let { viewModel.setForeignDnsPolicy(it) }
                        },
                        options = ForeignDnsPolicy.entries,
                        optionToString = { (it ?: ForeignDnsPolicy.Redirect).asString(context) },
                    )
                    DescriptionText(
                        stringResource(R.string.transit_dns_policy_desc),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}
