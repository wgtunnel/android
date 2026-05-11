package com.zaneschepke.networkmonitor

import com.zaneschepke.networkmonitor.model.StableNetworkSnapshot
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class StableNetworkEngine(
    private val scope: CoroutineScope,
    private val upstream: Flow<ConnectivityState>,
) {

    private val _stableState = MutableStateFlow<StableNetworkSnapshot?>(null)
    val stableState: StateFlow<StableNetworkSnapshot?> = _stableState.asStateFlow()

    private var lastKey: String? = null
    private var stableSinceMs: Long = 0L

    init {
        scope.launch {
            upstream.debounce(150.milliseconds).collect { state ->
                val key = state.activeNetwork.key()
                val now = System.currentTimeMillis()

                if (key != lastKey) {
                    lastKey = key
                    stableSinceMs = now
                }

                val snapshot =
                    StableNetworkSnapshot(key = key, state = state, stableSinceMs = stableSinceMs)

                _stableState.value = snapshot
            }
        }
    }
}
