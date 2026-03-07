package com.zaneschepke.wireguardautotunnel.domain.events

import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.util.StringValue

sealed class BackendCoreException : Exception() {
    abstract val stringRes: Int

    fun toStringValue(): StringValue {
        return StringValue.StringResource(stringRes)
    }
}

class DnsFailure : BackendCoreException() {
    override val stringRes = R.string.dns_resolve_error
}

class VpnUnauthorized : BackendCoreException() {
    override val stringRes = R.string.auth_error
}

class InvalidConfig : BackendCoreException() {
    override val stringRes = R.string.config_error
}

class KernelTunnelName(override val stringRes: Int) : BackendCoreException() {}

class NotAuthorized : BackendCoreException() {
    override val stringRes = R.string.auth_error
}

class ServiceNotRunning : BackendCoreException() {
    override val stringRes = R.string.service_running_error
}

class UnknownError : BackendCoreException() {
    override val stringRes = R.string.unknown_error
}

class UapiUpdateFailed : BackendCoreException() {
    override val stringRes = R.string.active_tunnel_update_failed
}

class KernelWireguardNotSupported : BackendCoreException() {
    override val stringRes = R.string.kernel_wireguard_unsupported
}
