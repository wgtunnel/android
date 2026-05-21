package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.zaneschepke.wireguardautotunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.parser.InterfaceSection

object ConfigReconciler {
    private fun mergeInterface(
        base: InterfaceSection,
        global: InterfaceSection,
        policy: ConfigReconcilePolicy,
    ): InterfaceSection {
        return base.copy(
            dns = if (policy.dns) global.dns else base.dns,
            includedApplications =
                if (policy.splitTunnel) global.includedApplications else base.includedApplications,
            excludedApplications =
                if (policy.splitTunnel) global.excludedApplications else base.excludedApplications,
            jC = if (policy.amnezia) global.jC else base.jC,
            jMin = if (policy.amnezia) global.jMin else base.jMin,
            jMax = if (policy.amnezia) global.jMax else base.jMax,
            s1 = if (policy.amnezia) global.s1 else base.s1,
            s2 = if (policy.amnezia) global.s2 else base.s2,
            s3 = if (policy.amnezia) global.s3 else base.s3,
            s4 = if (policy.amnezia) global.s4 else base.s4,
            h1 = if (policy.amnezia) global.h1 else base.h1,
            h2 = if (policy.amnezia) global.h2 else base.h2,
            h3 = if (policy.amnezia) global.h3 else base.h3,
            h4 = if (policy.amnezia) global.h4 else base.h4,
            i1 = if (policy.amnezia) global.i1 else base.i1,
            i2 = if (policy.amnezia) global.i2 else base.i2,
            i3 = if (policy.amnezia) global.i3 else base.i3,
            i4 = if (policy.amnezia) global.i4 else base.i4,
            i5 = if (policy.amnezia) global.i5 else base.i5,
        )
    }

    fun reconcileConfig(base: Config, global: Config?, policy: ConfigReconcilePolicy): Config {
        if (global == null) return base
        if (!policy.hasAnyOverrides) return base

        return base.copy(`interface` = mergeInterface(base.`interface`, global.`interface`, policy))
    }

    data class ConfigReconcilePolicy(
        val dns: Boolean,
        val splitTunnel: Boolean,
        val amnezia: Boolean,
    ) {
        val hasAnyOverrides
            get() = dns || splitTunnel || amnezia
    }
}
