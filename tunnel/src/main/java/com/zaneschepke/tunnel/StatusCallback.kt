package com.zaneschepke.tunnel

internal fun interface StatusCallback {

    fun onStatusChanged(handle: Int, statusCode: Int)
}
