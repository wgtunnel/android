package com.zaneschepke.networkmonitor.model

import com.zaneschepke.networkmonitor.ConnectivityState

data class StableNetworkSnapshot(
    val key: String,
    val state: ConnectivityState,
    val stableSinceMs: Long,
)
