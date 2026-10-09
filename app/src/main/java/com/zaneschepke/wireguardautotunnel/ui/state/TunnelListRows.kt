package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelGroup

sealed class TunnelListRow {
    abstract val key: String
    abstract val scopeKey: String

    data class GroupHeader(
        val group: TunnelGroup,
        val childCount: Int,
        val visibleExpanded: Boolean,
        val activeCount: Int,
    ) : TunnelListRow() {
        override val key: String = "group-${group.id}"
        override val scopeKey: String = ROOT_SCOPE
    }

    data class TunnelRow(val tunnel: TunnelConfig, val grouped: Boolean) : TunnelListRow() {
        override val key: String = "tunnel-${tunnel.id}"
        override val scopeKey: String = tunnel.groupId?.let { "group-$it" } ?: ROOT_SCOPE
    }

    companion object {
        const val ROOT_SCOPE = "root"
    }
}

private sealed interface RootItem {
    val position: Int
    val name: String

    data class Group(val group: TunnelGroup) : RootItem {
        override val position: Int
            get() = group.position

        override val name: String
            get() = group.name
    }

    data class Tunnel(val tunnel: TunnelConfig) : RootItem {
        override val position: Int
            get() = tunnel.position

        override val name: String
            get() = tunnel.name
    }
}

fun nextRootPosition(groups: List<TunnelGroup>, tunnels: List<TunnelConfig>): Int {
    val ungroupedMax = tunnels.filter { it.groupId == null }.maxOfOrNull { it.position } ?: -1
    val groupMax = groups.maxOfOrNull { it.position } ?: -1
    return maxOf(ungroupedMax, groupMax) + 1
}

fun nextChildPosition(tunnels: List<TunnelConfig>, groupId: Int): Int {
    return (tunnels.filter { it.groupId == groupId }.maxOfOrNull { it.position } ?: -1) + 1
}

fun uniqueDisplayName(desired: String, existing: Collection<String>, fallback: String): String {
    val base = desired.trim().ifEmpty { fallback }
    if (base !in existing) return base
    var n = 1
    var candidate = "$base ($n)"
    while (candidate in existing) {
        n++
        candidate = "$base ($n)"
    }
    return candidate
}

fun buildTunnelListRows(
    groups: List<TunnelGroup>,
    tunnels: List<TunnelConfig>,
    activeTunnelIds: Set<Int>,
    collapseGroups: Boolean = false,
    scopeGroupId: Int? = null,
): List<TunnelListRow> {
    val childrenByGroup = tunnels.filter { it.groupId != null }.groupBy { it.groupId }
    if (scopeGroupId != null) {
        val children = (childrenByGroup[scopeGroupId] ?: emptyList()).sortedBy { it.position }
        return children.map { TunnelListRow.TunnelRow(it, grouped = true) }
    }
    return rootItems(groups, tunnels).flatMap { item ->
        when (item) {
            is RootItem.Group -> {
                val children =
                    (childrenByGroup[item.group.id] ?: emptyList()).sortedBy { it.position }
                val activeCount = children.count { it.id in activeTunnelIds }
                // Not forced open by an active child, the header shows the active count instead
                val visibleExpanded = !collapseGroups && item.group.expanded
                buildList<TunnelListRow> {
                    add(
                        TunnelListRow.GroupHeader(
                            group = item.group,
                            childCount = children.size,
                            visibleExpanded = visibleExpanded,
                            activeCount = activeCount,
                        )
                    )
                    if (visibleExpanded) {
                        children.forEach { add(TunnelListRow.TunnelRow(it, grouped = true)) }
                    }
                }
            }
            is RootItem.Tunnel ->
                listOf<TunnelListRow>(TunnelListRow.TunnelRow(item.tunnel, grouped = false))
        }
    }
}

fun moveDisplayedRows(
    groups: List<TunnelGroup>,
    tunnels: List<TunnelConfig>,
    fromIndex: Int,
    toIndex: Int,
    scopeGroupId: Int?,
): Pair<List<TunnelGroup>, List<TunnelConfig>> {
    if (fromIndex == toIndex) return groups to tunnels
    if (scopeGroupId != null) {
        return groups to moveGroupChildren(tunnels, scopeGroupId, fromIndex, toIndex)
    }
    val root = rootItems(groups, tunnels).toMutableList()
    if (fromIndex !in root.indices || toIndex !in root.indices) return groups to tunnels
    val moved = root.removeAt(fromIndex)
    root.add(toIndex, moved)
    return reindexRoot(root, tunnels)
}

