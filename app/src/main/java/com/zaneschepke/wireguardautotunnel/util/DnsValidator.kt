package com.zaneschepke.wireguardautotunnel.util

import com.zaneschepke.wireguardautotunnel.domain.enums.DnsProtocol

object DnsValidator {

    private const val DEFAULT_DOT_PORT = 853
    private const val DEFAULT_DNS_PORT = 53

    sealed class Result {
        data object Valid : Result()

        data class Invalid(val error: DnsError) : Result()
    }

    fun normalize(protocol: DnsProtocol, input: String?): String {
        val value = input?.trim().orEmpty()

        if (value.isEmpty()) return value

        return when (protocol) {
            DnsProtocol.SYSTEM -> value
            DnsProtocol.DOH -> normalizeDoH(value)
            DnsProtocol.DOT -> normalizeDoT(value)
            DnsProtocol.UDP -> normalizeUdp(value)
        }
    }

    fun validate(protocol: DnsProtocol, endpoint: String?): Result {
        if (protocol == DnsProtocol.SYSTEM) return Result.Valid

        val value = endpoint?.trim().orEmpty()
        if (value.isEmpty()) {
            return Result.Invalid(DnsError.Empty)
        }

        return when (protocol) {
            DnsProtocol.SYSTEM -> Result.Valid
            DnsProtocol.DOH -> validateDoH(value)
            DnsProtocol.DOT -> validateDoT(value)
            DnsProtocol.UDP -> validateUdp(value)
        }
    }

    private fun validateDoH(value: String): Result {
        return try {
            val uri = java.net.URI(value)

            if (uri.scheme != "https") {
                return Result.Invalid(DnsError.InvalidScheme)
            }

            if (uri.host.isNullOrBlank()) {
                return Result.Invalid(DnsError.InvalidHost)
            }

            Result.Valid
        } catch (_: Exception) {
            Result.Invalid(DnsError.InvalidUrl)
        }
    }

    private fun validateDoT(value: String): Result {
        val parts = value.split(":")

        val host = parts.getOrNull(0)?.trim()
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 853

        if (host.isNullOrBlank()) {
            return Result.Invalid(DnsError.InvalidHost)
        }

        if (!isValidHostOrIp(host)) {
            return Result.Invalid(DnsError.InvalidIpOrHost)
        }

        if (port !in 1..65535) {
            return Result.Invalid(DnsError.InvalidPort)
        }

        return Result.Valid
    }

    private fun validateUdp(value: String): DnsValidator.Result {
        val parts = value.split(":")

        val host = parts.getOrNull(0)?.trim()
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 53

        if (host.isNullOrBlank()) {
            return DnsValidator.Result.Invalid(DnsError.InvalidHost)
        }

        // basic IP/hostname sanity check
        if (!isValidHostOrIp(host)) {
            return DnsValidator.Result.Invalid(DnsError.InvalidIpOrHost)
        }

        if (port !in 1..65535) {
            return DnsValidator.Result.Invalid(DnsError.InvalidPort)
        }

        return DnsValidator.Result.Valid
    }

    private fun isValidHostOrIp(value: String): Boolean {
        return isValidIpv4(value) || isValidHostname(value)
    }

    private fun isValidIpv4(value: String): Boolean {
        val parts = value.split(".")
        if (parts.size != 4) return false

        return parts.all { it.toIntOrNull()?.let { num -> num in 0..255 } == true }
    }

    private fun isValidHostname(value: String): Boolean {
        if (value.length > 253) return false

        val labels = value.split(".")

        return labels.all { label ->
            label.matches(Regex("^[a-zA-Z0-9-]{1,63}$")) &&
                !label.startsWith("-") &&
                !label.endsWith("-")
        }
    }

    private fun normalizeDoH(value: String): String {
        return if (value.startsWith("http://") || value.startsWith("https://")) {
            value
        } else {
            "https://$value"
        }
    }

    private fun normalizeDoT(value: String): String {
        val parts = value.split(":")

        val host = parts.getOrNull(0)?.trim().orEmpty()
        val port = parts.getOrNull(1)

        return if (port == null) {
            "$host:$DEFAULT_DOT_PORT"
        } else {
            value
        }
    }

    private fun normalizeUdp(value: String): String {
        val parts = value.split(":")

        val host = parts.getOrNull(0)?.trim().orEmpty()
        val port = parts.getOrNull(1)

        return if (port == null) {
            "$host:$DEFAULT_DNS_PORT"
        } else {
            value
        }
    }
}

sealed class DnsError {
    data object Empty : DnsError()

    data object InvalidUrl : DnsError()

    data object InvalidScheme : DnsError()

    data object InvalidHost : DnsError()

    data object InvalidPort : DnsError()

    data object InvalidIpOrHost : DnsError()
}
