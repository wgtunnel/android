package com.zaneschepke.tunnel.backend.dns

import com.zaneschepke.tunnel.model.DnsBootstrapResult

interface PeerResolver {
    suspend fun resolve(host: String): DnsBootstrapResult
}
