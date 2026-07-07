package com.zaneschepke.wireguardautotunnel.ui.screens.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Reviews
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberClipboardHelper
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.screens.support.components.PermissionDialog
import com.zaneschepke.wireguardautotunnel.ui.screens.support.components.UpdateDialog
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.launchPlayStoreListing
import com.zaneschepke.wireguardautotunnel.util.extensions.launchPlayStoreReview
import com.zaneschepke.wireguardautotunnel.util.extensions.launchSupportEmail
import com.zaneschepke.wireguardautotunnel.util.extensions.openWebUrl
import com.zaneschepke.wireguardautotunnel.viewmodel.SupportViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun SupportScreen(viewModel: SupportViewModel = koinViewModel()) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val isTv = LocalIsAndroidTV.current
    val scope = rememberCoroutineScope()

    val supportState by viewModel.collectAsState()

    val clipboardManager = rememberClipboardHelper()

    val issuesUrl = stringResource(R.string.github_url)
    val izzyUrl = stringResource(R.string.fdroid_url)
    val telegramUrl = stringResource(R.string.telegram_url)
    val matrixUrl = stringResource(R.string.matrix_url)
    val docsUrl = stringResource(R.string.docs_url)
    val websiteUrl = stringResource(R.string.website_url)
    val translationUrl = stringResource(R.string.translation_url)
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
    val playStoreUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"
    val playReviewsUrl =
        "https://play.google.com/store/apps/details?id=${context.packageName}&showAllReviews=true"

    val version = remember {
        "v${BuildConfig.VERSION_NAME +
                if(BuildConfig.DEBUG) "-debug" else "" }"
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTv) {
            focusRequester.requestFocus()
        }
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

    fun openWebUrl(url: String) {
        context.openWebUrl(url).onFailure {
            scope.launch {
                viewModel.postSideEffect(
                    GlobalSideEffect.Snackbar(
                        StringValue.StringResource(R.string.no_browser_detected),
                        ToastType.Error,
                    )
                )
            }
        }
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
                modifier = Modifier.focusRequester(focusRequester),
            )
            SurfaceRow(
                stringResource(R.string.docs_description),
                onClick = { openWebUrl(docsUrl) },
                leading = { Icon(Icons.Outlined.Book, contentDescription = null) },
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
            )
            SurfaceRow(
                stringResource(R.string.website),
                onClick = { openWebUrl(websiteUrl) },
                leading = { Icon(Icons.Outlined.Web, contentDescription = null) },
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
            )
            SurfaceRow(
                stringResource(R.string.translation),
                onClick = { openWebUrl(translationUrl) },
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
                onClick = { openWebUrl(privacyPolicyUrl) },
            )
            if (BuildConfig.FLAVOR == Constants.GOOGLE_PLAY_FLAVOR) {
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.Reviews, contentDescription = null) },
                    title = stringResource(R.string.review),
                    trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                    onClick = {
                        context.launchPlayStoreReview().onFailure { openWebUrl(playReviewsUrl) }
                    },
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
                onClick = { openWebUrl(matrixUrl) },
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
                onClick = { openWebUrl(telegramUrl) },
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
                onClick = { openWebUrl(issuesUrl) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Mail, contentDescription = null) },
                title = stringResource(R.string.email_description),
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                onClick = {
                    context.launchSupportEmail().onFailure {
                        scope.launch {
                            viewModel.postSideEffect(
                                GlobalSideEffect.Snackbar(
                                    StringValue.StringResource(R.string.no_email_detected),
                                    ToastType.Error,
                                )
                            )
                        }
                    }
                },
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
                    if (BuildConfig.DEBUG) {
                        scope.launch {
                            viewModel.postSideEffect(
                                GlobalSideEffect.Snackbar(
                                    StringValue.StringResource(R.string.update_check_unsupported),
                                    ToastType.Warning,
                                )
                            )
                        }
                        return@SurfaceRow
                    }
                    when (BuildConfig.FLAVOR) {
                        Constants.GOOGLE_PLAY_FLAVOR ->
                            context.launchPlayStoreListing().onFailure { openWebUrl(playStoreUrl) }
                        Constants.FDROID_FLAVOR -> openWebUrl(izzyUrl)
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
