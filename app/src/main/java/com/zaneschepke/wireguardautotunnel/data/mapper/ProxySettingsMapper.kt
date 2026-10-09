package com.zaneschepke.wireguardautotunnel.data.mapper

import com.zaneschepke.wireguardautotunnel.data.entity.ProxySettings as Entity
import com.zaneschepke.wireguardautotunnel.domain.model.ProxySettings as Domain

fun Entity.toDomain(): Domain =
    Domain(
        id = id,
        socks5ProxyEnabled = socks5ProxyEnabled,
        socks5ProxyBindAddress = socks5ProxyBindAddress,
        httpProxyEnabled = httpProxyEnabled,
        httpProxyBindAddress = httpProxyBindAddress,
        proxyUsername = proxyUsername,
        proxyPassword = proxyPassword,
        allowSocks4 = allowSocks4,
    )

fun Domain.toEntity(): Entity =
    Entity(
        id = id,
        socks5ProxyEnabled = socks5ProxyEnabled,
        socks5ProxyBindAddress = socks5ProxyBindAddress,
        httpProxyEnabled = httpProxyEnabled,
        httpProxyBindAddress = httpProxyBindAddress,
        proxyUsername = proxyUsername,
        proxyPassword = proxyPassword,
        allowSocks4 = allowSocks4,
    )
