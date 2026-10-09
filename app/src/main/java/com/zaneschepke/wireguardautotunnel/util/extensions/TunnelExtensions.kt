package com.zaneschepke.wireguardautotunnel.util.extensions

import com.wgtunnel.backend.exception.BackendException
import com.wgtunnel.backend.model.dns.TunnelDnsConfig
import com.wgtunnel.backend.util.DnsHostUtils
import com.wgtunnel.backend.util.parseDnsServersOnly
import com.wgtunnel.parser.Config
import com.wgtunnel.parser.ConfigParseException
import com.wgtunnel.parser.InterfaceSection
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsMode
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.model.DnsSettings
import com.zaneschepke.wireguardautotunnel.util.NumberUtils
import com.zaneschepke.wireguardautotunnel.util.StringValue
import java.net.URI

fun ConfigParseException.asStringValue(): StringValue {
    return StringValue.StringResource(
        R.string.config_error_template,
        this.errorType.name,
        this.field,
    )
}

fun Config.defaultName(): String {
    return this.peers[0].host ?: NumberUtils.generateRandomTunnelName()
}

fun InterfaceSection.isAmneziaEnabled(): Boolean {
    return listOfNotNull(
            jC?.toString(),
            jMin?.toString(),
            jMax?.toString(),
            s1?.toString(),
            s2?.toString(),
            s3?.toString(),
            s4?.toString(),
            h1,
            h2,
            h3,
            h4,
            i1,
            i2,
            i3,
            i4,
            i5,
            headerProtectionKey,
            contentPaddingAddition,
            rekeyAfterTime,
            rekeyTimeout,
            rejectAfterTime,
            keepaliveTimeout,
            maxHandshakeAttempts,
        )
        .any { it.isNotBlank() }
}

fun DnsSettings.toTunnelDnsConfigOrNull(config: Config): TunnelDnsConfig? {
    fun getHost(endpoint: String): String =
        TunnelDnsConfig.splitHostPort(
                endpoint.removePrefix("https://").removePrefix("http://").substringBefore("/")
            )
            ?.first
            ?: runCatching { URI(endpoint).host }.getOrNull()
            ?: endpoint.substringBefore(':')

    fun splitUpstream(): Triple<String, String?, List<String>> {
        return if (tunnelDnsProtocol == TunnelDnsProtocol.Plain && useTunnelDnsServersInSplit) {
            val endpoints = config.parseDnsServersOnly().map { DnsHostUtils.ensurePort53(it) }
            if (endpoints.isEmpty()) {
                throw BackendException.ConfigMissingDNS(
                    "Split with tunnel DNS requires DNS servers in the tunnel config"
                )
            }
            Triple("plain", null, endpoints)
        } else {
            val endpoint =
                tunnelDnsEndpoint
                    ?: throw BackendException.ConfigMissingDNS(
                        "Endpoint missing for split DNS mode"
                    )
            when (tunnelDnsProtocol) {
                TunnelDnsProtocol.Doh -> Triple("doh", getHost(endpoint), listOf(endpoint))
                TunnelDnsProtocol.Dot -> Triple("dot", getHost(endpoint), listOf(endpoint))
                TunnelDnsProtocol.Plain ->
                    Triple("plain", null, listOf(DnsHostUtils.ensurePort53(endpoint)))
            }
        }
    }

    fun parseSuffixes(): List<String> =
        localSuffixes?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    return when (tunnelDnsMode) {
        TunnelDnsMode.Off -> null
        TunnelDnsMode.AllLocal ->
            TunnelDnsConfig(
                defaultTransport = "local",
                foreignDnsPolicy = foreignDnsPolicy.toCore(),
            )

        TunnelDnsMode.Encrypted -> {
            val endpoint =
                tunnelDnsEndpoint
                    ?: throw BackendException.ConfigMissingDNS(
                        "No upstream endpoint configured for encrypted DNS mode"
                    )
            val (transport, host) =
                when (tunnelDnsProtocol) {
                    TunnelDnsProtocol.Doh -> "doh" to getHost(endpoint)
                    TunnelDnsProtocol.Dot -> "dot" to getHost(endpoint)
                    TunnelDnsProtocol.Plain ->
                        throw BackendException.ConfigMissingDNS(
                            "Plain is invalid for encrypted mode"
                        )
                }
            TunnelDnsConfig(
                defaultTransport = transport,
                upstream = listOf(endpoint),
                serverName = host,
                foreignDnsPolicy = foreignDnsPolicy.toCore(),
            )
        }

        TunnelDnsMode.Split -> {
            val (transport, host, endpoints) = splitUpstream()
            TunnelDnsConfig(
                defaultTransport = transport,
                localSuffixes = parseSuffixes(),
                upstream = endpoints,
                serverName = host,
                foreignDnsPolicy = foreignDnsPolicy.toCore(),
                splitMode = splitSuffixTarget.toCore(),
            )
        }
    }
}
