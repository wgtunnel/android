package com.zaneschepke.wireguardautotunnel.ui.screens.support.donate.crypto

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.scrollbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberClipboardHelper
import com.zaneschepke.wireguardautotunnel.ui.screens.support.donate.crypto.components.AddressItem

@Composable
fun AddressesScreen() {
    val scrollState = rememberScrollState()
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(scrollState)
                .scrollbar(
                    state = scrollState.scrollIndicatorState,
                    orientation = Orientation.Vertical,
                ),
    ) {
        val clipboard = rememberClipboardHelper()
        Address.allAddresses.forEach { AddressItem(it) { address -> clipboard.copy(address) } }
    }
}
