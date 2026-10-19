package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.state.AppliedSplitApps
import com.zaneschepke.wireguardautotunnel.ui.state.SplitAppListSummary

@Composable
fun splitAppsLabel(summary: SplitAppListSummary): String {
    return when (summary) {
        is SplitAppListSummary.Tunneled ->
            pluralStringResource(R.plurals.apps_tunneled, summary.count, summary.count)
        is SplitAppListSummary.Bypassed ->
            pluralStringResource(R.plurals.apps_bypassed, summary.count, summary.count)
        SplitAppListSummary.AllTunneled -> stringResource(R.string.all_apps_tunneled)
    }
}

@Composable
fun SplitAppsDescription(applied: AppliedSplitApps) {
    val label = splitAppsLabel(applied.summary)
    val text =
        if (applied.managedGlobally) {
            stringResource(R.string.managed_globally_detail, label)
        } else {
            label
        }
    DescriptionText(text)
}
