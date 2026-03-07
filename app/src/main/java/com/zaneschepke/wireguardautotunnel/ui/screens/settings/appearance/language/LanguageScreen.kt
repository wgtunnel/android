package com.zaneschepke.wireguardautotunnel.ui.screens.settings.appearance.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.util.LocaleUtil
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import java.text.Collator
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun LanguageScreen(sharedViewModel: SharedAppViewModel = koinActivityViewModel()) {

    val appState by sharedViewModel.container.stateFlow.collectAsStateWithLifecycle()

    val collator = Collator.getInstance(Locale.current.platformLocale)
    val locales =
        LocaleUtil.supportedLocales.map {
            val tag = it.replace("_", "-")
            java.util.Locale.forLanguageTag(tag)
        }

    val sortedLocales =
        remember(locales) {
            locales.sortedWith(compareBy(collator) { it.getDisplayName(it) }).toList()
        }

    val lazyListState = rememberLazyListState()

    val selectedIndex =
        remember(appState.locale, sortedLocales) {
            if (appState.locale == LocaleUtil.OPTION_PHONE_LANGUAGE) 0
            else {
                val selectedLocale = java.util.Locale.forLanguageTag(appState.locale)
                sortedLocales.indexOfFirst {
                    it.toLanguageTag() == selectedLocale.toLanguageTag()
                } + 1
            }
        }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            lazyListState.scrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        state = lazyListState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        item {
            SurfaceRow(
                title = stringResource(R.string.automatic),
                trailing =
                    if (appState.locale == LocaleUtil.OPTION_PHONE_LANGUAGE) {
                        {
                            Icon(
                                Icons.Outlined.Check,
                                stringResource(id = R.string.selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                onClick = { sharedViewModel.setLocale(LocaleUtil.OPTION_PHONE_LANGUAGE) },
            )
        }
        items(sortedLocales, key = { it }) { locale ->
            val buttonText =
                locale.getDisplayName(locale).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                }
            SurfaceRow(
                title = buttonText,
                trailing =
                    if (appState.locale == locale.toLanguageTag()) {
                        {
                            Icon(
                                Icons.Outlined.Check,
                                stringResource(id = R.string.selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                onClick = { sharedViewModel.setLocale(locale.toLanguageTag()) },
            )
        }
    }
}
