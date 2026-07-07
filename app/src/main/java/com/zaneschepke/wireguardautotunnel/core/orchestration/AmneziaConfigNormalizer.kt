package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.zaneschepke.wireguardautotunnel.parser.Config

object AmneziaConfigNormalizer {

    private val compatibilityDefaults =
        mapOf(
            "Jc" to 4,
            "Jmin" to 40,
            "Jmax" to 70,
            "S1" to 0,
            "S2" to 0,
            "S3" to 0,
            "S4" to 0,
            "H1" to "1",
            "H2" to "2",
            "H3" to "3",
            "H4" to "4",
        )

    /**
     * If the config uses any Amnezia mimic fields (I1–I5), ensure the required base Amnezia 2.0
     * required parameters are present. This helps make Amnezia 1.5 configs 2.0 compatible.
     */
    fun ensureAmneziaCompatibility(config: Config): Config {
        val iface = config.`interface`

        val hasAnyMimic =
            listOf(iface.i1, iface.i2, iface.i3, iface.i4, iface.i5).any { !it.isNullOrBlank() }

        if (!hasAnyMimic) return config

        val normalizedInterface =
            iface.copy(
                jC = iface.jC ?: compatibilityDefaults["Jc"] as Int,
                jMin = iface.jMin ?: compatibilityDefaults["Jmin"] as Int,
                jMax = iface.jMax ?: compatibilityDefaults["Jmax"] as Int,
                s1 = iface.s1 ?: compatibilityDefaults["S1"] as Int,
                s2 = iface.s2 ?: compatibilityDefaults["S2"] as Int,
                s3 = iface.s3 ?: compatibilityDefaults["S3"] as Int,
                s4 = iface.s4 ?: compatibilityDefaults["S4"] as Int,
                h1 = iface.h1 ?: compatibilityDefaults["H1"] as String,
                h2 = iface.h2 ?: compatibilityDefaults["H2"] as String,
                h3 = iface.h3 ?: compatibilityDefaults["H3"] as String,
                h4 = iface.h4 ?: compatibilityDefaults["H4"] as String,
            )

        return config.copy(`interface` = normalizedInterface)
    }
}
