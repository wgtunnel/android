package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelGroup
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSelectionSheet(
    groups: List<TunnelGroup>,
    tunnels: List<TunnelConfig>,
    selectedCount: Int,
    onCreateGroup: (name: String) -> Unit,
    onMoveToGroup: (groupId: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        SecondaryTabRow(
            selectedTabIndex = tabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Tab(
                selected = tabIndex == 0,
                onClick = { tabIndex = 0 },
                text = { Text(stringResource(R.string.create)) },
            )
            Tab(
                selected = tabIndex == 1,
                onClick = { tabIndex = 1 },
                text = { Text(stringResource(R.string.move)) },
            )
        }
        when (tabIndex) {
            0 ->
                CreateGroupTab(
                    selectedCount = selectedCount,
                    onCreateGroup = {
                        onCreateGroup(it)
                        onDismiss()
                    },
                )
            else ->
                MoveToGroupTab(
                    groups = groups,
                    tunnels = tunnels,
                    selectedCount = selectedCount,
                    onMoveToGroup = {
                        onMoveToGroup(it)
                        onDismiss()
                    },
                )
        }
    }
}

private val GroupSelectionTabHeight = 280.dp

@Composable
private fun CreateGroupTab(selectedCount: Int, onCreateGroup: (name: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val moveMessage =
        pluralStringResource(R.plurals.tunnels_will_move_to_group, selectedCount, selectedCount)

    Column(modifier = Modifier.height(GroupSelectionTabHeight).padding(bottom = 16.dp)) {
        GroupLabel(
            stringResource(R.string.group_name),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ConfigurationTextBox(
            value = name,
            label = "",
            hint = stringResource(R.string.add_group),
            onValueChange = { name = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SurfaceRow(
            leading = {
                Box(
                    modifier =
                        Modifier.size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$selectedCount",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            title =
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp)) {
                        append(moveMessage)
                    }
                },
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { onCreateGroup(name.trim()) },
            enabled = name.isNotBlank(),
            colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.create_group))
        }
    }
}

@Composable
private fun MoveToGroupTab(
    groups: List<TunnelGroup>,
    tunnels: List<TunnelConfig>,
    selectedCount: Int,
    onMoveToGroup: (groupId: Int) -> Unit,
) {
    var selectedGroupId by remember { mutableStateOf<Int?>(null) }
    val sortedGroups = remember(groups) { groups.sortedBy { it.name.lowercase() } }
    // Swallow scroll the list can't consume so it doesn't bubble up and drag the sheet closed.
    val listNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = available
        }
    }

    Column(modifier = Modifier.height(GroupSelectionTabHeight).padding(bottom = 16.dp)) {
        GroupLabel(
            pluralStringResource(R.plurals.move_n_tunnels_to, selectedCount, selectedCount),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f).nestedScroll(listNestedScrollConnection)) {
            items(sortedGroups, key = { it.id }) { group ->
                val count = tunnels.count { it.groupId == group.id }
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    title = group.name,
                    description = {
                        DescriptionText(
                            pluralStringResource(R.plurals.group_tunnel_count, count, count),
                        )
                    },
                    trailing = {
                        RadioButton(selected = selectedGroupId == group.id, onClick = null)
                    },
                    onClick = { selectedGroupId = group.id },
                )
            }
        }
        Button(
            onClick = { selectedGroupId?.let(onMoveToGroup) },
            enabled = groups.isNotEmpty() && selectedGroupId != null,
            colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(pluralStringResource(R.plurals.move_n_tunnels, selectedCount, selectedCount))
        }
    }
}