fun sortRootByName(
    groups: List<TunnelGroup>,
    tunnels: List<TunnelConfig>,
    ascending: Boolean,
): Pair<List<TunnelGroup>, List<TunnelConfig>> {
    val comparator =
        if (ascending) compareBy<RootItem> { it.name.lowercase() }
        else compareByDescending { it.name.lowercase() }
    return reindexRoot(rootItems(groups, tunnels).sortedWith(comparator), tunnels)
}

fun sortGroupChildrenByName(
    tunnels: List<TunnelConfig>,
    groupId: Int,
    ascending: Boolean,
): List<TunnelConfig> {
    val children = tunnels.filter { it.groupId == groupId }
    val others = tunnels.filter { it.groupId != groupId }
    val sorted =
        if (ascending) children.sortedBy { it.name.lowercase() }
        else children.sortedByDescending { it.name.lowercase() }
    return others + sorted.mapIndexed { index, tunnel -> tunnel.copy(position = index) }
}

fun ungroupKeepingOrder(
    groups: List<TunnelGroup>,
    tunnels: List<TunnelConfig>,
    groupId: Int,
): Pair<List<TunnelGroup>, List<TunnelConfig>> {
    val children = tunnels.filter { it.groupId == groupId }.sortedBy { it.position }
    val original = rootItems(groups, tunnels).toMutableList()
    val insertAt = original.indexOfFirst { it is RootItem.Group && it.group.id == groupId }
    if (insertAt >= 0) original.removeAt(insertAt)
    val index = if (insertAt >= 0) insertAt else original.size
    children.forEachIndexed { offset, child ->
        original.add(index + offset, RootItem.Tunnel(child.copy(groupId = null)))
    }
    val remainingGroups = groups.filter { it.id != groupId }
    return reindexRoot(original, tunnels.filter { it.groupId != groupId }, remainingGroups)
}

private fun rootItems(groups: List<TunnelGroup>, tunnels: List<TunnelConfig>): List<RootItem> {
    val ungrouped = tunnels.filter { it.groupId == null }
    return (groups.map { RootItem.Group(it) } + ungrouped.map { RootItem.Tunnel(it) }).sortedWith(
        compareBy({ it.position }, { it.name.lowercase() })
    )
}

private fun moveGroupChildren(
    tunnels: List<TunnelConfig>,
    groupId: Int,
    fromIndex: Int,
    toIndex: Int,
): List<TunnelConfig> {
    val children = tunnels.filter { it.groupId == groupId }.sortedBy { it.position }.toMutableList()
    val others = tunnels.filter { it.groupId != groupId }
    if (fromIndex !in children.indices || toIndex !in children.indices) return tunnels
    val moved = children.removeAt(fromIndex)
    children.add(toIndex, moved)
    return others + children.mapIndexed { index, tunnel -> tunnel.copy(position = index) }
}

private fun reindexRoot(
    root: List<RootItem>,
    tunnels: List<TunnelConfig>,
    groupsOverride: List<TunnelGroup>? = null,
): Pair<List<TunnelGroup>, List<TunnelConfig>> {
    val grouped = tunnels.filter { it.groupId != null }
    val newGroups = mutableListOf<TunnelGroup>()
    val newUngrouped = mutableListOf<TunnelConfig>()
    root.forEachIndexed { index, item ->
        when (item) {
            is RootItem.Group -> newGroups += item.group.copy(position = index)
            is RootItem.Tunnel -> newUngrouped += item.tunnel.copy(position = index)
        }
    }
    val groups =
        groupsOverride?.map { group -> newGroups.firstOrNull { it.id == group.id } ?: group }
            ?: newGroups
    return groups to (grouped + newUngrouped)
}

fun applyDisplayedOrder(
    rows: List<TunnelListRow>,
    groups: List<TunnelGroup>,
    tunnels: List<TunnelConfig>,
    scopeGroupId: Int?,
): Pair<List<TunnelGroup>, List<TunnelConfig>> {
    if (scopeGroupId != null) {
        val children = rows.filterIsInstance<TunnelListRow.TunnelRow>().map { it.tunnel }
        val others = tunnels.filter { it.groupId != scopeGroupId }
        return groups to
            others + children.mapIndexed { index, tunnel -> tunnel.copy(position = index) }
    }
    val newGroups = mutableListOf<TunnelGroup>()
    val newUngrouped = mutableListOf<TunnelConfig>()
    rows.forEachIndexed { index, row ->
        when (row) {
            is TunnelListRow.GroupHeader -> newGroups += row.group.copy(position = index)
            is TunnelListRow.TunnelRow -> newUngrouped += row.tunnel.copy(position = index)
        }
    }
    return newGroups to (tunnels.filter { it.groupId != null } + newUngrouped)
}
