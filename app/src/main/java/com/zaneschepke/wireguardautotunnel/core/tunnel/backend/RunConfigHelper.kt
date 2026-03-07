package com.zaneschepke.wireguardautotunnel.core.tunnel.backend

import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.data.model.DnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.events.InvalidConfig
import com.zaneschepke.wireguardautotunnel.domain.model.DnsSettings
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.model.ProxySettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.DnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.ProxySettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import java.util.Optional
import kotlinx.coroutines.flow.firstOrNull
import org.amnezia.awg.config.Config
import org.amnezia.awg.config.proxy.HttpProxy
import org.amnezia.awg.config.proxy.Socks5Proxy

class RunConfigHelper(
    private val settingsRepository: GeneralSettingRepository,
    private val proxySettingsRepository: ProxySettingsRepository,
    private val dnsSettingsRepository: DnsSettingsRepository,
    private val tunnelsRepository: TunnelRepository,
) {

    private data class PrepResult(
        val effectiveConfig: TunnelConfig,
        val generalSettings: GeneralSettings,
        val dnsSettings: DnsSettings,
    )

    private suspend fun prepare(tunnelConfig: TunnelConfig): PrepResult {
        val generalSettings = settingsRepository.getGeneralSettings()
        val dnsSettings = dnsSettingsRepository.getDnsSettings()
        val effectiveConfig =
            if (
                generalSettings.isGlobalSplitTunnelEnabled || dnsSettings.isGlobalTunnelDnsEnabled
            ) {
                val globalConfig =
                    tunnelsRepository.globalTunnelFlow.firstOrNull() ?: throw InvalidConfig()
                tunnelConfig.copyWithGlobalValues(
                    globalConfig,
                    dnsSettings.isGlobalTunnelDnsEnabled,
                    generalSettings.isGlobalSplitTunnelEnabled,
                )
            } else {
                tunnelConfig
            }
        return PrepResult(effectiveConfig, generalSettings, dnsSettings)
    }

    suspend fun buildAmRunConfig(tunnelConfig: TunnelConfig): Config {
        val prep = prepare(tunnelConfig)
        val proxies =
            if (prep.generalSettings.appMode == AppMode.PROXY) {
                val proxySettings = proxySettingsRepository.getProxySettings()
                buildList {
                    if (proxySettings.socks5ProxyEnabled) {
                        add(
                            Socks5Proxy(
                                proxySettings.socks5ProxyBindAddress
                                    ?: ProxySettings.DEFAULT_SOCKS_BIND_ADDRESS,
                                proxySettings.proxyUsername,
                                proxySettings.proxyPassword,
                            )
                        )
                    }
                    if (proxySettings.httpProxyEnabled) {
                        add(
                            HttpProxy(
                                proxySettings.httpProxyBindAddress
                                    ?: ProxySettings.DEFAULT_HTTP_BIND_ADDRESS,
                                proxySettings.proxyUsername,
                                proxySettings.proxyPassword,
                            )
                        )
                    }
                }
            } else {
                emptyList()
            }
        val amConfig = prep.effectiveConfig.toAmConfig()
        return Config.Builder()
            .setInterface(amConfig.`interface`)
            .addPeers(amConfig.peers)
            .addProxies(proxies)
            .setDnsSettings(
                org.amnezia.awg.config.DnsSettings(
                    prep.dnsSettings.dnsProtocol == DnsProtocol.DOH,
                    Optional.ofNullable(prep.dnsSettings.dnsEndpoint),
                )
            )
            .build()
    }

    suspend fun buildWgRunConfig(tunnelConfig: TunnelConfig): com.wireguard.config.Config {
        val prep = prepare(tunnelConfig)
        return prep.effectiveConfig.toWgConfig()
    }
}
