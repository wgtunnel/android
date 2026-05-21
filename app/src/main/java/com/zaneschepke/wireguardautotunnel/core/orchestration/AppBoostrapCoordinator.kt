package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.zaneschepke.logcatter.LogReader
import com.zaneschepke.tunnel.backend.Backend
import com.zaneschepke.tunnel.model.DnsBoostrapConfig
import com.zaneschepke.tunnel.model.DnsBoostrapMode
import com.zaneschepke.wireguardautotunnel.domain.enums.DnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.repository.DnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class AppBoostrapCoordinator(
    private val monitoringRepository: MonitoringSettingsRepository,
    private val dnsRepository: DnsSettingsRepository,
    private val tunnelRepository: TunnelRepository,
    private val backend: Backend,
    private val logReader: LogReader,
) {

    suspend fun bootstrap() = coroutineScope {
        launch { bootstrapDns() }
        launch { bootstrapLogging() }
        launch { ensureGlobalConfig() }
    }

    private suspend fun bootstrapDns() {
        val dnsSettings = dnsRepository.getDnsSettings()

        val mode =
            when (dnsSettings.dnsProtocol) {
                DnsProtocol.SYSTEM -> DnsBoostrapMode.System
                DnsProtocol.DOH ->
                    DnsBoostrapMode.Custom(DnsBoostrapConfig.DoH(dnsSettings.dnsEndpoint))
                DnsProtocol.DOT ->
                    DnsBoostrapMode.Custom(DnsBoostrapConfig.DoT(dnsSettings.dnsEndpoint))
                DnsProtocol.UDP ->
                    DnsBoostrapMode.Custom(DnsBoostrapConfig.Plain(dnsSettings.dnsEndpoint))
            }

        backend.setBootstrapDnsMode(mode)
    }

    private suspend fun bootstrapLogging() {
        monitoringRepository.flow
            .distinctUntilChangedBy { it.isLocalLogsEnabled }
            .collect { settings ->
                if (settings.isLocalLogsEnabled) {
                    logReader.start()
                } else {
                    logReader.stop()
                }
            }
    }

    private suspend fun ensureGlobalConfig() {
        tunnelRepository.ensureGlobalConfigExists()
    }
}
