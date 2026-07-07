package com.zaneschepke.networkmonitor

import kotlinx.coroutines.flow.Flow

interface NetworkMonitor {
    val connectivityStateFlow: Flow<ConnectivityState>

    fun checkPermissionsAndUpdateState()
}
