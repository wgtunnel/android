package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.model.TunnelCommand
import com.zaneschepke.tunnel.state.TunnelRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class TunnelActor(
    private val scope: CoroutineScope,
    private val backend: TunnelBackend
) {
    private val inbox = Channel<TunnelCommand>(Channel.UNLIMITED)

    private val state = mutableMapOf<Int, TunnelRuntimeState>()

    init {
        scope.launch {
            for (cmd in inbox) {
                when (cmd) {
                    is TunnelCommand.Start -> handleStart(cmd)
                    is TunnelCommand.Stop -> handleStop(cmd.tunnelId)
                    is TunnelCommand.UpdatePeers -> handleUpdatePeers(cmd)
                    is TunnelCommand.StatusEvent -> handleStatus(cmd)
                }
            }
        }
    }

    fun send(cmd: TunnelCommand) {
        inbox.trySend(cmd)
    }

    private fun update(tunnelId: Int, block: (TunnelRuntimeState?) -> TunnelRuntimeState?) {
        val current = state[tunnelId]
        val updated = block(current) ?: return
        state[tunnelId] = updated
    }

}