package com.zaneschepke.wireguardautotunnel.service.autotunnel

import com.wgtunnel.backend.Tunnel
import com.wgtunnel.backend.state.ActiveTunnel
import com.wgtunnel.backend.state.BackendStatus
import com.wgtunnel.backend.state.KillSwitchState
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelActionSource
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.events.AutoTunnelEvent
import com.zaneschepke.wireguardautotunnel.domain.model.AutoTunnelSettings
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.state.AutoTunnelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTunnelUnreachablePolicyTest {

    @Test
    fun `suspended replacement keeps the working tunnel running`() {
        val replacement = tunnel(id = 1)
        val event = AutoTunnelEvent.Sync(start = setOf(replacement), stop = setOf(2))

        assertEquals(AutoTunnelEvent.DoNothing, event.withoutSuspendedStarts(setOf(1)))
        assertEquals(event, event.withoutSuspendedStarts(emptySet()))
    }

    @Test
    fun `snapshot only arms auto tunnel origins`() {
        val state = state(tunnels = listOf(tunnel(1), tunnel(2)))
        val status =
            BackendStatus(
                activeTunnels =
                    mapOf(
                        1 to failingTunnel(),
                        2 to failingTunnel(),
                    )
            )

        val snapshot =
            buildUnreachableTunnelSnapshot(
                state = state,
                status = status,
                origins =
                    mapOf(
                        1 to TunnelActionSource.AUTO_TUNNEL,
                        2 to TunnelActionSource.USER,
                    ),
                general = GeneralSettings(),
                androidVpnLockdown = false,
            )

        assertTrue(snapshot.enabled)
        assertEquals(setOf(1), snapshot.armFailureGracePeriodsMs.keys)
        assertEquals(setOf(1), snapshot.keepFailureIds)
    }

    @Test
    fun `snapshot disables stops for every lockdown source`() {
        val tunnel = tunnel(1)
        val origins = mapOf(1 to TunnelActionSource.AUTO_TUNNEL)
        val status = BackendStatus(activeTunnels = mapOf(1 to failingTunnel()))
        val general = GeneralSettings()

        assertFalse(
            buildUnreachableTunnelSnapshot(
                    state = state(tunnels = listOf(tunnel), mode = TunnelMode.LOCK_DOWN),
                    status = status,
                    origins = origins,
                    general = general,
                    androidVpnLockdown = false,
                )
                .enabled
        )
        assertFalse(
            buildUnreachableTunnelSnapshot(
                    state = state(tunnels = listOf(tunnel)),
                    status = status.copy(killSwitch = KillSwitchState(enabled = true)),
                    origins = origins,
                    general = general,
                    androidVpnLockdown = false,
                )
                .enabled
        )
        assertFalse(
            buildUnreachableTunnelSnapshot(
                    state = state(tunnels = listOf(tunnel)),
                    status = status,
                    origins = origins,
                    general = general,
                    androidVpnLockdown = true,
                )
                .enabled
        )
    }

    private fun state(
        tunnels: List<TunnelConfig>,
        mode: TunnelMode = TunnelMode.VPN,
    ) =
        AutoTunnelState(
            settings =
                AutoTunnelSettings(
                    isAutoTunnelEnabled = true,
                    isStopOnUnreachableEnabled = true,
                ),
            tunnelMode = mode,
            tunnels = tunnels,
            confirmedHasUsableNetwork = true,
        )

    private fun tunnel(id: Int) = TunnelConfig(id = id, name = "tunnel-$id")

    private fun failingTunnel() = ActiveTunnel(transportState = Tunnel.State.Up.HandshakeFailure)
}
