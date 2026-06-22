package com.zaneschepke.tunnel

import androidx.annotation.Keep

@Keep
internal fun interface StatusCallback {

    fun onStatusChanged(handle: Int, statusCode: Int)
}
