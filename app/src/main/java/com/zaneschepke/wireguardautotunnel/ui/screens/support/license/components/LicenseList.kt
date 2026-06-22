package com.zaneschepke.wireguardautotunnel.ui.screens.support.license.components

import LicenseFileEntry
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.scrollbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.util.extensions.openWebUrl

@Composable
fun LicenseList(licenses: List<LicenseFileEntry>) {
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()

    LazyColumn(
        modifier =
            Modifier.fillMaxSize()
                .scrollbar(
                    state = lazyListState.scrollIndicatorState,
                    orientation = Orientation.Vertical,
                ),
        state = lazyListState,
    ) {
        items(licenses) { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier =
                    Modifier.clickable(enabled = entry.scm?.url != null) {
                            entry.scm?.url?.let { context.openWebUrl(it) }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${entry.artifactId} (${entry.version})",
                        style = MaterialTheme.typography.titleSmall,
                    )

                    entry.spdxLicenses.forEach { license ->
                        Text(
                            text = license.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                entry.scm?.url?.let { Icon(Icons.AutoMirrored.Outlined.Launch, null) }
            }
        }
    }
}
