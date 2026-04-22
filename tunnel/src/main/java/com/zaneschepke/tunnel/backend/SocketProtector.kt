package com.zaneschepke.tunnel.backend

interface SocketProtector {
    fun bypass(fd : Int) : Int
}