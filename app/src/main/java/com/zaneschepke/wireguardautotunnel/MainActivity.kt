package com.zaneschepke.wireguardautotunnel

import ProxySettingsScreen
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.wireguardautotunnel.data.AppDatabase
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.AppStateRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.banner.AppAlertBanner
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.VpnDeniedDialog
import com.zaneschepke.wireguardautotunnel.ui.common.snackbar.CustomSnackBar
import com.zaneschepke.wireguardautotunnel.ui.common.snackbar.SnackbarInfo
import com.zaneschepke.wireguardautotunnel.ui.common.snackbar.SnackbarType
import com.zaneschepke.wireguardautotunnel.ui.common.snackbar.rememberCustomSnackbarState
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.navigation.Tab
import com.zaneschepke.wireguardautotunnel.ui.navigation.components.BottomNavbar
import com.zaneschepke.wireguardautotunnel.ui.navigation.components.DynamicTopAppBar
import com.zaneschepke.wireguardautotunnel.ui.navigation.components.currentRouteAsNavbarState
import com.zaneschepke.wireguardautotunnel.ui.navigation.functions.rememberNavController
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.AutoTunnelScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.advanced.AutoTunnelAdvancedScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.detection.WifiDetectionMethodScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.disclosure.LocationDisclosureScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.preferred.PreferredTunnelScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.wifi.WifiSettingsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.pin.PinLockScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.SettingsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.appearance.AppearanceScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.appearance.display.DisplayScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.appearance.language.LanguageScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.dns.DnsSettingsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.integrations.AndroidIntegrationsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.lockdown.LockdownSettingsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring.TunnelMonitoringScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring.logs.LogsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring.ping.PingTargetScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.support.SupportScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.support.donate.DonateScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.support.donate.crypto.AddressesScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.support.license.LicenseScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.TunnelsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.config.ConfigScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.TunnelSettingsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.sort.SortScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.SplitTunnelScreen
import com.zaneschepke.wireguardautotunnel.ui.theme.AlertRed
import com.zaneschepke.wireguardautotunnel.ui.theme.OffWhite
import com.zaneschepke.wireguardautotunnel.ui.theme.WireguardAutoTunnelTheme
import com.zaneschepke.wireguardautotunnel.util.LocaleUtil
import com.zaneschepke.wireguardautotunnel.util.extensions.installApk
import com.zaneschepke.wireguardautotunnel.util.extensions.isRunningOnTv
import com.zaneschepke.wireguardautotunnel.util.extensions.openWebUrl
import com.zaneschepke.wireguardautotunnel.util.extensions.restartApp
import com.zaneschepke.wireguardautotunnel.util.extensions.showToast
import com.zaneschepke.wireguardautotunnel.viewmodel.ConfigViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SplitTunnelViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.TunnelViewModel
import de.raphaelebner.roomdatabasebackup.core.RoomBackup
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import xyz.teamgravity.pin_lock_compose.PinManager

class MainActivity : AppCompatActivity() {

    private val appStateRepository: AppStateRepository by inject()
    private val tunnelRepository: TunnelRepository by inject()
    private val appDatabase: AppDatabase by inject()
    private val networkMonitor: NetworkMonitor by inject()

    val viewModel by viewModel<SharedAppViewModel>()
    private lateinit var roomBackup: RoomBackup

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        roomBackup = RoomBackup(this)

        installSplashScreen().apply {
            setKeepOnScreenCondition { !viewModel.container.stateFlow.value.isAppLoaded }
        }

