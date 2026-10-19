package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox
import com.zaneschepke.wireguardautotunnel.ui.state.EditableInterface

@Composable
fun AmneziaSection(
    interfaceState: EditableInterface,
    onInterfaceChange: (EditableInterface) -> Unit,
    globalValues: EditableInterface? = null,
) {
    val locale = Locale.current.platformLocale

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Junk packets
        ConfigurationTextBox(
            value = interfaceState.junkPacketCount,
            onValueChange = { onInterfaceChange(interfaceState.copy(junkPacketCount = it)) },
            supportingText = managedGloballySupporting(globalValues?.junkPacketCount),
            label =
                stringResource(R.string.jc) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.junk_packet_count).lowercase(locale),
                    ),
            hint = stringResource(R.string.range_hint, 1, 128),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ConfigurationTextBox(
            value = interfaceState.junkPacketMinSize,
            onValueChange = { onInterfaceChange(interfaceState.copy(junkPacketMinSize = it)) },
            supportingText = managedGloballySupporting(globalValues?.junkPacketMinSize),
            label =
                stringResource(R.string.jmin) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.junk_packet_minimum_size).lowercase(locale),
                    ),
            hint = stringResource(R.string.range_hint, 1, 1279),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ConfigurationTextBox(
            value = interfaceState.junkPacketMaxSize,
            onValueChange = { onInterfaceChange(interfaceState.copy(junkPacketMaxSize = it)) },
            supportingText = managedGloballySupporting(globalValues?.junkPacketMaxSize),
            label =
                stringResource(R.string.jmax) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.junk_packet_maximum_size).lowercase(locale),
                    ),
            hint = stringResource(R.string.range_hint, 2, 1280),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        // S1–S4
        ConfigurationTextBox(
            value = interfaceState.initPacketJunkSize,
            onValueChange = { onInterfaceChange(interfaceState.copy(initPacketJunkSize = it)) },
            supportingText = managedGloballySupporting(globalValues?.initPacketJunkSize),
            label =
                stringResource(R.string.s1) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.init_packet_junk_size).lowercase(locale),
                    ),
            hint = stringResource(R.string.range_hint, 0, 64),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ConfigurationTextBox(
            value = interfaceState.responsePacketJunkSize,
            onValueChange = { onInterfaceChange(interfaceState.copy(responsePacketJunkSize = it)) },
            supportingText = managedGloballySupporting(globalValues?.responsePacketJunkSize),
            label =
                stringResource(R.string.s2) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.response_packet_junk_size).lowercase(locale),
                    ),
            hint = stringResource(R.string.range_hint, 0, 64),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ConfigurationTextBox(
            value = interfaceState.cookiePacketJunkSize,
            onValueChange = { onInterfaceChange(interfaceState.copy(cookiePacketJunkSize = it)) },
            supportingText = managedGloballySupporting(globalValues?.cookiePacketJunkSize),
            label =
                stringResource(R.string.s3) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.cookie_packet_junk_size).lowercase(locale),
                    ),
            hint = stringResource(R.string.range_hint, 0, 928),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ConfigurationTextBox(
            value = interfaceState.transportPacketJunkSize,
            onValueChange = {
                onInterfaceChange(interfaceState.copy(transportPacketJunkSize = it))
            },
            supportingText = managedGloballySupporting(globalValues?.transportPacketJunkSize),
            label =
                stringResource(R.string.s4) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.transport_packet_junk_size).lowercase(locale),
                    ),
            hint = stringResource(R.string.range_hint, 0, 928),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        // H1–H4
        ConfigurationTextBox(
            value = interfaceState.initPacketMagicHeader,
            onValueChange = { onInterfaceChange(interfaceState.copy(initPacketMagicHeader = it)) },
            supportingText = managedGloballySupporting(globalValues?.initPacketMagicHeader),
            label =
                stringResource(R.string.h1) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.init_packet_magic_header).lowercase(locale),
                    ),
            hint = stringResource(R.string.range_hint, 1, 4),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ConfigurationTextBox(
            value = interfaceState.responsePacketMagicHeader,
            onValueChange = {
                onInterfaceChange(interfaceState.copy(responsePacketMagicHeader = it))
            },
            supportingText = managedGloballySupporting(globalValues?.responsePacketMagicHeader),
            label =
                stringResource(R.string.h2) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.response_packet_magic_header).lowercase(locale),
                    ),
            hint = stringResource(R.string.range_hint, 1, 4),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ConfigurationTextBox(
            value = interfaceState.underloadPacketMagicHeader,
            onValueChange = {
                onInterfaceChange(interfaceState.copy(underloadPacketMagicHeader = it))
            },
            supportingText = managedGloballySupporting(globalValues?.underloadPacketMagicHeader),
            label =
                stringResource(R.string.h3) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.underload_packet_magic_header).lowercase(locale),
                    ),
            hint = stringResource(R.string.range_hint, 1, 4),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ConfigurationTextBox(
            value = interfaceState.transportPacketMagicHeader,
            onValueChange = {
                onInterfaceChange(interfaceState.copy(transportPacketMagicHeader = it))
            },
            supportingText = managedGloballySupporting(globalValues?.transportPacketMagicHeader),
            label =
                stringResource(R.string.h4) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.transport_packet_magic_header).lowercase(locale),
                    ),
            hint = stringResource(R.string.range_hint, 1, 4),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        // I1–I5
        ConfigurationTextBox(
            value = interfaceState.i1,
            onValueChange = { onInterfaceChange(interfaceState.copy(i1 = it)) },
            supportingText = managedGloballySupporting(globalValues?.i1),
            label =
                stringResource(R.string.i1) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.special_junk_packet).lowercase(locale),
                    ),
            hint = stringResource(R.string.hint_template, "<b 0x1A2B3C>"),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.i2,
            onValueChange = { onInterfaceChange(interfaceState.copy(i2 = it)) },
            supportingText = managedGloballySupporting(globalValues?.i2),
            label =
                stringResource(R.string.i2) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.special_junk_packet).lowercase(locale),
                    ),
            hint = stringResource(R.string.hint_template, "<b 0x1A2B3C>"),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.i3,
            onValueChange = { onInterfaceChange(interfaceState.copy(i3 = it)) },
            supportingText = managedGloballySupporting(globalValues?.i3),
            label =
                stringResource(R.string.i3) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.special_junk_packet).lowercase(locale),
                    ),
            hint = stringResource(R.string.hint_template, "<b 0x1A2B3C>"),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.i4,
            onValueChange = { onInterfaceChange(interfaceState.copy(i4 = it)) },
            supportingText = managedGloballySupporting(globalValues?.i4),
            label =
                stringResource(R.string.i4) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.special_junk_packet).lowercase(locale),
                    ),
            hint = stringResource(R.string.hint_template, "<b 0x1A2B3C>"),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.i5,
            onValueChange = { onInterfaceChange(interfaceState.copy(i5 = it)) },
            supportingText = managedGloballySupporting(globalValues?.i5),
            label =
                stringResource(R.string.i5) +
                    " " +
                    stringResource(
                        R.string.hint_template,
                        stringResource(R.string.special_junk_packet).lowercase(locale),
                    ),
            hint = stringResource(R.string.hint_template, "<b 0x1A2B3C>"),
            modifier = Modifier.fillMaxWidth(),
        )

        // AmneziaWG 3.0+
        ConfigurationTextBox(
            value = interfaceState.headerProtectionKey,
            onValueChange = { onInterfaceChange(interfaceState.copy(headerProtectionKey = it)) },
            supportingText = managedGloballySupporting(globalValues?.headerProtectionKey),
            label = stringResource(R.string.header_protection_key),
            hint = stringResource(R.string.hint_template, "base64 key"),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.contentPaddingAddition,
            onValueChange = { onInterfaceChange(interfaceState.copy(contentPaddingAddition = it)) },
            supportingText = managedGloballySupporting(globalValues?.contentPaddingAddition),
            label = stringResource(R.string.content_padding_addition),
            hint = stringResource(R.string.hint_template, "0-16"),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.rekeyAfterTime,
            onValueChange = { onInterfaceChange(interfaceState.copy(rekeyAfterTime = it)) },
            supportingText = managedGloballySupporting(globalValues?.rekeyAfterTime),
            label = stringResource(R.string.rekey_after_time),
            hint = stringResource(R.string.hint_template, "120"),
            trailing = {
                Text(
                    stringResource(R.string.seconds).lowercase(locale),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 10.dp),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.rekeyTimeout,
            onValueChange = { onInterfaceChange(interfaceState.copy(rekeyTimeout = it)) },
            supportingText = managedGloballySupporting(globalValues?.rekeyTimeout),
            label = stringResource(R.string.rekey_timeout),
            trailing = {
                Text(
                    stringResource(R.string.seconds).lowercase(locale),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 10.dp),
                )
            },
            hint = stringResource(R.string.hint_template, "5"),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.rejectAfterTime,
            onValueChange = { onInterfaceChange(interfaceState.copy(rejectAfterTime = it)) },
            supportingText = managedGloballySupporting(globalValues?.rejectAfterTime),
            label = stringResource(R.string.reject_after_time),
            trailing = {
                Text(
                    stringResource(R.string.seconds).lowercase(locale),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 10.dp),
                )
            },
            hint = stringResource(R.string.hint_template, "180"),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.keepaliveTimeout,
            onValueChange = { onInterfaceChange(interfaceState.copy(keepaliveTimeout = it)) },
            supportingText = managedGloballySupporting(globalValues?.keepaliveTimeout),
            label = stringResource(R.string.keepalive_timeout),
            trailing = {
                Text(
                    stringResource(R.string.seconds).lowercase(locale),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 10.dp),
                )
            },
            hint = stringResource(R.string.hint_template, "10"),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.maxHandshakeAttempts,
            onValueChange = { onInterfaceChange(interfaceState.copy(maxHandshakeAttempts = it)) },
            supportingText = managedGloballySupporting(globalValues?.maxHandshakeAttempts),
            label = stringResource(R.string.max_handshake_attempts),
            hint = stringResource(R.string.hint_template, "18"),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.randomTrailers,
            onValueChange = { onInterfaceChange(interfaceState.copy(randomTrailers = it)) },
            supportingText = managedGloballySupporting(globalValues?.randomTrailers),
            label = stringResource(R.string.random_trailers),
            hint = stringResource(R.string.hint_template, "on, off"),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.disableCookies,
            onValueChange = { onInterfaceChange(interfaceState.copy(disableCookies = it)) },
            supportingText = managedGloballySupporting(globalValues?.disableCookies),
            label = stringResource(R.string.disable_cookies),
            hint = stringResource(R.string.hint_template, "on, off"),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
