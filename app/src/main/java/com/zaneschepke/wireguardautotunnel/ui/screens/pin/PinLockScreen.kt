package com.zaneschepke.wireguardautotunnel.ui.screens.pin

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import xyz.teamgravity.pin_lock_compose.PinLock
import xyz.teamgravity.pin_lock_compose.PinManager

@Composable
fun PinLockScreen(sharedViewModel: SharedAppViewModel = koinActivityViewModel()) {
    val navController = LocalNavController.current
    val pinAlreadyExists by rememberSaveable { mutableStateOf(PinManager.pinExists()) }
    var pinCreated by rememberSaveable { mutableStateOf(false) }

    fun onPinCorrect() {
        sharedViewModel.authenticated()
        navController.popUpTo(Route.Tunnels)
    }

    PinLock(
        title = {
            Text(
                color = MaterialTheme.colorScheme.onSurface,
                text =
                    if (pinAlreadyExists) {
                        stringResource(id = R.string.enter_pin)
                    } else {
                        stringResource(id = R.string.create_pin)
                    },
            )
        },
        backgroundColor = MaterialTheme.colorScheme.surface,
        textColor = MaterialTheme.colorScheme.onSurface,
        onPinCorrect = { onPinCorrect() },
        onPinIncorrect = {
            sharedViewModel.showToast(StringValue.StringResource(R.string.incorrect_pin))
        },
        onPinCreated = {
            pinCreated = true
            sharedViewModel.showToast(StringValue.StringResource(R.string.pin_created))
            sharedViewModel.setPinLockEnabled(true)
            onPinCorrect()
        },
    )
    BackHandler(enabled = (!pinAlreadyExists && !pinCreated)) {
        PinManager.clearPin()
        navController.push(Route.Settings)
    }
}
