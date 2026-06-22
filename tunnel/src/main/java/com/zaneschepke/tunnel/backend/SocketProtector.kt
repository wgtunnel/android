package com.zaneschepke.tunnel.backend

internal interface SocketProtector {
    fun bypass(fd: Int): Int
}
