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
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.data.model.WifiDetectionMethod
import com.zaneschepke.wireguardautotunnel.ui.theme.AlertRed
import com.zaneschepke.wireguardautotunnel.ui.theme.CoolGray
import com.zaneschepke.wireguardautotunnel.ui.theme.SilverTree
import com.zaneschepke.wireguardautotunnel.ui.theme.Straw
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

fun Tunnel.State.asColor(): Color {
    return when (this) {
        Tunnel.State.Down -> CoolGray
        Tunnel.State.Starting, Tunnel.State.Stopping, Tunnel.State.Up.ResolvingDns -> Straw
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