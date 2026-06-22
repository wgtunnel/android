package com.zaneschepke.wireguardautotunnel.util.extensions

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.enums.WifiDetectionMethod
import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState
import com.zaneschepke.wireguardautotunnel.util.DnsError
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

fun TunnelMode.asTitleString(context: Context): String {
    return when (this) {
        TunnelMode.VPN -> asString(context)
        TunnelMode.PROXY -> asString(context)
        TunnelMode.LOCK_DOWN -> asString(context)
    }
}

fun TunnelMode.asString(context: Context): String {
    return when (this) {
        TunnelMode.VPN -> context.getString(R.string.vpn)
        TunnelMode.PROXY -> context.getString(R.string.local_proxy)
        TunnelMode.LOCK_DOWN -> context.getString(R.string.lockdown)
    }
}

@Composable
fun TunnelMode.asIcon(): ImageVector {
    return when (this) {
        TunnelMode.VPN -> Icons.Outlined.VpnKey
        TunnelMode.PROXY -> ImageVector.vectorResource(R.drawable.proxy)
        TunnelMode.LOCK_DOWN -> Icons.Outlined.Lock
    }
}

fun Duration.localized(locale: Locale = Locale.getDefault()): String {
    require(this >= Duration.ZERO) { "Duration cannot be negative" }

    if (this < 1.seconds) {
        return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
            .format(Measure(0, MeasureUnit.SECOND))
    }

    val measures = buildList {
        if (inWholeDays > 0) add(Measure(inWholeDays, MeasureUnit.DAY))
        if (inWholeHours % 24 > 0) add(Measure(inWholeHours % 24, MeasureUnit.HOUR))
        if (inWholeMinutes % 60 > 0) add(Measure(inWholeMinutes % 60, MeasureUnit.MINUTE))
        if (inWholeSeconds % 60 > 0) add(Measure(inWholeSeconds % 60, MeasureUnit.SECOND))
    }

    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
        .formatMeasures(*measures.toTypedArray())
}

fun Long?.toAgoDisplay(currentTimeMillis: Long = System.currentTimeMillis()): String? {
    val timestamp = this ?: return null
    if (timestamp <= 0L) return null

    val nowSeconds = currentTimeMillis / 1000
    val secondsAgo = (nowSeconds - timestamp).coerceAtLeast(0L)

    return secondsAgo.seconds.localized()
}

fun Long.toUptimeDisplay(currentTimeMillis: Long = System.currentTimeMillis()): String {
    val elapsedMillis = (currentTimeMillis - this).coerceAtLeast(0L)
    return elapsedMillis.milliseconds.localized()
}

@StringRes
fun DnsError.labelRes(): Int {
    return when (this) {
        DnsError.Empty -> R.string.dns_error_empty
        DnsError.InvalidUrl -> R.string.dns_error_invalid_url
        DnsError.InvalidScheme -> R.string.dns_error_invalid_scheme
        DnsError.InvalidHost -> R.string.dns_error_invalid_host
        DnsError.InvalidPort -> R.string.dns_error_invalid_port
        DnsError.InvalidIpOrHost -> R.string.dns_error_invalid_ip_or_host
    }
}

fun ActiveTunnel.statusText(context: Context): String {
    return context.getString(
        R.string.status_template,
        DisplayTunnelState.from(this).asLocalizedString(context),
    )
}

fun ActiveTunnel.uptimeText(context: Context, now: Long): String? {

    val startedAt = uptime ?: return null

    val uptimeDisplay = startedAt.toUptimeDisplay(now)

    return context.getString(R.string.uptime_template, uptimeDisplay)
}
