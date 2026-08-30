package com.zaneschepke.wireguardautotunnel.data.mapper

import com.zaneschepke.wireguardautotunnel.data.entity.AppState as Entity
import com.zaneschepke.wireguardautotunnel.domain.model.AppState as Domain

fun Entity.toDomain(): Domain =
    Domain(
        isLocationDisclosureShown = isLocationDisclosureShown,
        isBatteryOptimizationDisableShown = isBatteryOptimizationDisableShown,
        isNotificationPermissionRequested = isNotificationPermissionRequested,
        shouldShowDonationSnackbar = shouldShowDonationSnackbar,
    )
