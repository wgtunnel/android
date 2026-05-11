package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.event.ActorEvent
import com.zaneschepke.tunnel.event.ActorEvent.*
import com.zaneschepke.tunnel.event.ActorEvent.TunnelStarted
import com.zaneschepke.tunnel.event.ActorEvent.TunnelStopped
import com.zaneschepke.tunnel.model.RunningTunnel
import com.zaneschepke.tunnel.model.TunnelCommand
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.tunnel.state.ActorState
import com.zaneschepke.tunnel.state.TunnelRuntimeState
import com.zaneschepke.tunnel.util.buildResolvedPeers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class TunnelActor(scope: CoroutineScope, val engine: TunnelEngine) {

    private val inbox = Channel<TunnelCommand>(Channel.UNLIMITED)

    private val _state =
        MutableStateFlow(ActorState(byTunnelId = emptyMap(), byHandle = emptyMap()))

    val state: StateFlow<ActorState> = _state.asStateFlow()

    init {
        scope.launch {
            engine.status.collect { status ->
                when (status.statusCode) {
                    99 -> {
                        val tunnelId = _state.value.byHandle[status.handle] ?: return@collect
                        apply(TunnelStopped(tunnelId, status.handle))
                    }
                    else -> {
                        apply(ActorEvent.EngineStatus(status))
                    }
                }
            }
        }

        scope.launch {
            for (cmd in inbox) {
                when (cmd) {
                    is TunnelCommand.Start -> {
                        val result = engine.start(cmd.tunnel, cmd.mode)
                        apply(TunnelStarted(result, cmd))
                    }

                    is TunnelCommand.Stop -> {
                        val runtime = _state.value.byTunnelId[cmd.tunnelId] ?: continue
                        val handle = runtime.running.handle

                        engine.stop(handle, runtime.running.mode)

                        apply(TunnelStopped(cmd.tunnelId, handle))
                    }

                    is TunnelCommand.UpdatePeers -> {
                        val runtime = _state.value.byTunnelId[cmd.tunnelId] ?: continue
                        val running = runtime.running

                        val peers = running.buildResolvedPeers(preferIpv6 = cmd.preferIpv6)

                        engine.updatePeers(
                            handle = running.handle,
                            mode = running.mode,
                            peers = peers,
                        )

                        apply(
                            PeersUpdated(
                                tunnelId = cmd.tunnelId,
                                peers = peers,
                                preferIpv6 = cmd.preferIpv6,
                            )
                        )
                    }

                    is TunnelCommand.AttachJob -> {
                        apply(JobAttached(tunnelId = cmd.tunnelId, job = cmd.job))
                    }

                    is TunnelCommand.ApplyResolvedPeers -> {
                        val runtime = _state.value.byTunnelId[cmd.tunnelId] ?: continue
                        val running = runtime.running

                        engine.updatePeers(
                            handle = running.handle,
                            mode = running.mode,
                            peers = cmd.peers,
                        )

                        apply(
                            ResolvedPeersApplied(
                                tunnelId = cmd.tunnelId,
                                cache = cmd.cache,
                                peers = cmd.peers,
                            )
                        )
                    }

                    is TunnelCommand.UpdateActiveConfig -> {
                        val runtime = _state.value.byTunnelId[cmd.tunnelId] ?: continue
                        val running = runtime.running

                        val activeConfig = engine.getActiveConfig(running.handle, running.mode)

                        apply(
                            ActiveConfigUpdated(
                                tunnelId = cmd.tunnelId,
                                activeConfig = activeConfig,
                            )
                        )
                    }
                    is TunnelCommand.BeginDnsResolution -> apply(DnsResolutionStarted(cmd.tunnelId))
                    is TunnelCommand.EndDnsResolution -> apply(DnsResolutionFinished(cmd.tunnelId))
                }
            }
        }
    }

    fun send(cmd: TunnelCommand) {
        inbox.trySend(cmd)
    }

    private fun apply(event: ActorEvent) {
        _state.value = reduce(_state.value, event)
    }

    private fun reduce(state: ActorState, event: ActorEvent): ActorState {
        return when (event) {
            is ActorEvent.EngineStatus -> {
                val tunnelId = state.byHandle[event.status.handle] ?: return state
                val runtime = state.byTunnelId[tunnelId] ?: return state

                val newState = event.status.asTunnelState()
                val now = System.currentTimeMillis()

                val effectiveState =
                    if (
                        runtime.active.resolvingDns && newState is Tunnel.State.Up.HandshakeFailure
                    ) {
                        Tunnel.State.Up.ResolvingDns
                    } else {
                        newState
                    }

                val updatedActive =
                    runtime.active.copy(
                        state = effectiveState,
                        lastStateChangeMs = now,
                        lastHealthChangeMs =
                            if (newState is Tunnel.State.Up.Healthy) {
                                now
                            } else {
                                runtime.active.lastHealthChangeMs
                            },
                    )

                val updated = runtime.copy(active = updatedActive)

                state.copy(byTunnelId = state.byTunnelId + (tunnelId to updated))
            }

            is TunnelStarted -> {
                val result = event.result
                val cmd = event.cmd

                val running =
                    RunningTunnel(
                        handle = result.handle,
                        interfaceName = result.interfaceName,
                        mode = result.mode,
                        tunnel = cmd.tunnel,
                        currentPreferIpv6 = cmd.tunnel.ipStrategy is Tunnel.IpStrategy.PreferIpv6,
                    )

                val runtime =
                    TunnelRuntimeState(
                        running = running,
                        active =
                            ActiveTunnel(
                                state = Tunnel.State.Starting,
                                interfaceName = result.interfaceName,
                                mode = result.mode,
                                uptime = System.currentTimeMillis(),
                                activeConfig = null,
                            ),
                    )

                state.copy(
                    byTunnelId = state.byTunnelId + (result.tunnelId to runtime),
                    byHandle = state.byHandle + (result.handle to result.tunnelId),
                )
            }

            is TunnelStopped -> {
                val runtime = state.byTunnelId[event.tunnelId] ?: return state

                runtime.running.job?.cancel()

                state.copy(
                    byTunnelId = state.byTunnelId - event.tunnelId,
                    byHandle = state.byHandle - event.handle,
                )
            }
            is ActorEvent.PeersUpdated -> {
                val runtime = state.byTunnelId[event.tunnelId] ?: return state

                val updatedRunning =
                    runtime.running.copy(
                        currentPreferIpv6 = event.preferIpv6,
                        resolvedPeers = event.peers,
                    )

                state.copy(
                    byTunnelId =
                        state.byTunnelId +
                            (event.tunnelId to runtime.copy(running = updatedRunning))
                )
            }
            is ActorEvent.ResolvedPeersApplied -> {
                val runtime = state.byTunnelId[event.tunnelId] ?: return state
                val running = runtime.running

                val updatedRunning =
                    running.copy(resolvedPeers = event.peers, peerBootstrapCache = event.cache)

                state.copy(
                    byTunnelId =
                        state.byTunnelId +
                            (event.tunnelId to runtime.copy(running = updatedRunning))
                )
            }
            is ActorEvent.JobAttached -> {
                val runtime = state.byTunnelId[event.tunnelId] ?: return state

                val updatedRunning = runtime.running.copy(job = event.job)

                state.copy(
                    byTunnelId =
                        state.byTunnelId +
                            (event.tunnelId to runtime.copy(running = updatedRunning))
                )
            }
            is ActorEvent.ActiveConfigUpdated -> {
                val runtime = state.byTunnelId[event.tunnelId] ?: return state

                val updated =
                    runtime.copy(active = runtime.active.copy(activeConfig = event.activeConfig))

                state.copy(byTunnelId = state.byTunnelId + (event.tunnelId to updated))
            }
            is ActorEvent.DnsResolutionStarted -> {
                val runtime = state.byTunnelId[event.tunnelId] ?: return state

                val updated =
                    runtime.copy(
                        active =
                            runtime.active.copy(
                                resolvingDns = true,
                                state = Tunnel.State.Up.ResolvingDns,
                            )
                    )

                state.copy(byTunnelId = state.byTunnelId + (event.tunnelId to updated))
            }
            is ActorEvent.DnsResolutionFinished -> {
                val runtime = state.byTunnelId[event.tunnelId] ?: return state

                val updated = runtime.copy(active = runtime.active.copy(resolvingDns = false))

                state.copy(byTunnelId = state.byTunnelId + (event.tunnelId to updated))
            }
        }
    }
}
