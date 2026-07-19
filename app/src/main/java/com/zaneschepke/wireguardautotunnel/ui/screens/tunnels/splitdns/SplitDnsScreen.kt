package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splitdns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.util.DnsValidator
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SplitDnsViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplitDnsScreen(
    viewModel: SplitDnsViewModel,
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val uiState by viewModel.collectAsState()

    // The field text is owned locally (synchronous) so async state emissions can't reset the
    // cursor mid-keystroke. Only committed domains flow through the view model.
    var input by
        rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

    val submitDomain = {
        val text = input.text
        viewModel.addDomain(text)
        // Clear on any valid entry (including duplicates); invalid entries stay for correction.
        if (DnsValidator.validateDomain(text) is DnsValidator.Result.Valid) {
            input = TextFieldValue("")
        }
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(waveSpeed = 60.dp, modifier = Modifier.size(48.dp))
        }
        return
    }

    sharedViewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is LocalSideEffect.SaveChanges -> viewModel.save()
            else -> Unit
        }
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            DescriptionText(stringResource(R.string.split_dns_description))
            if (!uiState.tunnelHasDnsServer) {
                DescriptionText(stringResource(R.string.split_dns_no_server_warning))
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    viewModel.onInputChanged()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.split_dns_add_domain)) },
                placeholder = { Text(stringResource(R.string.split_dns_domain_hint)) },
                singleLine = true,
                isError = uiState.inputError != null,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { submitDomain() }),
                trailingIcon = {
                    IconButton(onClick = { submitDomain() }) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.split_dns_add_domain),
                        )
                    }
                },
            )
        }

        Column {
            GroupLabel(
                stringResource(R.string.split_dns_domains),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (uiState.domains.isEmpty()) {
                DescriptionText(
                    stringResource(R.string.split_dns_empty),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                uiState.domains.forEach { domain ->
                    SurfaceRow(
                        leading = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                        title = domain,
                        trailing = { modifier ->
                            IconButton(
                                onClick = { viewModel.removeDomain(domain) },
                                modifier = modifier,
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.remove),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
