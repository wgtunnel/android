package com.zaneschepke.networkmonitor

import kotlinx.coroutines.flow.StateFlow

interface NetworkMonitor {
    val connectivityStateFlow: StateFlow<ConnectivityState?>

    fun checkPermissionsAndUpdateState()
}
