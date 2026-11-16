package com.zaneschepke.wireguardautotunnel.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.telephony.TelephonyManager

enum class RussiaHint {
    NONE,
    LOCALE,
    TELEPHONY,
}

fun Context.russiaHint(): RussiaHint {
    if (hasRussianTelephony()) return RussiaHint.TELEPHONY
    val locales = Resources.getSystem().configuration.locales
    val localeCountry = if (locales.isEmpty) "" else locales.get(0).country
    return if (localeCountry.equals("RU", ignoreCase = true)) RussiaHint.LOCALE else RussiaHint.NONE
}

private fun Context.hasRussianTelephony(): Boolean {
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return false
    val telephony = getSystemService(TelephonyManager::class.java) ?: return false
    val simCountry = runCatching { telephony.simCountryIso }.getOrNull()
    val networkCountry = runCatching { telephony.networkCountryIso }.getOrNull()
    return simCountry.equals("ru", ignoreCase = true) ||
        networkCountry.equals("ru", ignoreCase = true)
}
