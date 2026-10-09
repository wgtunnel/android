package com.zaneschepke.wireguardautotunnel.util.extensions

import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Passes `true` through immediately, but only passes `false` through after the upstream has held
 * `false` continuously for [timeout] with no intervening `true`.
 *
 * The very first value collected is always passed through immediately, regardless of its value. The
 * hold only protects against a previously-confirmed `true` flapping to `false` and back.
 */
fun Flow<Boolean>.debounceFalling(timeout: Duration): Flow<Boolean> = channelFlow {
    var isFirst = true
    collectLatest { value ->
        if (value || isFirst) {
            isFirst = false
            send(value)
        } else {
            delay(timeout)
            send(false)
        }
    }
}
