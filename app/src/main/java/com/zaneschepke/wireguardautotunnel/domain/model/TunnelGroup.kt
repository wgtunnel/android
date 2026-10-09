package com.zaneschepke.wireguardautotunnel.domain.model

data class TunnelGroup(
    val id: Int = 0,
    val name: String,
    val position: Int = 0,
    val expanded: Boolean = true,
)
