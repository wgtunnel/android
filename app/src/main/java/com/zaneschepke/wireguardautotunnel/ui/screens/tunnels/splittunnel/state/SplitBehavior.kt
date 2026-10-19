package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state

enum class SplitBehavior {
    ALL,
    INCLUDE,
    EXCLUDE,
}

/** An empty pair is tunnel-all. Android has no separate "split off" value. */
fun splitBehaviorOf(included: Collection<String>, excluded: Collection<String>): SplitBehavior {
    return when {
        excluded.isNotEmpty() -> SplitBehavior.EXCLUDE
        included.isNotEmpty() -> SplitBehavior.INCLUDE
        else -> SplitBehavior.ALL
    }
}
