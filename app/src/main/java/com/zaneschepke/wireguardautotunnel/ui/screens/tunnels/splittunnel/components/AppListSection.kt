package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.InstalledPackage
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.CustomTextField
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.SplitOption

@Composable
fun AppListSection(
    splitConfig: Pair<SplitOption, Set<String>>,
    installedPackages: List<InstalledPackage>,
    onAppSelectionToggle: (String, Boolean) -> Unit,
) {

    var query by remember { mutableStateOf("") }
    val locale = Locale.current.platformLocale

    val filteredAndSortedPackages by remember {
        derivedStateOf {
            installedPackages
                .filter { pkg ->
                    query.isBlank() ||
                        pkg.name.contains(query, ignoreCase = true) ||
                        pkg.packageName.contains(query, ignoreCase = true)
                }
                .sortedWith(
                    compareByDescending<InstalledPackage> {
                            splitConfig.second.contains(it.packageName)
                        }
                        .thenBy { it.name.lowercase(locale) }
                )
        }
    }

    val inputHeight = 45.dp

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        CustomTextField(
            textStyle =
                MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
            value = query,
            onValueChange = { query = it },
            interactionSource = remember { MutableInteractionSource() },
            label = {},
            leading = { Icon(Icons.Outlined.Search, stringResource(R.string.search)) },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().height(inputHeight).padding(horizontal = 16.dp),
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(),
        )
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
            contentPadding = PaddingValues(top = 24.dp),
        ) {
            items(filteredAndSortedPackages, key = { it.packageName }) { pkg ->
                val selected = splitConfig.second.contains(pkg.packageName)
                AppListItem(
                    installedPackage = pkg,
                    isSelected = selected,
                    onToggle = { onAppSelectionToggle(pkg.packageName, it) },
                )
            }
        }
    }
}
