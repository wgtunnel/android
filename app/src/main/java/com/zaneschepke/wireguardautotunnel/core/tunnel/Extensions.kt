//package com.zaneschepke.wireguardautotunnel.core.tunnel
//
//import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
//import com.zaneschepke.wireguardautotunnel.domain.state.TunnelState
//import kotlinx.coroutines.flow.MutableStateFlow
//
//fun Map<TunnelConfig, TunnelState>.allDown(): Boolean {
//    return this.all { it.value.status.isDown() }
//}
//
//fun Map<TunnelConfig, TunnelState>.hasActive(): Boolean {
//    return this.any { it.value.status.isUp() }
//}
//
//fun Map<TunnelConfig, TunnelState>.getValueById(id: Int): TunnelState? {
//    val key = this.keys.find { it.id == id }
//    return key?.let { this@getValueById[it] }
//}
//
//fun Map<TunnelConfig, TunnelState>.getKeyById(id: Int): TunnelConfig? {
//    return this.keys.find { it.id == id }
//}
//
//fun Map<TunnelConfig, TunnelState>.isUp(tunnelConfig: TunnelConfig): Boolean {
//    return this.getValueById(tunnelConfig.id)?.status?.isUp() ?: false
//}
//
//fun MutableStateFlow<Map<TunnelConfig, TunnelState>>.exists(id: Int): Boolean {
//    return this.value.any { it.key.id == id }
//}
//
//fun MutableStateFlow<Map<TunnelConfig, TunnelState>>.isUp(id: Int): Boolean {
//    return this.value.any { it.key.id == id && it.value.status is TunnelStatus.Up }
//}
//
//fun MutableStateFlow<Map<TunnelConfig, TunnelState>>.isStarting(id: Int): Boolean {
//    return this.value.any { it.key.id == id && it.value.status == TunnelStatus.Starting }
//}
//
//fun MutableStateFlow<Map<TunnelConfig, TunnelState>>.findTunnel(id: Int): TunnelConfig? {
//    return this.value.keys.find { it.id == id }
//}
//
//private val URL_PATTERN =
//    Regex("""^([a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}:[0-9]{1,5}$""")
//
//fun String.isUrl(): Boolean {
//    return URL_PATTERN.matches(this)
//}
