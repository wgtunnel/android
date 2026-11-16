package com.zaneschepke.wireguardautotunnel.ui.common.text

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R

interface ScreenSection {
    @get:StringRes val title: Int
}

private const val MAX_LISTED_SECTIONS = 3

@Composable
fun sectionsSummary(sections: List<ScreenSection>): String {
    val names = sections.map { stringResource(it.title) }
    if (names.size <= MAX_LISTED_SECTIONS) return names.joinToString(", ")
    return stringResource(
        R.string.sections_and_more,
        names.take(MAX_LISTED_SECTIONS - 1).joinToString(", "),
    )
}
