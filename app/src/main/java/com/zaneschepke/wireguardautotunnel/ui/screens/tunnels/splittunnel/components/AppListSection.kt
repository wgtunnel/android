package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.InstalledPackage
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.CustomTextField

@Composable
fun AppSearchField(query: String, onQueryChange: (String) -> Unit) {
    CustomTextField(
        textStyle =
            MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
        value = query,
        onValueChange = onQueryChange,
        interactionSource = remember { MutableInteractionSource() },
        label = {},
        leading = { Icon(Icons.Outlined.Search, stringResource(R.string.search)) },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(45.dp),
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(),
    )
}

fun filterAndSortPackages(
    installedPackages: List<InstalledPackage>,
    query: String,
    pinnedPackages: Set<String>,
    selectedPackages: Set<String>,
    showSystemApps: Boolean,
    locale: Locale,
): List<InstalledPackage> {
    val platformLocale = locale.platformLocale
    return installedPackages
        .filter { pkg ->
            val matchesQuery =
                query.isBlank() ||
                    pkg.name.contains(query, ignoreCase = true) ||
                    pkg.packageName.contains(query, ignoreCase = true)
            // A selected system app stays visible.
            val showPackage =
                showSystemApps || !pkg.isSystem || selectedPackages.contains(pkg.packageName)
            matchesQuery && showPackage
        }
        .sortedWith(
            compareByDescending<InstalledPackage> { pinnedPackages.contains(it.packageName) }
                .thenBy { it.name.lowercase(platformLocale) }
        )
}
