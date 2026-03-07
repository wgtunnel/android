package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.sort

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.ExpandingRowListItem
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.theme.AlertRed
import com.zaneschepke.wireguardautotunnel.ui.theme.SilverTree
import com.zaneschepke.wireguardautotunnel.ui.theme.Straw
import com.zaneschepke.wireguardautotunnel.util.extensions.isSortedBy
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectSideEffect
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SortScreen(sharedViewModel: SharedAppViewModel = koinActivityViewModel()) {
    val tunnelsUiState by sharedViewModel.tunnelsUiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    val isTv = LocalIsAndroidTV.current

    var sortAscending by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var editableTunnels by rememberSaveable { mutableStateOf(tunnelsUiState.tunnels) }
    var latencies by rememberSaveable { mutableStateOf<Map<Int, Double>>(emptyMap()) }

    sharedViewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            LocalSideEffect.SaveChanges -> {
                sharedViewModel.saveSortChanges(editableTunnels)
            }
            LocalSideEffect.Sort -> {
                sortAscending =
                    when (sortAscending) {
                        null -> !editableTunnels.isSortedBy { it.name }
                        true -> false
                        false -> null
                    }
                editableTunnels =
                    when (sortAscending) {
                        true -> editableTunnels.sortedBy { it.name }
                        false -> editableTunnels.sortedByDescending { it.name }
                        null -> tunnelsUiState.tunnels
                    }
            }
            LocalSideEffect.SortByLatency -> {
                sharedViewModel.sortByLatency(editableTunnels)
            }
            is LocalSideEffect.LatencySortFinished -> {
                editableTunnels = sideEffect.tunnels
                latencies = sideEffect.latencies
            }
            else -> Unit
        }
    }

    val lazyListState = rememberLazyListState()

    val reorderableLazyListState =
        rememberReorderableLazyListState(
            lazyListState,
            scrollThresholdPadding = WindowInsets.systemBars.asPaddingValues(),
        ) { from, to ->
            editableTunnels =
                editableTunnels.toMutableList().apply { add(to.index, removeAt(from.index)) }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }

    LazyColumn(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Top),
        modifier =
            Modifier.pointerInput(Unit) {
                    if (tunnelsUiState.tunnels.isEmpty()) return@pointerInput
                }
                .overscroll(rememberOverscrollEffect()),
        state = lazyListState,
        userScrollEnabled = true,
        reverseLayout = false,
        flingBehavior = ScrollableDefaults.flingBehavior(),
    ) {
        itemsIndexed(editableTunnels, key = { _, tunnel -> tunnel.id }) { index, tunnel ->
            ReorderableItem(reorderableLazyListState, tunnel.id) { isDragging ->
                val latency = latencies[tunnel.id]
                val text = buildAnnotatedString {
                    append(tunnel.name)
                    if (latency != null && latency != Double.MAX_VALUE) {
                        append(" - ")
                        val color =
                            when (latency) {
                                in 0.0..50.0 -> SilverTree
                                in 50.0..150.0 -> Straw
                                else -> AlertRed
                            }
                        withStyle(style = SpanStyle(color = color)) {
                            append("${latency.toInt()}ms")
                        }
                    }
                }
                ExpandingRowListItem(
                    leading = {},
                    text = text,
                    trailing = {
                        if (!isTv)
                            Icon(Icons.Default.DragHandle, stringResource(R.string.drag_handle))
                        else
                            Row {
                                IconButton(
                                    onClick = {
                                        editableTunnels =
                                            editableTunnels.toMutableList().apply {
                                                add(index - 1, removeAt(index))
                                            }
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
                                        editableTunnels =
                                            editableTunnels.toMutableList().apply {
                                                add(index + 1, removeAt(index))
                                            }
                                    },
                                    enabled = index != editableTunnels.size - 1,
                                ) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        stringResource(R.string.move_down),
                                    )
                                }
                            }
                    },
                    isSelected = isDragging,
                    expanded = {},
                    modifier =
                        Modifier.draggableHandle(
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(
                                    HapticFeedbackType.GestureThresholdActivate
                                )
                            },
                            onDragStopped = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            },
                            dragGestureDetector = DragGestureDetector.LongPress,
                        ),
                )
            }
        }
    }
}
