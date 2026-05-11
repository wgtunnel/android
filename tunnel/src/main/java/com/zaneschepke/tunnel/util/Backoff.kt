package com.zaneschepke.tunnel.util

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber

suspend fun exponentialBackoffForever(
    initialDelayMs: Long = 500,
    factor: Double = 2.0,
    maxDelayMs: Long = 30_000,
    block: suspend () -> Unit,
) = coroutineScope {
    var delayMs = initialDelayMs

    while (isActive) {
        try {
            block()
            Timber.d("exponentialBackoffForever: block succeeded, exiting loop")
            return@coroutineScope
        } catch (e: Exception) {
            Timber.w(e, "Backoff operation failed, retrying in ${delayMs}ms...")

            delay(delayMs.milliseconds)

            delayMs = (delayMs * factor).toLong().coerceAtMost(maxDelayMs)
        }
    }
}
