package com.zaneschepke.tunnel.backend.dns

import android.net.Network
import com.zaneschepke.tunnel.model.DnsBootstrapResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class AndroidNetworkResolver(private val network: Network) : PeerResolver {

    override suspend fun resolve(host: String): DnsBootstrapResult =
        withContext(Dispatchers.IO) {
            // use underlying network for resolution
            val ips = network.getAllByName(host)

            Timber.d("Resolution from network bind socket: ${ips.contentToString()}")

            val v4 = ips.filter { it.address.size == 4 }.map { it.hostAddress }
            val v6 = ips.filter { it.address.size == 16 }.map { it.hostAddress }

            DnsBootstrapResult(v4, v6)
        }
}
