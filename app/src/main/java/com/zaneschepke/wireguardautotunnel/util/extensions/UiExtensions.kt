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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.data.model.TunnelMode
import com.zaneschepke.wireguardautotunnel.data.model.WifiDetectionMethod
import com.zaneschepke.wireguardautotunnel.ui.theme.AlertRed
import com.zaneschepke.wireguardautotunnel.ui.theme.CoolGray
import com.zaneschepke.wireguardautotunnel.ui.theme.SilverTree
import com.zaneschepke.wireguardautotunnel.ui.theme.Straw
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
        TunnelMode.PROXY -> context.getString(R.string.expiremental_template, asString(context))
        TunnelMode.LOCK_DOWN -> context.getString(R.string.expiremental_template, asString(context))
    }
}

fun TunnelMode.asString(context: Context): String {
    return when (this) {
        TunnelMode.VPN -> context.getString(R.string.vpn)
        TunnelMode.PROXY -> context.getString(R.string.proxy)
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

fun Tunnel.State.asColor(): Color {
    return when (this) {
        Tunnel.State.Down -> CoolGray
        Tunnel.State.Starting,
        Tunnel.State.Stopping,
        Tunnel.State.Up.ResolvingDns -> Straw
        Tunnel.State.Up.HandshakeFailure -> AlertRed
        Tunnel.State.Up.Healthy -> SilverTree
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

fun DnsError.toLocalizedString(context: Context): String {
    return when (this) {
        DnsError.Empty -> context.getString(R.string.dns_error_empty)
        DnsError.InvalidUrl -> context.getString(R.string.dns_error_invalid_url)
        DnsError.InvalidScheme -> context.getString(R.string.dns_error_invalid_scheme)
        DnsError.InvalidHost -> context.getString(R.string.dns_error_invalid_host)
        DnsError.InvalidPort -> context.getString(R.string.dns_error_invalid_port)
        DnsError.InvalidIpOrHost -> context.getString(R.string.dns_error_invalid_ip_or_host)
    }
}

@StringRes
fun Tunnel.State.labelRes(): Int {
    return when (this) {
        is Tunnel.State.Up.Healthy -> R.string.tunnel_state_connected

        is Tunnel.State.Up.ResolvingDns -> R.string.tunnel_state_resolving_dns

        is Tunnel.State.Up.HandshakeFailure -> R.string.tunnel_state_handshake_failure

        Tunnel.State.Down -> R.string.tunnel_state_disconnected

        Tunnel.State.Starting -> R.string.tunnel_state_starting

        Tunnel.State.Stopping -> R.string.tunnel_state_stopping
    }
}

fun Tunnel.State.localizedLabel(context: Context): String {
    return context.getString(labelRes())
}
