package com.zaneschepke.wireguardautotunnel.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.zaneschepke.wireguardautotunnel.ui.navigation.NavController

val LocalIsAndroidTV = staticCompositionLocalOf { false }

val LocalNavController = staticCompositionLocalOf<NavController> { error("No backstack provided") }

typealias BackStack = NavBackStack<NavKey>
