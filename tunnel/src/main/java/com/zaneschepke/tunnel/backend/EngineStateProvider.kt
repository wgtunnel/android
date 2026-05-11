package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.state.EngineState
import kotlinx.coroutines.flow.Flow

internal interface EngineStateProvider {
    val state: Flow<EngineState>
}
