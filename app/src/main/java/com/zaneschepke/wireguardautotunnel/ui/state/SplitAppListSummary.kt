package com.zaneschepke.wireguardautotunnel.ui.state

sealed interface SplitAppListSummary {
    data class Tunneled(val count: Int) : SplitAppListSummary

    data class Bypassed(val count: Int) : SplitAppListSummary

    data object AllTunneled : SplitAppListSummary
}

data class AppliedSplitApps(
    val summary: SplitAppListSummary,
    val managedGlobally: Boolean,
)

// An empty pair is labeled only when emptyMeansAllTunneled.
fun splitAppListSummary(
    includedCount: Int,
    excludedCount: Int,
    emptyMeansAllTunneled: Boolean,
): SplitAppListSummary? {
    return when {
        excludedCount > 0 -> SplitAppListSummary.Bypassed(excludedCount)
        includedCount > 0 -> SplitAppListSummary.Tunneled(includedCount)
        emptyMeansAllTunneled -> SplitAppListSummary.AllTunneled
        else -> null
    }
}
