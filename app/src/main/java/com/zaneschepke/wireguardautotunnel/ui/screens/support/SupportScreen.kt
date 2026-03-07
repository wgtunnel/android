package com.zaneschepke.wireguardautotunnel.ui.screens.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberClipboardHelper
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.screens.support.components.PermissionDialog
import com.zaneschepke.wireguardautotunnel.ui.screens.support.components.UpdateDialog
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.extensions.*
import com.zaneschepke.wireguardautotunnel.viewmodel.SupportViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SupportScreen(viewModel: SupportViewModel = koinViewModel()) {
    val context = LocalContext.current
    val navController = LocalNavController.current

    val supportState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    val clipboardManager = rememberClipboardHelper()

    val version = remember {
        "v${BuildConfig.VERSION_NAME +
                if(BuildConfig.DEBUG) "-debug" else "" }"
    }

    var showPermissionDialog by rememberSaveable { mutableStateOf(false) }

    if (supportState.appUpdate != null) {
        UpdateDialog(
            viewModel = viewModel,
            context = context,
            onPermissionNeeded = { showPermissionDialog = true },
        )
    }

    if (showPermissionDialog) {
        PermissionDialog(context = context, onDismiss = { showPermissionDialog = false })
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
    ) {
        GroupLabel(
            stringResource(R.string.thank_you),
            modifier = Modifier.padding(horizontal = 16.dp),
            MaterialTheme.colorScheme.onSurface,
        )
        Column {
            GroupLabel(stringResource(R.string.resources), Modifier.padding(horizontal = 16.dp))
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Favorite, contentDescription = null) },
                title = stringResource(R.string.donate),
                onClick = { navController.push(Route.Donate) },
            )
            SurfaceRow(
                stringResource(R.string.docs_description),
                onClick = { context.openWebUrl(context.getString(R.string.docs_url)) },
                leading = { Icon(Icons.Outlined.Book, contentDescription = null) },
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
            )
            SurfaceRow(
                stringResource(R.string.website),
                onClick = { context.openWebUrl(context.getString(R.string.website_url)) },
                leading = { Icon(Icons.Outlined.Web, contentDescription = null) },
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
            )
            SurfaceRow(
                stringResource(R.string.translation),
                onClick = { context.openWebUrl(context.getString(R.string.translation_url)) },
                description = { DescriptionText(stringResource(R.string.help_translate)) },
                leading = { Icon(Icons.Outlined.Translate, contentDescription = null) },
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Balance, contentDescription = null) },
                title = stringResource(R.string.licenses),
                onClick = { navController.push(Route.License) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Policy, contentDescription = null) },
                title = stringResource(R.string.privacy_policy),
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                onClick = { context.openWebUrl(context.getString(R.string.privacy_policy_url)) },
            )
            if (BuildConfig.FLAVOR == Constants.GOOGLE_PLAY_FLAVOR) {
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.Reviews, contentDescription = null) },
                    title = stringResource(R.string.review),
                    trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                    onClick = { context.launchPlayStoreReview() },
                )
            }
        }
        Column {
            GroupLabel(
                stringResource(R.string.contact),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = {
                    Icon(
                        ImageVector.vectorResource(R.drawable.matrix),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                title = stringResource(R.string.join_matrix),
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                onClick = { context.openWebUrl(context.getString(R.string.matrix_url)) },
            )
            SurfaceRow(
                leading = {
                    Icon(
                        ImageVector.vectorResource(R.drawable.telegram),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                title = stringResource(R.string.join_telegram),
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                onClick = { context.openWebUrl(context.getString(R.string.telegram_url)) },
            )
            SurfaceRow(
                leading = {
                    Icon(
                        ImageVector.vectorResource(R.drawable.github),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                title = stringResource(R.string.open_issue),
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                onClick = { context.openWebUrl(context.getString(R.string.github_url)) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Mail, contentDescription = null) },
                title = stringResource(R.string.email_description),
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                onClick = { context.launchSupportEmail() },
            )
        }
        Column {
            GroupLabel(
                stringResource(R.string.other),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Memory, contentDescription = null) },
                title = stringResource(R.string.about),
                description = {
                    Column {
                        DescriptionText(stringResource(R.string.version_template, version))
                        DescriptionText(
                            stringResource(R.string.flavor_template, BuildConfig.FLAVOR)
                        )
                    }
                },
                onClick = { clipboardManager.copy(version) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.InstallMobile, contentDescription = null) },
                title = stringResource(R.string.check_for_update),
                onClick = {
                    if (BuildConfig.DEBUG)
                        return@SurfaceRow context.showToast(R.string.update_check_unsupported)
                    when (BuildConfig.FLAVOR) {
                        Constants.GOOGLE_PLAY_FLAVOR -> context.launchPlayStoreListing()
                        Constants.FDROID_FLAVOR ->
                            context.openWebUrl(context.getString(R.string.fdroid_url))
                        else -> viewModel.checkForStandaloneUpdate()
                    }
                },
                trailing =
                    if (BuildConfig.FLAVOR == Constants.STANDALONE_FLAVOR) null
                    else {
                        { Icon(Icons.AutoMirrored.Outlined.Launch, null) }
                    },
            )
        }
    }
}
