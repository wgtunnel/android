package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.state.EngineState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class DefaultEngineStateProvider : EngineStateProvider {

    private val _state = MutableStateFlow(EngineState())

    override val state: Flow<EngineState> = _state

    fun update(transform: (EngineState) -> EngineState) {
        _state.value = transform(_state.value)
    }
}
