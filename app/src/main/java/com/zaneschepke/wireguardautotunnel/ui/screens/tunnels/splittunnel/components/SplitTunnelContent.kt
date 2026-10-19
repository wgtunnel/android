package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.InstalledPackage
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.banner.InfoNotice
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.SplitBehavior
import com.zaneschepke.wireguardautotunnel.ui.state.SplitAppListSummary

@Composable
fun SplitTunnelContent(
    activeBehavior: SplitBehavior,
    selectedPackages: Set<String>,
    installedPackages: List<InstalledPackage>,
    onActiveBehaviorChange: (SplitBehavior) -> Unit,
    onAppSelectionToggle: (String, Boolean) -> Unit,
    showSystemApps: Boolean,
    globalOverride: SplitAppListSummary? = null,
) {
    val isTv = LocalIsAndroidTV.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isTv) focusRequester.requestFocus() }

    var query by rememberSaveable { mutableStateOf("") }
    val locale = Locale.current
    // Pin the selection from the first time this mode is shown.
    val showApps = activeBehavior != SplitBehavior.ALL
    val pinnedByBehavior = remember { mutableMapOf<SplitBehavior, Set<String>>() }
    val pinnedPackages =
        if (showApps) pinnedByBehavior.getOrPut(activeBehavior) { selectedPackages } else emptySet()
    val filteredAndSortedPackages =
        remember(
            installedPackages,
            query,
            pinnedPackages,
            selectedPackages,
            showSystemApps,
            locale,
        ) {
            filterAndSortPackages(
                installedPackages,
                query,
                pinnedPackages,
                selectedPackages,
                showSystemApps,
                locale,
            )
        }

    Column(modifier = Modifier.fillMaxSize()) {
        if (globalOverride != null) {
            InfoNotice(
                title = stringResource(R.string.managed_globally),
                message = stringResource(R.string.split_override_notice),
                detail = splitAppsLabel(globalOverride),
            )
        }
        SplitBehaviorOptions(
            activeBehavior = activeBehavior,
            onBehaviorChange = onActiveBehaviorChange,
            modifier = Modifier.focusRequester(focusRequester),
        )
        if (showApps) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                GroupLabel(
                    stringResource(
                        if (activeBehavior == SplitBehavior.INCLUDE) {
                            R.string.select_apps_to_include
                        } else {
                            R.string.select_apps_to_exclude
                        }
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                AppSearchField(query = query, onQueryChange = { query = it })
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredAndSortedPackages, key = { it.packageName }) { pkg ->
                    val selected = selectedPackages.contains(pkg.packageName)
                    AppListItem(
                        installedPackage = pkg,
                        isSelected = selected,
                        onToggle = { onAppSelectionToggle(pkg.packageName, it) },
                    )
                }
            }
        }
    }
}
