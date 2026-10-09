package com.zaneschepke.wireguardautotunnel.util.extensions

import java.util.Locale
import timber.log.Timber

fun String.abbreviateKey(prefixLength: Int = 6): String {
    val full = this
    return if (full.length > prefixLength * 2 + 3) {
        "${full.take(prefixLength)}...${full.takeLast(prefixLength)}"
    } else {
        full
    }
}

fun String.capitalize(locale: Locale): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

fun List<String>.isMatchingToWildcardList(value: String): Boolean {
    val excludeValues =
        this.filter { it.startsWith("!") }.map { it.removePrefix("!").transformWildcardsToRegex() }
    Timber.d("Excluded values: $excludeValues")
    val includedValues = this.filter { !it.startsWith("!") }.map { it.transformWildcardsToRegex() }
    Timber.d("Included values: $includedValues")
    val matches = includedValues.filter { it.matches(value) }
    val excludedMatches = excludeValues.filter { it.matches(value) }
    Timber.d("Excluded matches: $excludedMatches")
    Timber.d("Matches: $matches")
    return matches.isNotEmpty() && excludedMatches.isEmpty()
}

fun String.transformWildcardsToRegex(): Regex {
    return this.replaceUnescapedChar("*", ".*").replaceUnescapedChar("?", ".").toRegex()
}

fun String.replaceUnescapedChar(charToReplace: String, replacement: String): String {
    val escapedChar = Regex.escape(charToReplace)
    val regex = "(?<!\\\\)(?<!(?<!\\\\)\\\\)($escapedChar)".toRegex()
    return regex.replace(this) { matchResult ->
        if (
            matchResult.range.first == 0 ||
                this[matchResult.range.first - 1] != '\\' ||
                (matchResult.range.first > 1 && this[matchResult.range.first - 2] == '\\')
        ) {
            replacement
        } else {
            matchResult.value
        }
    }
}

fun Iterable<String>.joinAndTrim(): String {
    return this.joinToString(", ").trim()
}

fun String.toTrimmedList(): List<String> {
    return this.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

inline fun String?.ifNotBlank(block: (String) -> Unit): String? {
    if (!isNullOrBlank()) {
        block(this)
    }
    return this
}

fun String.isTextTooLargeForQr(maxBytes: Int = 1500): Boolean {
    return toByteArray(Charsets.UTF_8).size > maxBytes
}
