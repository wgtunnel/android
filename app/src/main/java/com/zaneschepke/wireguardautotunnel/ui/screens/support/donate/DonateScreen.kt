package com.zaneschepke.wireguardautotunnel.ui.screens.support.donate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.screens.support.donate.components.DonationHeroSection
import com.zaneschepke.wireguardautotunnel.ui.screens.support.donate.components.GoogleDonationMessage
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.extensions.openWebUrl
import com.zaneschepke.wireguardautotunnel.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun DonateScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val uiState by viewModel.container.stateFlow.collectAsStateWithLifecycle()
    if (uiState.isLoading) return
    val context = LocalContext.current
    val navController = LocalNavController.current
    val isGoogleFlavor = remember { BuildConfig.FLAVOR == Constants.GOOGLE_PLAY_FLAVOR }
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        DonationHeroSection()
        Column {
            GroupLabel(
                stringResource(R.string.options),
                Modifier.padding(bottom = if (isGoogleFlavor) 8.dp else 0.dp)
                    .padding(horizontal = 16.dp),
            )
            if (!isGoogleFlavor) {
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.CurrencyBitcoin, contentDescription = null) },
                    title = stringResource(R.string.crypto),
                    onClick = { navController.push(Route.Addresses) },
                )
                SurfaceRow(
                    leading = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.github),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    title = stringResource(R.string.github_sponsors),
                    trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                    onClick = {
                        context.openWebUrl(context.getString(R.string.github_sponsors_url))
                    },
                )
                SurfaceRow(
                    leading = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.liberapay),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    title = stringResource(R.string.liberapay),
                    trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                    onClick = { context.openWebUrl(context.getString(R.string.liberapay_url)) },
                )
                SurfaceRow(
                    leading = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.kofi),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    title = stringResource(R.string.kofi),
                    trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                    onClick = { context.openWebUrl(context.getString(R.string.kofi_url)) },
                )
            } else {
                GoogleDonationMessage()
            }
            SurfaceRow(
                leading = {
                    Icon(
                        Icons.Outlined.Done,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                title = stringResource(R.string.already_donated),
                description = {
                    DescriptionText(stringResource(R.string.already_donated_description))
                },
                trailing = {
                    ThemedSwitch(
                        checked = uiState.settings.alreadyDonated,
                        onClick = { viewModel.setAlreadyDonated(it) },
                    )
                },
                onClick = { viewModel.setAlreadyDonated(!uiState.settings.alreadyDonated) },
            )
        }
    }
}
