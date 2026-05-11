package com.zaneschepke.tunnel.state

import com.zaneschepke.tunnel.model.DnsBoostrapMode

data class DnsState(
    val bootstrapMode: DnsBoostrapMode = DnsBoostrapMode.System,
    val currentUpstream: String? = null,
    val lastError: String? = null,
    val isResolving: Boolean = false,
)
