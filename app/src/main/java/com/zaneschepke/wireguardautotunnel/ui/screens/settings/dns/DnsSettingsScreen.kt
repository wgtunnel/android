package com.zaneschepke.wireguardautotunnel.ui.screens.settings.dns

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.networkmonitor.PrivateDnsMode
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.DnsProtocol
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.dropdown.LabelledDropdown
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
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

    if (uiState.isLoading) return

    sharedViewModel.collectSideEffect { effect ->
        when (effect) {
            is LocalSideEffect.SaveChanges -> {
                viewModel.save()
            }
            else -> Unit
        }
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
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
            LabelledDropdown(
                title = stringResource(R.string.resolution_method),
                leading = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                currentValue = uiState.dnsSettings.dnsProtocol,
                onSelected = { selected -> selected?.let { viewModel.setDnsProtocol(it) } },
                options = DnsProtocol.entries,
                optionToString = { (it ?: DnsProtocol.SYSTEM).asString(context) },
            )
            AnimatedVisibility(uiState.dnsSettings.dnsProtocol != DnsProtocol.SYSTEM) {
                ConfigurationTextBox(
                    modifier =
                        Modifier.padding(horizontal = 16.dp).padding(top = 8.dp).fillMaxWidth(),
                    hint = stringResource(R.string.dns_endpoint_hint),
                    label = stringResource(R.string.dns_endpoint_label),
                    value = uiState.dnsSettings.dnsEndpoint ?: "",
                    isError = uiState.peerResolutionEndpointError != null,
                    onValueChange = viewModel::setDnsEndpoint,
                )
            }
        }
    }
}
