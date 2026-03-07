package com.zaneschepke.wireguardautotunnel.ui.navigation.functions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.zaneschepke.wireguardautotunnel.ui.navigation.NavController

@Composable
fun rememberNavController(
    backStack: NavBackStack<NavKey>,
    isDisclosureShown: Boolean,
    onChange: (NavKey?) -> Unit = {},
    onExitApp: () -> Unit = {},
): NavController {
    return remember(backStack, isDisclosureShown, onChange, onExitApp) {
        NavController(backStack, isDisclosureShown, onChange, onExitApp)
    }
}
