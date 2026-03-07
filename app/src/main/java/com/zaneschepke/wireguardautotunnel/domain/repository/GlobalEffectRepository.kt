package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class GlobalEffectRepository {

    private val _globalEffectFlow =
        MutableSharedFlow<GlobalSideEffect>(replay = 0, extraBufferCapacity = 1)
    val flow = _globalEffectFlow.asSharedFlow()

    suspend fun post(effect: GlobalSideEffect) {
        _globalEffectFlow.emit(effect)
    }
}
