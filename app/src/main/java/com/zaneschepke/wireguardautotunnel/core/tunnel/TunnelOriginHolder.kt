package com.zaneschepke.wireguardautotunnel.core.tunnel

import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelActionSource
import com.zaneschepke.wireguardautotunnel.domain.events.TunnelActionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Tracks what started each active tunnel, since the backend has no notion of an action source.
class TunnelOriginHolder {

    private val _origins = MutableStateFlow<Map<Int, TunnelActionSource>>(emptyMap())
    val origins: StateFlow<Map<Int, TunnelActionSource>> = _origins

    fun bind(scope: CoroutineScope, actions: Flow<TunnelActionEvent>) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            actions.collect { event ->
                when (event) {
                    is TunnelActionEvent.Started ->
                        _origins.update { it + (event.tunnelId to event.source) }
                    is TunnelActionEvent.Stopped -> _origins.update { it - event.tunnelId }
                }
            }
        }
    }
}
