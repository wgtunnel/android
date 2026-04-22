package com.zaneschepke.tunnel

fun interface StatusCallback {

    fun onStatusChanged(handle: Int, interfaceName: String, statusCode: Int)
}