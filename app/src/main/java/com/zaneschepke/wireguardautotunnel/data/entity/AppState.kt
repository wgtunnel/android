package com.zaneschepke.wireguardautotunnel.data.entity

data class AppState(
    val isLocationDisclosureShown: Boolean = false,
    val isBatteryOptimizationDisableShown: Boolean = false,
    val isNotificationPermissionRequested: Boolean = false,
    val shouldShowDonationSnackbar: Boolean = false,
)
