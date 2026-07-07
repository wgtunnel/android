package com.zaneschepke.wireguardautotunnel.util

object BssidUtils {

    fun normalizeBssid(input: String): String {
        var cleaned = input.trim().uppercase().replace("-", ":").replace(".", ":").replace(" ", "")

        val isBlacklist = isBlacklistRule(cleaned)
        if (isBlacklist) {
            cleaned = cleaned.removePrefix("!")
        }

        cleaned = cleaned.replace(Regex("[^A-F0-9:*]"), "")

        if (!cleaned.contains(":") && cleaned.replace("*", "").length == 12) {
            val hexPart = cleaned.replace("*", "")
            val formatted = hexPart.chunked(2).joinToString(":")
            cleaned = if (cleaned.endsWith("*")) "$formatted*" else formatted
        }

        return if (isBlacklist) "!$cleaned" else cleaned
    }

    fun isValidBssidPattern(input: String): Boolean {
        val normalized = normalizeBssid(input).lowercase()
        val pattern = normalized.removePrefix("!")

        val exact = Regex("^([a-f0-9]{2}:){5}[a-f0-9]{2}$")
        val wildcard = Regex("^([a-f0-9]{2}:){0,5}\\*$")

        return exact.matches(pattern) || wildcard.matches(pattern) || pattern == "*"
    }

    fun isBlacklistRule(rule: String): Boolean {
        return rule.trim().startsWith("!")
    }
}
