package com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.disclosure

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.disclosure.components.LocationDisclosureHeader
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun LocationDisclosureScreen(sharedViewModel: SharedAppViewModel = koinActivityViewModel()) {
    val context = LocalContext.current
    val navController = LocalNavController.current

    fun goToAutoTunnel() {
        navController.popUpTo(Route.AutoTunnel)
    }

    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            goToAutoTunnel()
        }

    LaunchedEffect(Unit) { sharedViewModel.setLocationDisclosureShown() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().padding(top = 18.dp),
    ) {
        LocationDisclosureHeader(Modifier.padding(horizontal = 16.dp))
        Column {
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                title = stringResource(R.string.launch_app_settings),
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                onClick = {
                    val intent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    settingsLauncher.launch(intent)
                },
            )
            SurfaceRow(
                leading = {
                    Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null)
                },
                title = stringResource(R.string.skip),
                onClick = { goToAutoTunnel() },
            )
        }
    }
}
