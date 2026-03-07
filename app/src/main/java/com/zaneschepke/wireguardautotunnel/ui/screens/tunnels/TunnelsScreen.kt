package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberClipboardHelper
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberFileImportLauncherForResult
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components.ExportTunnelsBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components.TunnelImportSheet
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components.TunnelList
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components.UrlImportDialog
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import com.zaneschepke.wireguardautotunnel.util.StringValue
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

    val uiState by sharedViewModel.tunnelsUiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) return

    var showExportSheet by rememberSaveable { mutableStateOf(false) }
    var showImportSheet by rememberSaveable { mutableStateOf(false) }
    var showDeleteModal by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }

    sharedViewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            LocalSideEffect.Sheet.ImportTunnels -> showImportSheet = true
            LocalSideEffect.Modal.DeleteTunnels -> showDeleteModal = true
            LocalSideEffect.Sheet.ExportTunnels -> showExportSheet = true
            LocalSideEffect.SelectedTunnels.Copy -> sharedViewModel.copySelectedTunnel()
            LocalSideEffect.SelectedTunnels.SelectAll -> sharedViewModel.toggleSelectAllTunnels()
            else -> Unit
        }
    }

    val tunnelFileImportResultLauncher =
        rememberFileImportLauncherForResult(
            onNoFileExplorer = {
                sharedViewModel.showSnackMessage(
                    StringValue.StringResource(R.string.error_no_file_explorer)
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
                        StringValue.StringResource(R.string.camera_permission_required)
                    )
                }
                is QRResult.QRSuccess -> {
                    result.content.rawValue?.let { sharedViewModel.importFromQr(it) }
                        ?: sharedViewModel.showSnackMessage(
                            StringValue.StringResource(R.string.config_error)
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
                    StringValue.StringResource(R.string.camera_permission_required)
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
            title = stringResource(R.string.delete_tunnel),
            body = { Text(text = stringResource(R.string.delete_tunnel_message)) },
            confirmText = stringResource(R.string.yes),
        )
    }

    if (showExportSheet) {
        ExportTunnelsBottomSheet({ type, uri ->
            sharedViewModel.exportSelectedTunnels(type, uri)
            showExportSheet = false
        }) {
            showExportSheet = false
            sharedViewModel.clearSelectedTunnels()
        }
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
            onManualImportClick = { navController.push(Route.Config(null)) },
            onUrlClick = { showUrlDialog = true },
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
