package com.zaneschepke.tunnel.state

data class NativeTunnelStatus(val handle: Int, val code: NativeTunnelStatusCode) {
    enum class NativeTunnelStatusCode(val code: Int) {
        HEALTHY(0),
        HANDSHAKE_FAILURE(1),
        STOPPED(99);

        companion object {
            fun from(code: Int): NativeTunnelStatusCode? {
                return entries.firstOrNull { it.code == code }
            }
        }
    }
}