        setContent {
            val context = LocalContext.current
            val isTv = isRunningOnTv()
            val uiState by viewModel.container.stateFlow.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            LaunchedEffect(uiState.isAppLoaded) {
                if (uiState.isAppLoaded) {
                    uiState.locale.let { LocaleUtil.changeLocale(it) }
                }
            }

            val snackbarState = rememberCustomSnackbarState()
            var showVpnPermissionDialog by remember { mutableStateOf(false) }
            var vpnPermissionDenied by remember { mutableStateOf(false) }
            var requestingAppMode by remember {
                mutableStateOf<Pair<AppMode?, TunnelConfig?>>(Pair(null, null))
            }

            val startingStack = buildList {
                add(Route.Tunnels)
                if (intent?.action == Intent.ACTION_APPLICATION_PREFERENCES) add(Route.Settings)
                if (uiState.pinLockEnabled) add(Route.Lock)
            }

            val backStack = rememberNavBackStack(*startingStack.toTypedArray())
            var previousRoute by remember { mutableStateOf<Route?>(null) }

            val navController =
                rememberNavController(
                    backStack,
                    uiState.isLocationDisclosureShown,
                    onChange = { previousKey -> previousRoute = previousKey as? Route },
                    onExitApp = { finish() },
                )

            val vpnActivity =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                    onResult = {
                        if (it.resultCode != RESULT_OK) {
                            showVpnPermissionDialog = true
                            vpnPermissionDenied = true
                        } else {
                            vpnPermissionDenied = false
                            showVpnPermissionDialog = false
                            val (appMode, config) = requestingAppMode
                            when (appMode) {
                                AppMode.VPN -> if (config != null) viewModel.startTunnel(config)
                                AppMode.LOCK_DOWN -> viewModel.setAppMode(AppMode.LOCK_DOWN)
                                else -> Unit
                            }
                        }
                        requestingAppMode = Pair(null, null)
                    },
                )

            LaunchedEffect(Unit) {
                viewModel.globalSideEffect.collectLatest { sideEffect ->
                    when (sideEffect) {
                        GlobalSideEffect.ConfigChanged -> restartApp()
                        GlobalSideEffect.PopBackStack -> navController.pop()
                        is GlobalSideEffect.RequestVpnPermission -> {
                            requestingAppMode = Pair(sideEffect.requestingMode, sideEffect.config)
                            vpnActivity.launch(VpnService.prepare(this@MainActivity))
                        }

                        is GlobalSideEffect.Snackbar -> {
                            scope.launch {
                                snackbarState.showSnackbar(
                                    SnackbarInfo(
                                        message =
                                            buildAnnotatedString {
                                                append(sideEffect.message.asString(context))
                                            },
                                        type = sideEffect.type ?: SnackbarType.INFO,
                                        durationMs = sideEffect.durationMs ?: 4000L,
                                    )
                                )
                            }
                        }

                        is GlobalSideEffect.Toast ->
                            scope.launch { context.showToast(sideEffect.message.asString(context)) }

                        is GlobalSideEffect.LaunchUrl -> context.openWebUrl(sideEffect.url)
                        is GlobalSideEffect.InstallApk -> context.installApk(sideEffect.apk)
                    }
                }
            }

            if (!uiState.isAppLoaded) return@setContent

            var showLock by remember {
                mutableStateOf(uiState.pinLockEnabled && !uiState.isPinVerified)
            }
            LaunchedEffect(uiState.isPinVerified) { if (uiState.isPinVerified) showLock = false }

