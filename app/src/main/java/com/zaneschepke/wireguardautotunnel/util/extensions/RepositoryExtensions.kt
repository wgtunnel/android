package com.zaneschepke.wireguardautotunnel.util.extensions

import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository

suspend fun TunnelRepository.saveTunnelsUniquely(
    tunnels: List<TunnelConfig>,
    existingNames: List<String>,
) {
    val uniqueTunnels = generateUniquelyNamedConfigs(tunnels, existingNames)
    saveAll(uniqueTunnels)
}

private fun generateUniquelyNamedConfigs(
    incoming: List<TunnelConfig>,
    existingNames: List<String>,
): List<TunnelConfig> {
    val usedNames = existingNames.toMutableSet()
    val result = mutableListOf<TunnelConfig>()
    val regex = Regex("(.+)\\s*\\((\\d+)\\)$")

    for (tun in incoming) {
        var baseName = tun.name
        var uniqueName = tun.name
        var counter = 1

        val matchResult = regex.find(baseName)
        if (matchResult != null) {
            baseName = matchResult.groupValues[1].trimEnd()
            counter = matchResult.groupValues[2].toIntOrNull()?.plus(1) ?: 1
            uniqueName = "$baseName ($counter)"
        }

        while (uniqueName in usedNames) {
            uniqueName = "$baseName ($counter)"
            counter++
        }

        usedNames.add(uniqueName)
        result.add(tun.copy(name = uniqueName))
    }
    return result
}
