package com.zaneschepke.wireguardautotunnel.ui.screens.settings.logs.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zaneschepke.logcatter.model.LogMessage
import com.zaneschepke.wireguardautotunnel.ui.common.scroll.appScrollbar

@Composable
fun LogList(
    logs: List<LogMessage>,
    lazyColumnListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = lazyColumnListState,
        modifier =
            modifier
                .padding(horizontal = 12.dp)
                .appScrollbar(
                    state = lazyColumnListState.scrollIndicatorState,
                    orientation = Orientation.Vertical,
                ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(items = logs, key = { index, _ -> index }) { _, log -> LogItem(log = log) }
    }
}
