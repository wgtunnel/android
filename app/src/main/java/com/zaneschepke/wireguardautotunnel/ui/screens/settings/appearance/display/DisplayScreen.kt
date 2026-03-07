package com.zaneschepke.wireguardautotunnel.ui.screens.settings.appearance.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.theme.Theme
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun DisplayScreen(sharedViewModel: SharedAppViewModel = koinActivityViewModel()) {

    val appState by sharedViewModel.container.stateFlow.collectAsStateWithLifecycle()

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier.fillMaxSize(),
    ) {
        enumValues<Theme>().forEach {
            val title =
                when (it) {
                    Theme.DARK -> stringResource(R.string.dark)
                    Theme.LIGHT -> stringResource(R.string.light)
                    Theme.AUTOMATIC -> stringResource(R.string.automatic)
                    Theme.DYNAMIC -> stringResource(R.string.dynamic)
                    Theme.DARKER -> stringResource(R.string.darker)
                    Theme.AMOLED -> stringResource(R.string.amoled)
                }
            SurfaceRow(
                title = title,
                trailing =
                    if (appState.theme == it) {
                        {
                            Icon(
                                Icons.Outlined.Check,
                                stringResource(id = R.string.selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                onClick = { sharedViewModel.setTheme(it) },
            )
        }
    }
}
