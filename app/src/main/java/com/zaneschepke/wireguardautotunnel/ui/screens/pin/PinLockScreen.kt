package com.zaneschepke.wireguardautotunnel.ui.screens.pin

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinActivityViewModel
import xyz.teamgravity.pin_lock_compose.PinLock
import xyz.teamgravity.pin_lock_compose.PinManager

@Composable
fun PinLockScreen(sharedViewModel: SharedAppViewModel = koinActivityViewModel()) {
    val navController = LocalNavController.current
    // Returns at once after the first call and must run before pinExists()
    PinManager.initialize(context = LocalContext.current)
    val pinAlreadyExists by rememberSaveable { mutableStateOf(PinManager.pinExists()) }
    var pinCreated by rememberSaveable { mutableStateOf(false) }
    // Bumped on each wrong PIN to show the message again
    var incorrectAttempts by remember { mutableIntStateOf(0) }
    var showIncorrect by remember { mutableStateOf(false) }
    LaunchedEffect(incorrectAttempts) {
        if (incorrectAttempts == 0) return@LaunchedEffect
        showIncorrect = true
        delay(INCORRECT_MESSAGE_MS)
        showIncorrect = false
    }

    fun onPinCorrect() {
        sharedViewModel.authenticated()
        if (navController.currentRoute is Route.Lock) navController.popUpTo(Route.Tunnels)
    }

    PinLock(
        title = {
            Text(
                color =
                    if (showIncorrect) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                text =
                    when {
                        showIncorrect -> stringResource(id = R.string.incorrect_pin)
                        pinAlreadyExists -> stringResource(id = R.string.enter_pin)
                        else -> stringResource(id = R.string.create_pin)
                    },
                // Read out by screen readers, since a toast would sit beneath the lock
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        },
        onPinCorrect = { onPinCorrect() },
        onPinIncorrect = { incorrectAttempts++ },
        onPinCreated = {
            pinCreated = true
            sharedViewModel.showSnackMessage(
                StringValue.StringResource(R.string.pin_created),
                ToastType.Success,
            )
            // Unlock first so turning the lock on doesn't show the PIN screen again
            onPinCorrect()
            sharedViewModel.setPinLockEnabled(true)
        },
    )
    BackHandler(enabled = (!pinAlreadyExists && !pinCreated)) {
        PinManager.clearPin()
        navController.push(Route.Settings)
    }
}

private const val INCORRECT_MESSAGE_MS = 2_000L
