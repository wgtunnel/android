package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.scroll.appScrollbar
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox

@Composable
fun TunnelPickerList(
    tunnels: List<TunnelConfig>,
    isSelected: (TunnelConfig) -> Boolean,
    onSelect: (TunnelConfig) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    leadingItem: (@Composable () -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered =
        remember(tunnels, query) { tunnels.filter { it.name.contains(query, ignoreCase = true) } }

    val lazyListState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        ConfigurationTextBox(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.search),
            hint = stringResource(R.string.search),
            trailing = { Icon(Icons.Outlined.Search, contentDescription = null) },
        )
        LazyColumn(
            state = lazyListState,
            modifier =
                Modifier.fillMaxSize()
                    .overscroll(rememberOverscrollEffect())
                    .appScrollbar(lazyListState.scrollIndicatorState, Orientation.Vertical),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            flingBehavior = ScrollableDefaults.flingBehavior(),
        ) {
            if (leadingItem != null) item { leadingItem() }
            items(filtered, key = { it.id }) { tunnel ->
                val selected = isSelected(tunnel)
                SurfaceRow(
                    leading = leading,
                    title = tunnel.name,
                    selected = selected,
                    trailing = {
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = { onSelect(tunnel) },
                )
            }
        }
    }
}
