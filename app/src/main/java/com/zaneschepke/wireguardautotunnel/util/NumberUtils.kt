package com.zaneschepke.wireguardautotunnel.util

import com.vdurmont.semver4j.Semver
import timber.log.Timber

object NumberUtils {

    fun generateRandomTunnelName(): String {
        return "tunnel${randomFive()}"
    }

    private fun randomFive(): Int {
        return (Math.random() * 100000).toInt()
    }

    fun compareVersions(newVersion: String, currentVersion: String): Int {
        try {
            val newSemver = Semver(newVersion, Semver.SemverType.LOOSE)
            val currentSemver = Semver(currentVersion, Semver.SemverType.LOOSE)
            return newSemver.compareTo(currentSemver)
        } catch (e: Exception) {
            Timber.e(e, "Failed to compare versions $newVersion and $currentVersion")
            return 0
        }
    }
}
