package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaneschepke.wireguardautotunnel.util.BackupManager
import com.zaneschepke.wireguardautotunnel.util.BackupOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BackupUiState(
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val backupOptions: BackupOptions = BackupOptions(),
    val error: String? = null,
    val success: Boolean = false
)

class BackupRestoreViewModel(
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState

    fun setBackupOption(option: String, value: Boolean) {
        _uiState.value = _uiState.value.copy(
            backupOptions = when (option) {
                "tunnels" -> _uiState.value.backupOptions.copy(backupTunnels = value)
                "settings" -> _uiState.value.backupOptions.copy(backupSettings = value)
                "proxy" -> _uiState.value.backupOptions.copy(backupProxySettings = value)
                "monitoring" -> _uiState.value.backupOptions.copy(backupMonitoringSettings = value)
                "dns" -> _uiState.value.backupOptions.copy(backupDnsSettings = value)
                "autoTunnel" -> _uiState.value.backupOptions.copy(backupAutoTunnelSettings = value)
                "lockdown" -> _uiState.value.backupOptions.copy(backupLockdownSettings = value)
                else -> _uiState.value.backupOptions
            }
        )
    }

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBackingUp = true, error = null, success = false)
            try {
                backupManager.createBackup(uri, _uiState.value.backupOptions)
                _uiState.value = _uiState.value.copy(isBackingUp = false, success = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isBackingUp = false, error = e.message)
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true, error = null, success = false)
            try {
                backupManager.restoreBackup(uri, _uiState.value.backupOptions)
                _uiState.value = _uiState.value.copy(isRestoring = false, success = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRestoring = false, error = e.message)
            }
        }
    }
}
