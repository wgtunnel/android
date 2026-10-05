package com.zaneschepke.wireguardautotunnel.service.autotunnel

import com.wgtunnel.backend.Tunnel
import com.wgtunnel.backend.autotunnel.TunnelActions
import com.wgtunnel.backend.state.ActiveTunnel
import com.wgtunnel.backend.state.BackendStatus
import com.wgtunnel.backend.state.KillSwitchState
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelActionSource
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.model.AutoTunnelSettings
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.state.AutoTunnelState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTunnelUnreachablePolicyTest {

    @Test
    fun `suspended replacement keeps the working tunnel running`() = runBlocking {
        val recording = RecordingActions()
        val actions = CoolingDownTunnelActions(recording) { setOf(1L) }

        actions.stop(2L)
        actions.start(1L)
        actions.finish()

        assertTrue(recording.events.isEmpty())
    }

    @Test
    fun `unsuspended replacement stops the working tunnel then starts`() = runBlocking {
        val recording = RecordingActions()
        val actions = CoolingDownTunnelActions(recording) { emptySet() }

        actions.stop(2L)
        actions.start(1L)
        actions.finish()

        assertEquals(listOf("stop:2", "start:1"), recording.events)
    }

    @Test
    fun `stop only pass still stops`() = runBlocking {
        val recording = RecordingActions()
        val actions = CoolingDownTunnelActions(recording) { setOf(1L) }

        actions.stop(2L)
        actions.finish()

        assertEquals(listOf("stop:2"), recording.events)
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

    private class RecordingActions : TunnelActions {
        val events = mutableListOf<String>()

        override suspend fun start(id: Long) {
            events += "start:$id"
        }

        override suspend fun stop(id: Long) {
            events += "stop:$id"
        }
    }
}