            CompositionLocalProvider(
                LocalIsAndroidTV provides isTv,
                LocalNavController provides navController,
            ) {
                WireguardAutoTunnelTheme(theme = uiState.theme) {
                    VpnDeniedDialog(
                        showVpnPermissionDialog,
                        onDismiss = {
                            showVpnPermissionDialog = false
                            vpnPermissionDenied = false
                        },
                    )

                    val annotatedMessage = buildAnnotatedString {
                        append(context.getString(R.string.donation_prompt_prefix))
                        append(" ")
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = context.getString(R.string.support),
                                styles =
                                    TextLinkStyles(
                                        style =
                                            SpanStyle(
                                                textDecoration = TextDecoration.Underline,
                                                color = MaterialTheme.colorScheme.primary,
                                            ),
                                        focusedStyle =
                                            SpanStyle(
                                                textDecoration = TextDecoration.Underline,
                                                color = MaterialTheme.colorScheme.primary,
                                                background =
                                                    MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.2f
                                                    ),
                                            ),
                                    ),
                            ) {
                                snackbarState.dismissCurrent()
                                navController.push(Route.Donate)
                            }
                        ) {
                            append(context.getString(R.string.donation_prompt_link))
                        }
                        append(" ")
                        append(context.getString(R.string.donation_prompt_suffix))
                    }

                    LaunchedEffect(Unit) {
                        if (uiState.shouldShowDonationSnackbar && !uiState.alreadyDonated) {
                            viewModel.setShouldShowDonationSnackbar(false)
                            snackbarState.showSnackbar(
                                SnackbarInfo(
                                    message = annotatedMessage,
                                    type = SnackbarType.THANK_YOU,
                                    durationMs = 30_000L,
                                )
                            )
                        }
                    }

                    if (showLock) {
                        PinManager.initialize(context = this@MainActivity)
                        PinLockScreen()
                    } else {
                        val currentRoute by remember {
                            derivedStateOf { backStack.lastOrNull() as? Route }
                        }
                        val currentTab by remember {
                            derivedStateOf { Tab.fromRoute(currentRoute ?: Route.Tunnels) }
                        }
                        val navState by
                            currentRouteAsNavbarState(
                                uiState,
                                viewModel,
                                currentRoute,
                                navController,
                            )

                        Box(modifier = Modifier.fillMaxSize()) {
                            if (uiState.appMode == AppMode.LOCK_DOWN) {
                                AppAlertBanner(
                                    stringResource(R.string.locked_down)
                                        .uppercase(Locale.current.platformLocale),
                                    OffWhite,
                                    AlertRed,
                                    modifier = Modifier.fillMaxWidth().zIndex(2f),
                                )
                            }
                            Scaffold(
                                snackbarHost = {
                                    snackbarState.SnackbarHost(
                                        modifier =
                                            Modifier.align(Alignment.BottomCenter)
                                                .padding(
                                                    bottom =
                                                        if (LocalIsAndroidTV.current) 120.dp
                                                        else 80.dp
                                                )
                                    ) { info ->
                                        CustomSnackBar(
                                            message = info.message,
                                            type = info.type,
                                            onDismiss = { snackbarState.dismissCurrent() },
                                            containerColor =
                                                MaterialTheme.colorScheme.surfaceColorAtElevation(
                                                    2.dp
                                                ),
                                            modifier =
                                                Modifier.wrapContentHeight(align = Alignment.Top),
                                        )
                                    }
                                },
                                topBar = { DynamicTopAppBar(navState) },
                                bottomBar = {
                                    if (navState.showBottomItems) {
                                        BottomNavbar(
                                            uiState.isAutoTunnelActive,
                                            currentTab,
                                            onTabSelected = { tab ->
                                                navController.popUpTo(tab.startRoute)
                                            },
                                        )
                                    }
                                },
                            ) { padding ->
                                Column(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(
                                                top = padding.calculateTopPadding().plus(8.dp),
                                                bottom = padding.calculateBottomPadding(),
                                            )
                                            .consumeWindowInsets(padding)
                                            .imePadding()
                                ) {
                                    NavDisplay(
                                        backStack = backStack,
                                        modifier = Modifier.fillMaxSize(),
                                        onBack = { navController.pop() },
                                        transitionSpec = {
                                            val initialIndex =
                                                previousRoute?.let(Tab::fromRoute)?.index ?: 0
                                            val targetIndex =
                                                currentRoute?.let(Tab::fromRoute)?.index ?: 0
                                            if (initialIndex != targetIndex) {
                                                val dir = if (targetIndex > initialIndex) 1 else -1
                                                (slideInHorizontally { dir * it } +
                                                    fadeIn()) togetherWith
                                                    (slideOutHorizontally { dir * -it } + fadeOut())
                                            } else {
                                                (slideInHorizontally { it } + fadeIn()) togetherWith
                                                    (slideOutHorizontally { -it } + fadeOut())
                                            }
                                        },
                                        popTransitionSpec = {
                                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                                (slideOutHorizontally { it } + fadeOut())
                                        },
                                        predictivePopTransitionSpec = {
                                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                                (slideOutHorizontally { it } + fadeOut())
                                        },
                                        entryDecorators =
                                            listOf(
                                                rememberSaveableStateHolderNavEntryDecorator(),
                                                rememberViewModelStoreNavEntryDecorator(),
                                            ),
                                        entryProvider =
                                            entryProvider {
                                                entry<Route.Lock> {
                                                    PinManager.initialize(
                                                        context = this@MainActivity
                                                    )
                                                    PinLockScreen()
                                                }
                                                entry<Route.Tunnels> { TunnelsScreen() }
                                                entry<Route.Sort> { SortScreen() }
                                                entry<Route.TunnelSettings> { key ->
                                                    val viewModel: TunnelViewModel =
                                                        koinViewModel(
                                                            parameters = { parametersOf(key.id) }
                                                        )
                                                    TunnelSettingsScreen(viewModel)
                                                }
                                                entry<Route.SplitTunnel> { key ->
                                                    val viewModel: SplitTunnelViewModel =
                                                        koinViewModel(
                                                            parameters = { parametersOf(key.id) }
                                                        )
                                                    SplitTunnelScreen(viewModel)
                                                }
                                                entry<Route.Config> { key ->
                                                    val viewModel: ConfigViewModel =
                                                        koinViewModel(
                                                            parameters = { parametersOf(key.id) }
                                                        )
                                                    ConfigScreen(viewModel)
                                                }
                                                entry<Route.LocationDisclosure> {
                                                    LocationDisclosureScreen()
                                                }
                                                entry<Route.AutoTunnel> { AutoTunnelScreen() }
                                                entry<Route.WifiPreferences> {
                                                    WifiSettingsScreen()
                                                }
                                                entry<Route.AdvancedAutoTunnel> {
                                                    AutoTunnelAdvancedScreen()
                                                }
                                                entry<Route.WifiDetectionMethod> {
                                                    WifiDetectionMethodScreen()
                                                }
                                                entry<Route.Settings> { SettingsScreen() }
                                                entry<Route.TunnelMonitoring> {
                                                    TunnelMonitoringScreen()
                                                }
                                                entry<Route.AndroidIntegrations> {
                                                    AndroidIntegrationsScreen()
                                                }
                                                entry<Route.Dns> { DnsSettingsScreen() }
                                                entry<Route.ConfigGlobal> { key ->
                                                    val viewModel: ConfigViewModel =
                                                        koinViewModel(
                                                            parameters = { parametersOf(key.id) }
                                                        )
                                                    ConfigScreen(viewModel)
                                                }
                                                entry<Route.SplitTunnelGlobal> { key ->
                                                    val viewModel: SplitTunnelViewModel =
                                                        koinViewModel(
                                                            parameters = { parametersOf(key.id) }
                                                        )
                                                    SplitTunnelScreen(viewModel)
                                                }
                                                entry<Route.LockdownSettings> {
                                                    LockdownSettingsScreen()
                                                }
                                                entry<Route.ProxySettings> { ProxySettingsScreen() }
                                                entry<Route.Appearance> { AppearanceScreen() }
                                                entry<Route.Language> { LanguageScreen() }
                                                entry<Route.Display> { DisplayScreen() }
                                                entry<Route.Logs> { LogsScreen() }
                                                entry<Route.Support> { SupportScreen() }
                                                entry<Route.License> { LicenseScreen() }
                                                entry<Route.Donate> { DonateScreen() }
                                                entry<Route.Addresses> { AddressesScreen() }
                                                entry<Route.PreferredTunnel> { key ->
                                                    PreferredTunnelScreen(key.tunnelNetwork)
                                                }
                                                entry<Route.PingTarget> { PingTargetScreen() }
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        networkMonitor.checkPermissionsAndUpdateState()
        WireGuardAutoTunnel.setUiActive(true)
    }

    override fun onPause() {
        super.onPause()
        WireGuardAutoTunnel.setUiActive(false)
    }

    fun performBackup() =
        lifecycleScope.launch {
            // reset active tuns before backup to prevent trying to start them without permission on
            // restore
            tunnelRepository.resetActiveTunnels()
            roomBackup
                .database(appDatabase)
                .backupLocation(RoomBackup.BACKUP_FILE_LOCATION_CUSTOM_DIALOG)
                .enableLogDebug(true)
                .maxFileCount(5)
                .apply {
                    onCompleteListener { success, _, _ ->
                        lifecycleScope.launch {
                            if (success) {
                                showToast(
                                    getString(
                                        R.string.backup_success,
                                        getString(R.string.restarting_app),
                                    )
                                )
                                restartApp()
                            } else {
                                showToast(R.string.backup_failed)
                            }
                        }
                    }
                }
                .backup()
        }

    fun performRestore() =
        lifecycleScope.launch {
            roomBackup
                .database(appDatabase)
                .enableLogDebug(true)
                .backupLocation(RoomBackup.BACKUP_FILE_LOCATION_CUSTOM_DIALOG)
                .apply {
                    onCompleteListener { success, _, _ ->
                        lifecycleScope.launch {
                            if (success) {
                                showToast(
                                    getString(
                                        R.string.restore_success,
                                        getString(R.string.restarting_app),
                                    )
                                )
                                restartApp()
                            } else {
                                showToast(R.string.restore_failed)
                            }
                        }
                    }
                }
                .restore()
        }
}
