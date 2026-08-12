package com.zaneschepke.wireguardautotunnel.util

import com.zaneschepke.wireguardautotunnel.domain.enums.BootstrapDnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsMode
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsProtocol

object DnsValidator {

    private const val DEFAULT_DOT_PORT = 853
    private const val DEFAULT_DNS_PORT = 53

    sealed class Result {
        data object Valid : Result()

        data class Invalid(val error: DnsError) : Result()
    }

    fun normalize(protocol: BootstrapDnsProtocol, input: String?): String {
        val value = input?.trim().orEmpty()

        if (value.isEmpty()) return value

        return when (protocol) {
            BootstrapDnsProtocol.SYSTEM -> value
            BootstrapDnsProtocol.DOH -> normalizeDoH(value)
            BootstrapDnsProtocol.DOT -> normalizeDoT(value)
            BootstrapDnsProtocol.UDP -> normalizeUdp(value)
        }
    }

    fun validate(protocol: BootstrapDnsProtocol, endpoint: String?): Result {
        if (protocol == BootstrapDnsProtocol.SYSTEM) return Result.Valid

        val value = endpoint?.trim().orEmpty()
        if (value.isEmpty()) {
            return Result.Invalid(DnsError.Empty)
        }

        return when (protocol) {
            BootstrapDnsProtocol.SYSTEM -> Result.Valid
            BootstrapDnsProtocol.DOH -> validateDoH(value)
            BootstrapDnsProtocol.DOT -> validateDoT(value)
            BootstrapDnsProtocol.UDP -> validateUdp(value)
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

    private fun validateUdp(value: String): Result {
        val parts = value.split(":")

        val host = parts.getOrNull(0)?.trim()
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 53

        if (host.isNullOrBlank()) {
            return DnsValidator.Result.Invalid(DnsError.InvalidHost)
        }

        // basic IP/hostname sanity check
        if (!isValidHostOrIp(host)) {
            return Result.Invalid(DnsError.InvalidIpOrHost)
        }

        if (port !in 1..65535) {
            return Result.Invalid(DnsError.InvalidPort)
        }

        return Result.Valid
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

    fun validateTunnelEndpoint(protocol: TunnelDnsProtocol, endpoint: String?): Result {
        val asBootstrap =
            when (protocol) {
                TunnelDnsProtocol.Doh -> BootstrapDnsProtocol.DOH
                TunnelDnsProtocol.Dot -> BootstrapDnsProtocol.DOT
                TunnelDnsProtocol.Plain -> BootstrapDnsProtocol.UDP
            }
        return validate(asBootstrap, endpoint)
    }

    fun normalizeTunnelEndpoint(protocol: TunnelDnsProtocol, endpoint: String?): String {
        val asBootstrap =
            when (protocol) {
                TunnelDnsProtocol.Doh -> BootstrapDnsProtocol.DOH
                TunnelDnsProtocol.Dot -> BootstrapDnsProtocol.DOT
                TunnelDnsProtocol.Plain -> BootstrapDnsProtocol.UDP
            }
        return normalize(asBootstrap, endpoint)
    }

    fun normalizeLocalSuffixes(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input
            .split(',', '\n', ' ')
            .asSequence()
            .map { it.trim().lowercase().trim('.') }
            .filter { it.isNotEmpty() }
            .map { ".$it" }
            .distinct()
            .joinToString(",")
    }

    fun validateLocalSuffixes(mode: TunnelDnsMode, input: String?): Result {
        if (mode != TunnelDnsMode.Split) return Result.Valid

        val normalized = normalizeLocalSuffixes(input)
        if (normalized.isEmpty()) {
            return Result.Invalid(DnsError.Empty)
        }

        for (suffix in normalized.split(",")) {
            val label = suffix.removePrefix(".")
            if (label.isEmpty() || label.contains("..") || label.contains(" ")) {
                return Result.Invalid(DnsError.InvalidHost)
            }
            // single-label special-use (.local) and normal multi-label suffixes
            if (!isValidSuffix(label)) {
                return Result.Invalid(DnsError.InvalidHost)
            }
        }
        return Result.Valid
    }

    // Allows "local" and dotted domain suffixes
    private fun isValidSuffix(label: String): Boolean {
        if (label.equals("local", ignoreCase = true)) return true
        return isValidHostname(label)
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
