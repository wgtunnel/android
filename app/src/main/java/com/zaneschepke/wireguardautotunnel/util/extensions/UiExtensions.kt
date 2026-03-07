package com.zaneschepke.wireguardautotunnel.util.extensions

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.data.model.WifiDetectionMethod
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelState
import com.zaneschepke.wireguardautotunnel.ui.theme.AlertRed
import com.zaneschepke.wireguardautotunnel.ui.theme.CoolGray
import com.zaneschepke.wireguardautotunnel.ui.theme.SilverTree
import com.zaneschepke.wireguardautotunnel.ui.theme.Straw
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

fun WifiDetectionMethod.asTitleString(context: Context): String {
    return when (this) {
        WifiDetectionMethod.DEFAULT -> context.getString(R.string._default)
        WifiDetectionMethod.LEGACY -> context.getString(R.string.legacy)
        WifiDetectionMethod.ROOT -> context.getString(R.string.root)
        WifiDetectionMethod.SHIZUKU -> context.getString(R.string.shizuku)
    }
}

fun WifiDetectionMethod.to(): AndroidNetworkMonitor.WifiDetectionMethod {
    return when (this) {
        WifiDetectionMethod.DEFAULT -> AndroidNetworkMonitor.WifiDetectionMethod.DEFAULT
        WifiDetectionMethod.LEGACY -> AndroidNetworkMonitor.WifiDetectionMethod.LEGACY
        WifiDetectionMethod.ROOT -> AndroidNetworkMonitor.WifiDetectionMethod.ROOT
        WifiDetectionMethod.SHIZUKU -> AndroidNetworkMonitor.WifiDetectionMethod.SHIZUKU
    }
}

fun WifiDetectionMethod.asDescriptionString(context: Context): String? {
    return when (this) {
        WifiDetectionMethod.LEGACY -> context.getString(R.string.legacy_api_description)
        WifiDetectionMethod.ROOT -> context.getString(R.string.use_root_shell_for_wifi)
        WifiDetectionMethod.SHIZUKU -> context.getString(R.string.use_shell_via_shizuku)
        WifiDetectionMethod.DEFAULT -> context.getString(R.string.use_android_recommended)
    }
}

fun AppMode.asTitleString(context: Context): String {
    return when (this) {
        AppMode.VPN -> asString(context)
        AppMode.PROXY -> context.getString(R.string.expiremental_template, asString(context))
        AppMode.KERNEL -> context.getString(R.string.root_required_template, asString(context))
        AppMode.LOCK_DOWN -> context.getString(R.string.expiremental_template, asString(context))
    }
}

fun AppMode.asString(context: Context): String {
    return when (this) {
        AppMode.VPN -> context.getString(R.string.vpn)
        AppMode.PROXY -> context.getString(R.string.proxy)
        AppMode.KERNEL -> context.getString(R.string.kernel)
        AppMode.LOCK_DOWN -> context.getString(R.string.lockdown)
    }
}

fun AppMode.description(context: Context): String? {
    return if (this == AppMode.KERNEL)
        context.getString(R.string.only_template, context.getString(R.string.wireguard))
    else null
}

@Composable
fun AppMode.asIcon(): ImageVector {
    return when (this) {
        AppMode.VPN -> Icons.Outlined.VpnKey
        AppMode.PROXY -> ImageVector.vectorResource(R.drawable.proxy)
        AppMode.KERNEL -> Icons.Outlined.Terminal
        AppMode.LOCK_DOWN -> Icons.Outlined.Lock
    }
}

fun TunnelState.Health.asColor(): Color {
    return when (this) {
        TunnelState.Health.UNKNOWN -> CoolGray
        TunnelState.Health.UNHEALTHY -> AlertRed
        TunnelState.Health.HEALTHY -> SilverTree
        TunnelState.Health.STALE -> Straw
    }
}

fun Long.localizedDuration(locale: Locale = Locale.getDefault()): String {
    require(this >= 0L) { "Duration cannot be negative" }

    val duration = this.milliseconds

    if (duration < 1000.milliseconds) {
        return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
            .format(Measure(0, MeasureUnit.SECOND))
    }

    val totalSeconds = duration.inWholeSeconds

    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60

    val measures = buildList {
        if (days > 0) add(Measure(days, MeasureUnit.DAY))
        if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
        if (minutes > 0) add(Measure(minutes, MeasureUnit.MINUTE))
        if (seconds > 0) add(Measure(seconds, MeasureUnit.SECOND))
    }

    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
        .formatMeasures(*measures.toTypedArray())
}

fun Long.millisAgo(): Long {
    return System.currentTimeMillis() - this
}
