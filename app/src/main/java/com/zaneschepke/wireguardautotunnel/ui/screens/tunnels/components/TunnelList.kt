package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wgtunnel.backend.state.ActiveTunnel
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.ExpandingRowListItem
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.SwitchWithDivider
import com.zaneschepke.wireguardautotunnel.ui.common.scroll.appScrollbar
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelListRow
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelsUiState
import com.zaneschepke.wireguardautotunnel.ui.state.applyDisplayedOrder
import com.zaneschepke.wireguardautotunnel.ui.state.buildTunnelListRows
import com.zaneschepke.wireguardautotunnel.ui.theme.AlertRed
import com.zaneschepke.wireguardautotunnel.ui.theme.SilverTree
import com.zaneschepke.wireguardautotunnel.ui.theme.Straw
import com.zaneschepke.wireguardautotunnel.util.extensions.openWebUrl
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TunnelList(
    uiState: TunnelsUiState,
    modifier: Modifier = Modifier,
    viewModel: SharedAppViewModel,
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val isTv = LocalIsAndroidTV.current
    val hapticFeedback = LocalHapticFeedback.current

    val focusRequester = remember { FocusRequester() }
    var editableRows by remember { mutableStateOf(uiState.rows) }

    LaunchedEffect(Unit) {
        if (isTv) {
            focusRequester.requestFocus()
        }
    }

    val lazyListState = rememberLazyListState()

    fun persistRows(rows: List<TunnelListRow>) {
        val (groups, tunnels) =
            applyDisplayedOrder(
                rows,
                uiState.groups,
                uiState.tunnels,
                uiState.reorderScopeGroupId,
            )
        viewModel.applyReorderDraft(groups, tunnels)
    }

    val reorderableLazyListState =
        rememberReorderableLazyListState(
            lazyListState,
            scrollThresholdPadding = WindowInsets.systemBars.asPaddingValues(),
        ) { from, to ->
            editableRows =
                editableRows.toMutableList().apply { add(to.index, removeAt(from.index)) }
            persistRows(editableRows)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }

    LaunchedEffect(
        uiState.isReorderMode,
        uiState.reorderScopeGroupId,
        uiState.groups,
        uiState.tunnels,
    ) {
        if (uiState.isReorderMode && !reorderableLazyListState.isAnyItemDragging) {
            editableRows =
                buildTunnelListRows(
                    groups = uiState.groups,
                    tunnels = uiState.tunnels,
                    activeTunnelIds = uiState.backendStatus.activeTunnels.keys,
                    collapseGroups = uiState.reorderScopeGroupId == null,
                    scopeGroupId = uiState.reorderScopeGroupId,
                )
        }
    }

    val rows = if (uiState.isReorderMode) editableRows else uiState.rows

    LazyColumn(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Top),
        modifier =
            modifier
                .then(
                    if (uiState.isReorderMode) Modifier
                    else
                        Modifier.pointerInput(Unit) {
                            detectTapGestures {
                                if (uiState.tunnels.isEmpty() && uiState.groups.isEmpty()) {
                                    return@detectTapGestures
                                }
                                viewModel.clearSelectedTunnels()
                            }
                        }
                )
                .overscroll(rememberOverscrollEffect())
                .appScrollbar(
                    state = lazyListState.scrollIndicatorState,
                    orientation = Orientation.Vertical,
                ),
        state = lazyListState,
        userScrollEnabled = true,
        reverseLayout = false,
        flingBehavior = ScrollableDefaults.flingBehavior(),
    ) {
        if (uiState.tunnels.isEmpty() && uiState.groups.isEmpty()) {
            item {
                GettingStartedSection(
                    onClick = { context.openWebUrl(it) },
                    modifier = Modifier.animateItem(),
                )
            }
            return@LazyColumn
        }

        itemsIndexed(items = rows, key = { _, row -> row.key }) { index, row ->
            if (uiState.isReorderMode) {
                ReorderableItem(reorderableLazyListState, row.key) { isDragging ->
                    val title = buildAnnotatedString {
                        when (row) {
                            is TunnelListRow.GroupHeader -> {
                                // Not pinged as a unit, so there's no latency for a header
                                append(
                                    if (row.childCount == 0) row.group.name
                                    else
                                        stringResource(
                                            R.string.group_with_count,
                                            row.group.name,
                                            row.childCount,
                                        )
                                )
                            }
                            is TunnelListRow.TunnelRow -> {
                                append(row.tunnel.name)
                                val latency = uiState.reorderLatencies[row.tunnel.id]
                                if (latency != null) {
                                    append(" - ")
                                    if (latency == Double.MAX_VALUE) {
                                        withStyle(SpanStyle(color = AlertRed)) {
                                            append(stringResource(R.string.ping_unreachable))
                                        }
                                    } else {
                                        val color =
                                            when (latency) {
                                                in 0.0..50.0 -> SilverTree
                                                in 50.0..150.0 -> Straw
                                                else -> AlertRed
                                            }
                                        withStyle(SpanStyle(color = color)) {
                                            append("${latency.toInt()}ms")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ExpandingRowListItem(
                        leading = {
                            if (row is TunnelListRow.GroupHeader) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    contentDescription = row.group.name,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        text = title,
                        // Whole row, not just the folder icon, so it's an easy tap target
                        onClick =
                            if (row is TunnelListRow.GroupHeader && row.childCount > 0) {
                                { viewModel.enterReorderScope(row.group.id) }
                            } else {
                                null
                            },
                        trailing = {
                            if (!isTv) {
                                Icon(
                                    Icons.Default.DragHandle,
                                    stringResource(R.string.drag_handle),
                                )
                            } else {
                                Row {
                                    IconButton(
                                        onClick = {
                                            editableRows =
                                                editableRows.toMutableList().apply {
                                                    add(index - 1, removeAt(index))
                                                }
                                            persistRows(editableRows)
                                        },
                                        enabled = index != 0,
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            stringResource(R.string.move_up),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            editableRows =
                                                editableRows.toMutableList().apply {
                                                    add(index + 1, removeAt(index))
                                                }
                                            persistRows(editableRows)
                                        },
                                        enabled = index != rows.size - 1,
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowDownward,
                                            stringResource(R.string.move_down),
                                        )
                                    }
                                }
                            }
                        },
                        isSelected = isDragging,
                        expanded = {},
                        modifier =
                            if (!isTv)
                                Modifier.draggableHandle(
                                    onDragStarted = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.GestureThresholdActivate
                                        )
                                    },
                                    onDragStopped = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.GestureEnd
                                        )
                                    },
                                    dragGestureDetector = DragGestureDetector.LongPress,
                                )
                            else Modifier,
                    )
                }
            } else {
                ListRow(
                    row = row,
                    index = index,
                    rowCount = rows.size,
                    uiState = uiState,
                    viewModel = viewModel,
                    isTv = isTv,
                    isDragging = false,
                    focusRequester = if (index == 0) focusRequester else null,
                    onNavigateTunnel = { navController.push(Route.TunnelSettings(it)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ListRow(
    row: TunnelListRow,
    index: Int,
    rowCount: Int,
    uiState: TunnelsUiState,
    viewModel: SharedAppViewModel,
    isTv: Boolean,
    isDragging: Boolean,
    focusRequester: FocusRequester?,
    onNavigateTunnel: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (row) {
        is TunnelListRow.GroupHeader -> {
            SurfaceRow(
                modifier =
                    modifier.then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    ),
                leading = {
                    Icon(
                        if (row.visibleExpanded) Icons.Outlined.FolderOpen
                        else Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                title = row.group.name,
                description =
                    // Count of tunnels in the group, not a suffix on the name
                    if (row.childCount > 0 && !uiState.isReorderMode) {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text =
                                        pluralStringResource(
                                            R.plurals.group_tunnel_count,
                                            row.childCount,
                                            row.childCount,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (row.activeCount > 0) {
                                    Text(
                                        text = "·",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.group_active_count,
                                                row.activeCount,
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    } else null,
                onClick = {
                    when {
                        uiState.isReorderMode -> viewModel.enterReorderScope(row.group.id)
                        uiState.isSelectionMode -> viewModel.toggleSelectedGroup(row.group.id)
                        row.childCount > 0 -> viewModel.toggleGroupExpanded(row.group)
                        else -> Unit
                    }
                },
                selected = isDragging || uiState.selectedGroups.any { it.id == row.group.id },
                onLongClick =
                    if (uiState.isReorderMode) null
                    else {
                        { viewModel.toggleSelectedGroup(row.group.id) }
                    },
                trailing =
                    // Nothing to expand into, an empty group has no chevron
                    if (row.childCount > 0) {
                        { _ ->
                            Icon(
                                if (row.visibleExpanded) Icons.Rounded.ExpandLess
                                else Icons.Rounded.ExpandMore,
                                contentDescription =
                                    stringResource(
                                        if (row.visibleExpanded) R.string.collapse_group
                                        else R.string.expand_group
                                    ),
                            )
                        }
                    } else null,
            )
        }
        is TunnelListRow.TunnelRow -> {
            val tunnel = row.tunnel
            val activeTunnel = uiState.backendStatus.activeTunnels[tunnel.id] ?: ActiveTunnel()
            val displayState = remember(activeTunnel) { DisplayTunnelState.from(activeTunnel) }
            val isRunning = uiState.backendStatus.activeTunnels.containsKey(tunnel.id)
            val selected =
                remember(uiState.selectedTunnels) {
                    uiState.selectedTunnels.any { it.id == tunnel.id }
                }
            SurfaceRow(
                modifier =
                    modifier
                        .then(if (row.grouped) Modifier.padding(start = 24.dp) else Modifier)
                        .then(
                            if (focusRequester != null) Modifier.focusRequester(focusRequester)
                            else Modifier
                        ),
                leading = {
                    Icon(
                        Icons.Rounded.Circle,
                        contentDescription = stringResource(R.string.tunnel_monitoring),
                        tint = remember(displayState) { displayState.asColor() },
                        modifier = Modifier.size(14.dp),
                    )
                },
                title = tunnel.name,
                description =
                    tunnel.entryTunnelId
                        ?.let { id -> uiState.tunnels.firstOrNull { it.id == id }?.name }
                        ?.let { entryName ->
                            {
                                Text(
                                    text = stringResource(R.string.via_entry, entryName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        },
                onClick = {
                    when {
                        uiState.isReorderMode -> Unit
                        uiState.isSelectionMode -> viewModel.toggleSelectedTunnel(tunnel.id)
                        else -> {
                            onNavigateTunnel(tunnel.id)
                            viewModel.clearSelectedTunnels()
                        }
                    }
                },
                selected = selected || isDragging,
                expandedContent =
                    if (isRunning && !uiState.isReorderMode) {
                        { TunnelStatisticsRow(activeTunnel) }
                    } else {
                        null
                    },
                onLongClick =
                    if (uiState.isReorderMode) null
                    else {
                        { viewModel.toggleSelectedTunnel(tunnel.id) }
                    },
                trailing = { rowModifier ->
                    SwitchWithDivider(
                        checked = isRunning,
                        onClick = { checked ->
                            if (checked) {
                                viewModel.startTunnel(tunnel)
                            } else {
                                viewModel.stopTunnel(tunnel)
                            }
                        },
                        modifier = rowModifier,
                    )
                },
            )
        }
    }
}
