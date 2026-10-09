package com.zaneschepke.wireguardautotunnel.util.extensions

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.pow
import kotlin.math.roundToInt

fun <T> List<T>.update(index: Int, item: T): List<T> = toMutableList().apply { this[index] = item }

typealias TunnelName = String?

typealias QuickConfig = String

fun <T, R : Comparable<R>> List<T>.isSortedBy(selector: (T) -> R): Boolean {
    return zipWithNext().all { (a, b) -> selector(a) <= selector(b) }
}

fun Double.round(decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return (this * factor).roundToInt() / factor
}

fun Instant.toUserFriendlyTimestamp(): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault()).format(this)

fun Throwable.isFileAccessDenied(): Boolean {
    if (this is SecurityException) return true
    return generateSequence(this) { it.cause }
        .any { error ->
            val message = error.message.orEmpty()
            message.contains("EACCES", ignoreCase = true) ||
                message.contains("Permission denied", ignoreCase = true)
        }
}
