package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelGroup
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberClipboardHelper
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberFileExportLauncherForResult
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberFileImportLauncherForResult
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.CustomBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.SheetOption
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components.AddMenuSheet
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components.GroupNameDialog
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components.MoveToGroupSheet
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components.SelectionActionsSheet
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components.TunnelImportSheet
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components.TunnelList
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components.UrlImportDialog
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.asExportFileName
import com.zaneschepke.wireguardautotunnel.util.extensions.asFileExportName
import com.zaneschepke.wireguardautotunnel.util.extensions.hasSAFSupport
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectSideEffect
import timber.log.Timber

@Composable
fun TunnelsScreen(sharedViewModel: SharedAppViewModel = koinActivityViewModel()) {
    val navController = LocalNavController.current
    val clipboard = rememberClipboardHelper()
    val context = LocalContext.current
    val isTv = LocalIsAndroidTV.current

    val uiState by sharedViewModel.tunnelsUiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) return

    val selectedTunnelsExportLauncher =
        rememberFileExportLauncherForResult(
            onSuccess = { uri -> sharedViewModel.exportSelectedTunnels(uri) },
            onCanceled = {
                sharedViewModel.showSnackMessage(
                    StringValue.StringResource(R.string.export_canceled),
                    ToastType.Warning,
                )
            },
            onUnsupported = { sharedViewModel.exportSelectedTunnels(uri = null) },
        )

    var showAddMenu by rememberSaveable { mutableStateOf(false) }
    var showImportSheet by rememberSaveable { mutableStateOf(false) }
    var showDeleteModal by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showNewGroupDialog by rememberSaveable { mutableStateOf(false) }
    var showMoveToGroup by rememberSaveable { mutableStateOf(false) }
    var showNewGroupForSelection by rememberSaveable { mutableStateOf(false) }
    var showSelectionActions by rememberSaveable { mutableStateOf(false) }
    var showReorderActions by rememberSaveable { mutableStateOf(false) }
    var renameGroup by remember { mutableStateOf<TunnelGroup?>(null) }
    var deleteGroup by remember { mutableStateOf<TunnelGroup?>(null) }
    var deleteGroupAndTunnels by remember { mutableStateOf<TunnelGroup?>(null) }
    var exportGroup by remember { mutableStateOf<TunnelGroup?>(null) }

    sharedViewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            LocalSideEffect.Sheet.AddMenu -> showAddMenu = true
            LocalSideEffect.Sheet.ImportTunnels -> showImportSheet = true
            LocalSideEffect.Sheet.MoveToGroup -> showMoveToGroup = true
            LocalSideEffect.Sheet.SelectionActions -> showSelectionActions = true
            LocalSideEffect.Sheet.ReorderActions -> showReorderActions = true
            LocalSideEffect.LaunchExportPicker -> {
                val (fileName, mimeType) = uiState.tunnelsForExport.asFileExportName()
                // Fallback, especially for TV, for downloads export
                if (isTv && !context.hasSAFSupport(mimeType)) {
                    sharedViewModel.exportSelectedTunnels(uri = null)
                } else {
                    selectedTunnelsExportLauncher.launch(fileName)
                }
            }
            LocalSideEffect.SelectedTunnels.Copy -> sharedViewModel.copySelectedTunnel()
            LocalSideEffect.SelectedTunnels.SelectAll -> sharedViewModel.toggleSelectAllTunnels()
            else -> Unit
        }
    }

    BackHandler(enabled = uiState.isReorderMode || uiState.isSelectionMode) {
        when {
            uiState.isReorderMode && uiState.reorderScopeGroupId != null ->
                sharedViewModel.exitReorderScope()
            uiState.isReorderMode -> sharedViewModel.cancelReorder()
            else -> sharedViewModel.clearSelectedTunnels()
        }
    }

    val groupExportLauncher =
        rememberFileExportLauncherForResult(
            onSuccess = { uri ->
                exportGroup?.let { sharedViewModel.exportGroup(it, uri) }
                exportGroup = null
            },
            onCanceled = {
                exportGroup = null
                sharedViewModel.showSnackMessage(
                    StringValue.StringResource(R.string.export_canceled),
                    ToastType.Warning,
                )
            },
            onUnsupported = {
                exportGroup?.let { sharedViewModel.exportGroup(it, uri = null) }
                exportGroup = null
            },
        )

    val tunnelFileImportResultLauncher =
        rememberFileImportLauncherForResult(
            onNoFileExplorer = {
                sharedViewModel.showSnackMessage(
                    StringValue.StringResource(R.string.error_no_file_explorer),
                    ToastType.Error,
                )
            },
            onData = { data -> sharedViewModel.importFromUri(data) },
        )

    val scanQrCodeLauncher =
        rememberLauncherForActivityResult(ScanQRCode()) { result ->
            when (result) {
                is QRResult.QRError -> {
                    Timber.e(result.exception, "QR Code")
                }
                QRResult.QRMissingPermission -> {
                    sharedViewModel.showSnackMessage(
                        StringValue.StringResource(R.string.camera_permission_required),
                        ToastType.Warning,
                    )
                }
                is QRResult.QRSuccess -> {
                    result.content.rawValue?.let { sharedViewModel.importFromQr(it) }
                        ?: sharedViewModel.showSnackMessage(
                            StringValue.StringResource(R.string.config_error),
                            ToastType.Error,
                        )
                }
                QRResult.QRUserCanceled -> Unit
            }
        }

    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted
            ->
            if (!isGranted) {
                sharedViewModel.showSnackMessage(
                    StringValue.StringResource(R.string.camera_permission_required),
                    ToastType.Warning,
                )
                return@rememberLauncherForActivityResult
            }
            scanQrCodeLauncher.launch(null)
        }

    if (showDeleteModal) {
        InfoDialog(
            onDismiss = { showDeleteModal = false },
            onAttest = {
                sharedViewModel.deleteSelectedTunnels()
                showDeleteModal = false
            },
            title =
                stringResource(
                    if (uiState.selectedTunnels.isEmpty() && uiState.selectedGroups.isNotEmpty())
                        R.string.delete_group
                    else R.string.delete_tunnel
                ),
            body = {
                Text(
                    text =
                        stringResource(
                            if (
                                uiState.selectedTunnels.isEmpty() &&
                                    uiState.selectedGroups.isNotEmpty()
                            )
                                R.string.delete_group_message
                            else R.string.delete_tunnel_message
                        )
                )
            },
            confirmText = stringResource(R.string.yes),
        )
    }

    if (showAddMenu) {
        AddMenuSheet(
            onDismiss = { showAddMenu = false },
            onNewGroupClick = { showNewGroupDialog = true },
            onImportClick = { showImportSheet = true },
        )
    }

    if (showImportSheet) {
        TunnelImportSheet(
            onDismiss = { showImportSheet = false },
            onFileClick = {
                tunnelFileImportResultLauncher.launch(FileUtils.ALLOWED_TV_FILE_TYPES)
            },
            onQrClick = { requestPermissionLauncher.launch(android.Manifest.permission.CAMERA) },
            onClipboardClick = {
                clipboard.paste { result ->
                    if (result != null) sharedViewModel.importFromClipboard(result)
                }
            },
            onManualImportClick = { navController.push(Route.ConfigEdit(null)) },
            onUrlClick = { showUrlDialog = true },
        )
    }

    if (showNewGroupDialog) {
        GroupNameDialog(
            title = stringResource(R.string.add_group),
            initialName = "",
            onDismiss = { showNewGroupDialog = false },
            onConfirm = { name ->
                sharedViewModel.createGroup(name)
                showNewGroupDialog = false
            },
        )
    }

    renameGroup?.let { group ->
        GroupNameDialog(
            title = stringResource(R.string.rename_group),
            initialName = group.name,
            onDismiss = { renameGroup = null },
            onConfirm = { name ->
                sharedViewModel.renameGroup(group, name)
                renameGroup = null
            },
        )
    }

    if (showMoveToGroup) {
        MoveToGroupSheet(
            groups = uiState.groups,
            onDismiss = { showMoveToGroup = false },
            onSelect = { group -> sharedViewModel.moveSelectedToGroup(group.id) },
            onNewGroup = { showNewGroupForSelection = true },
        )
    }

    if (showNewGroupForSelection) {
        GroupNameDialog(
            title = stringResource(R.string.add_group),
            initialName = "",
            onDismiss = { showNewGroupForSelection = false },
            onConfirm = { name ->
                sharedViewModel.createGroupAndMoveSelected(name)
                showNewGroupForSelection = false
            },
        )
    }

    if (showSelectionActions) {
        SelectionActionsSheet(
            uiState = uiState,
            onDismiss = { showSelectionActions = false },
            onSelectAll = sharedViewModel::toggleSelectAllTunnels,
            onMoveToGroup = { showMoveToGroup = true },
            onUngroup = sharedViewModel::ungroupSelected,
            onExport = {
                val (fileName, mimeType) = uiState.tunnelsForExport.asFileExportName()
                if (isTv && !context.hasSAFSupport(mimeType)) {
                    sharedViewModel.exportSelectedTunnels(uri = null)
                } else {
                    selectedTunnelsExportLauncher.launch(fileName)
                }
            },
            onCopy = sharedViewModel::copySelectedTunnel,
            onDelete = { showDeleteModal = true },
            onRenameGroup = { renameGroup = it },
            onExportGroup = { group ->
                exportGroup = group
                val (fileName, mimeType) = group.asExportFileName()
                if (isTv && !context.hasSAFSupport(mimeType)) {
                    sharedViewModel.exportGroup(group, uri = null)
                    exportGroup = null
                } else {
                    groupExportLauncher.launch(fileName)
                }
            },
            onDeleteGroup = { deleteGroup = it },
            onDeleteGroupAndTunnels = { deleteGroupAndTunnels = it },
        )
    }

    if (showReorderActions) {
        CustomBottomSheet(
            listOf(
                SheetOption(
                    Icons.Rounded.SortByAlpha,
                    stringResource(R.string.sort),
                    onClick = {
                        showReorderActions = false
                        sharedViewModel.sortReorderByName()
                    },
                ),
                SheetOption(
                    Icons.Rounded.NetworkCheck,
                    stringResource(R.string.sort_by_latency),
                    onClick = {
                        showReorderActions = false
                        sharedViewModel.sortReorderByLatency()
                    },
                ),
            )
        ) {
            showReorderActions = false
        }
    }

    deleteGroup?.let { group ->
        InfoDialog(
            onDismiss = { deleteGroup = null },
            onAttest = {
                sharedViewModel.ungroup(group)
                deleteGroup = null
            },
            title = stringResource(R.string.delete_group),
            body = { Text(text = stringResource(R.string.delete_group_message)) },
            confirmText = stringResource(R.string.yes),
        )
    }

    deleteGroupAndTunnels?.let { group ->
        InfoDialog(
            onDismiss = { deleteGroupAndTunnels = null },
            onAttest = {
                sharedViewModel.deleteGroupAndTunnels(group)
                deleteGroupAndTunnels = null
            },
            title = stringResource(R.string.delete_group_and_tunnels),
            body = { Text(text = stringResource(R.string.delete_group_and_tunnels_message)) },
            confirmText = stringResource(R.string.yes),
        )
    }

    if (showUrlDialog) {
        UrlImportDialog(
            onDismiss = { showUrlDialog = false },
            onConfirm = { url ->
                sharedViewModel.importFromUrl(url)
                showUrlDialog = false
            },
        )
    }

    TunnelList(uiState, Modifier.fillMaxSize(), sharedViewModel)
}
