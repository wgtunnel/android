package com.zaneschepke.wireguardautotunnel.domain.model

data class AppState(
    val isLocationDisclosureShown: Boolean = false,
    val isBatteryOptimizationDisableShown: Boolean = false,
    val isNotificationPermissionRequested: Boolean = false,
    val shouldShowDonationSnackbar: Boolean = false,
)
