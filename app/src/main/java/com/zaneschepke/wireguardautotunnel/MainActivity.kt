package com.zaneschepke.wireguardautotunnel

import ProxySettingsScreen
import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dokar.sonner.TextToastAction
import com.dokar.sonner.ToastType
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.wireguardautotunnel.data.AppDatabase
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.AppStateRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.banner.AppAlertBanner
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.LocalNetworkPermissionDialog
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.VpnDeniedDialog
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.navigation.SecureRoute
import com.zaneschepke.wireguardautotunnel.ui.navigation.Tab
import com.zaneschepke.wireguardautotunnel.ui.navigation.components.BottomNavbar
import com.zaneschepke.wireguardautotunnel.ui.navigation.components.DynamicTopAppBar
import com.zaneschepke.wireguardautotunnel.ui.navigation.components.currentRouteAsNavbarState
import com.zaneschepke.wireguardautotunnel.ui.navigation.functions.rememberNavController
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.AutoTunnelScreen
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
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.globals.TunnelGlobalsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.integrations.AndroidIntegrationsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.lockdown.LockdownSettingsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.logs.LogsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring.MonitoringScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.security.SecurityScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.support.SupportScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.support.donate.DonateScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.support.donate.crypto.AddressesScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.support.license.LicenseScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.TunnelsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.TunnelSettingsScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.ConfigScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.ConfigEditScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.ipv6.IPv6Screen
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.sort.SortScreen
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.SplitTunnelScreen
import com.zaneschepke.wireguardautotunnel.ui.theme.AlertRed
import com.zaneschepke.wireguardautotunnel.ui.theme.Heart
import com.zaneschepke.wireguardautotunnel.ui.theme.OffWhite
import com.zaneschepke.wireguardautotunnel.ui.theme.SilverTree
import com.zaneschepke.wireguardautotunnel.ui.theme.Straw
import com.zaneschepke.wireguardautotunnel.ui.theme.WireguardAutoTunnelTheme
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import com.zaneschepke.wireguardautotunnel.util.LocaleUtil
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.installApk
import com.zaneschepke.wireguardautotunnel.util.extensions.isRunningOnTv
import com.zaneschepke.wireguardautotunnel.util.extensions.openWebUrl
import com.zaneschepke.wireguardautotunnel.util.extensions.restartApp
import com.zaneschepke.wireguardautotunnel.util.permission.LocalNetworkPermissionHelper
import com.zaneschepke.wireguardautotunnel.viewmodel.ConfigEditViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SplitTunnelViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.TunnelViewModel
import de.raphaelebner.roomdatabasebackup.core.OnCompleteListener.Companion.EXIT_CODE_ERROR
import de.raphaelebner.roomdatabasebackup.core.OnCompleteListener.Companion.EXIT_CODE_ERROR_DECRYPTION_ERROR
import de.raphaelebner.roomdatabasebackup.core.OnCompleteListener.Companion.EXIT_CODE_ERROR_RESTORE_BACKUP_IS_ENCRYPTED
import de.raphaelebner.roomdatabasebackup.core.OnCompleteListener.Companion.EXIT_CODE_ERROR_WRONG_DECRYPTION_PASSWORD
import de.raphaelebner.roomdatabasebackup.core.RoomBackup
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import timber.log.Timber
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

        roomBackup = RoomBackup(this).database(appDatabase).enableLogDebug(true).maxFileCount(5)

        handleConfigFileIntent(intent)
        handleWgDeepLinkIntent(intent)

        installSplashScreen().apply {
            setKeepOnScreenCondition { !viewModel.container.stateFlow.value.isAppLoaded }
        }

        setContent {
            val context = LocalContext.current
            val isTv = isRunningOnTv()
            val uiState by viewModel.collectAsState()
            val scope = rememberCoroutineScope()

            LaunchedEffect(uiState.isAppLoaded) {
                if (uiState.isAppLoaded) {
                    uiState.locale.let { LocaleUtil.changeLocale(it) }
                }
            }

            val toaster = rememberToasterState()
            var showVpnPermissionDialog by remember { mutableStateOf(false) }
            var vpnPermissionDenied by remember { mutableStateOf(false) }
            var requestingTunnelMode by remember {
                mutableStateOf<Pair<TunnelMode?, TunnelConfig?>>(Pair(null, null))
            }
            var showLocalNetworkRationale by remember { mutableStateOf(false) }
            var hasPromptedLocalNetwork by rememberSaveable { mutableStateOf(false) }

            val localNetworkPermissionLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (!isGranted) {
                        val canAskAgain =
                            ActivityCompat.shouldShowRequestPermissionRationale(
                                this,
                                Manifest.permission.ACCESS_LOCAL_NETWORK,
                            )

                        if (!canAskAgain) {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                            startActivity(intent)
                        } else {
                            toaster.show(
                                message =
                                    context.getString(R.string.local_network_permission_denied),
                                type = ToastType.Warning,
                                duration = 6000.milliseconds,
                            )
                        }
                    }
                }

            LaunchedEffect(uiState.isAppLoaded) {
                if (
                    uiState.isAppLoaded &&
                        !hasPromptedLocalNetwork &&
                        LocalNetworkPermissionHelper.shouldRequestPermission() &&
                        !LocalNetworkPermissionHelper.isPermissionGranted(context)
                ) {
                    hasPromptedLocalNetwork = true
                    showLocalNetworkRationale = true
                }
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
                            val (appMode, config) = requestingTunnelMode
                            when (appMode) {
                                TunnelMode.VPN -> if (config != null) viewModel.startTunnel(config)
                                TunnelMode.LOCK_DOWN -> viewModel.setAppMode(TunnelMode.LOCK_DOWN)
                                else -> Unit
                            }
                        }
                        requestingTunnelMode = Pair(null, null)
                    },
                )

            LaunchedEffect(Unit) {
                viewModel.globalSideEffect.collectLatest { sideEffect ->
                    when (sideEffect) {
                        GlobalSideEffect.ConfigChanged -> restartApp()
                        GlobalSideEffect.PopBackStack -> navController.pop()
                        is GlobalSideEffect.RequestVpnPermission -> {
                            requestingTunnelMode =
                                Pair(sideEffect.requestingMode, sideEffect.config)
                            vpnActivity.launch(VpnService.prepare(this@MainActivity))
                        }

                        is GlobalSideEffect.Snackbar -> {
                            when (sideEffect.type) {
                                ToastType.Warning,
                                ToastType.Error -> toaster.dismissAll()
                                else -> Unit
                            }

                            toaster.show(
                                message = sideEffect.message.asString(context),
                                type = sideEffect.type,
                                duration = (sideEffect.durationMs ?: 4000L).milliseconds,
                            )
                        }

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

                    if (showLocalNetworkRationale) {
                        LocalNetworkPermissionDialog(
                            onDismiss = {
                                showLocalNetworkRationale = false
                                toaster.show(
                                    message =
                                        context.getString(R.string.local_network_permission_denied),
                                    type = ToastType.Warning,
                                    duration = 6000.milliseconds,
                                )
                            },
                            onAttest = {
                                showLocalNetworkRationale = false

                                localNetworkPermissionLauncher.launch(
                                    Manifest.permission.ACCESS_LOCAL_NETWORK
                                )
                            },
                        )
                    }

                    uiState.pendingWgImportUrl?.let { url ->
                        val host = Uri.parse(url).host ?: url
                        InfoDialog(
                            onDismiss = { viewModel.dismissWgImport() },
                            onAttest = { viewModel.importFromUrl(url) },
                            title = stringResource(R.string.add_from_url),
                            body = { Text(stringResource(R.string.wg_url_confirm_message, host)) },
                            confirmText = stringResource(R.string.okay),
                        )
                    }

                    LaunchedEffect(Unit) {
                        if (uiState.shouldShowDonationSnackbar && !uiState.alreadyDonated) {
                            viewModel.setShouldShowDonationSnackbar(false)
                            toaster.show(
                                message =
                                    context.getString(R.string.donation_prompt_prefix) +
                                        " " +
                                        context.getString(R.string.donation_prompt_link) +
                                        " " +
                                        context.getString(R.string.donation_prompt_suffix),
                                type = ToastType.Normal,
                                duration = 30_000L.milliseconds,
                                action =
                                    TextToastAction(
                                        text = context.getString(R.string.donate_title),
                                        onClick = { toastId ->
                                            toaster.dismiss(toastId)
                                            navController.push(Route.Donate)
                                        },
                                    ),
                            )
                        }
                    }

                    val isPinVisible by remember { derivedStateOf { showLock } }

                    val currentRoute by remember {
                        derivedStateOf { backStack.lastOrNull() as? Route }
                    }

                    LaunchedEffect(
                        uiState.isScreenRecordingProtectionEnabled,
                        currentRoute,
                        isPinVisible,
                    ) {
                        val isSecureRoute = currentRoute is SecureRoute

                        val shouldProtect =
                            uiState.isScreenRecordingProtectionEnabled &&
                                (isSecureRoute || isPinVisible)

                        if (shouldProtect) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE,
                            )
                        } else {
                            delay(500L)
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            awaitCancellation()
                        }
                    }

                    if (showLock) {
                        PinManager.initialize(context = this@MainActivity)
                        PinLockScreen()
                    } else {

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
                            if (uiState.tunnelMode == TunnelMode.LOCK_DOWN) {
                                AppAlertBanner(
                                    stringResource(R.string.locked_down)
                                        .uppercase(Locale.current.platformLocale),
                                    OffWhite,
                                    AlertRed,
                                    modifier = Modifier.fillMaxWidth().zIndex(2f),
                                )
                            }
                            Scaffold(
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
                                                entry<Route.Config> { key ->
                                                    val viewModel: TunnelViewModel =
                                                        koinViewModel(
                                                            parameters = { parametersOf(key.id) }
                                                        )
                                                    ConfigScreen(viewModel, key.live)
                                                }
                                                entry<Route.SplitTunnel> { key ->
                                                    val viewModel: SplitTunnelViewModel =
                                                        koinViewModel(
                                                            parameters = { parametersOf(key.id) }
                                                        )
                                                    SplitTunnelScreen(viewModel)
                                                }
                                                entry<Route.ConfigEdit> { key ->
                                                    val viewModel: ConfigEditViewModel =
                                                        koinViewModel(
                                                            parameters = { parametersOf(key.id) }
                                                        )
                                                    ConfigEditScreen(viewModel)
                                                }
                                                entry<Route.LocationDisclosure> {
                                                    LocationDisclosureScreen()
                                                }
                                                entry<Route.AutoTunnel> { AutoTunnelScreen() }
                                                entry<Route.WifiPreferences> {
                                                    WifiSettingsScreen()
                                                }
                                                entry<Route.WifiDetectionMethod> {
                                                    WifiDetectionMethodScreen()
                                                }
                                                entry<Route.Settings> { SettingsScreen() }
                                                entry<Route.AndroidIntegrations> {
                                                    AndroidIntegrationsScreen()
                                                }
                                                entry<Route.Dns> { DnsSettingsScreen() }
                                                entry<Route.ConfigGlobal> { key ->
                                                    val viewModel: ConfigEditViewModel =
                                                        koinViewModel(
                                                            parameters = { parametersOf(key.id) }
                                                        )
                                                    ConfigEditScreen(viewModel)
                                                }
                                                entry<Route.SplitTunnelGlobal> { key ->
                                                    val viewModel: SplitTunnelViewModel =
                                                        koinViewModel(
                                                            parameters = { parametersOf(key.id) }
                                                        )
                                                    SplitTunnelScreen(viewModel)
                                                }
                                                entry<Route.IPv6> { key ->
                                                    val viewModel: TunnelViewModel =
                                                        koinViewModel(
                                                            parameters = { parametersOf(key.id) }
                                                        )
                                                    IPv6Screen(viewModel)
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
                                                entry<Route.TunnelGlobals> { TunnelGlobalsScreen() }
                                                entry<Route.Security> { SecurityScreen() }
                                                entry<Route.Monitoring> { MonitoringScreen() }
                                            },
                                    )
                                }
                            }
                            Toaster(
                                state = toaster,
                                alignment = Alignment.BottomCenter,
                                offset = IntOffset(0, -220),
                                richColors = true,
                                background = {
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                                            MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                                        )
                                    )
                                },
                                elevation = 1.dp,
                                shadowAmbientColor =
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                shadowSpotColor =
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                border = {
                                    BorderStroke(
                                        0.dp,
                                        androidx.compose.ui.graphics.Color.Transparent,
                                    )
                                },
                                actionSlot = { toast ->
                                    (toast.action as? TextToastAction)?.let { action ->
                                        TextButton(
                                            onClick = { action.onClick(toast) },
                                            colors =
                                                ButtonDefaults.textButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.primary
                                                ),
                                            modifier = Modifier.fillMaxWidth(.25f),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                        ) {
                                            Text(
                                                text = action.text,
                                                fontWeight = FontWeight.Medium,
                                                softWrap = true,
                                                maxLines = 5,
                                            )
                                        }
                                    }
                                },
                                iconSlot = { toast ->
                                    val (icon, color) =
                                        when (toast.type) {
                                            ToastType.Success ->
                                                Icons.Outlined.CheckCircleOutline to SilverTree
                                            ToastType.Error ->
                                                Icons.Outlined.ErrorOutline to AlertRed
                                            ToastType.Warning ->
                                                Icons.Outlined.WarningAmber to Straw
                                            ToastType.Info ->
                                                Icons.Outlined.Info to
                                                    MaterialTheme.colorScheme.onSurface
                                            ToastType.Normal ->
                                                Icons.Outlined.FavoriteBorder to Heart
                                        }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                },
                                contentColor = { MaterialTheme.colorScheme.onSurface },
                                shape = { RoundedCornerShape(16.dp) },
                                showCloseButton = true,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleWgDeepLinkIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            if (uri.scheme == "wg") {
                val httpsUrl = uri.toString().replaceFirst("wg://", "https://")
                viewModel.promptWgImport(httpsUrl)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        networkMonitor.checkPermissionsAndUpdateState()
    }

    fun performBackup(encrypt: Boolean = false, password: String? = null) {
        roomBackup
            .backupLocation(RoomBackup.BACKUP_FILE_LOCATION_CUSTOM_DIALOG)
            .apply {
                if (encrypt && !password.isNullOrBlank()) {
                    backupIsEncrypted(true)
                    customEncryptPassword(password)
                }
            }
            .onCompleteListener { success, _, _ ->
                lifecycleScope.launch {
                    val sideEffect =
                        if (success) {
                            GlobalSideEffect.Snackbar(
                                StringValue.StringResource(R.string.backup_success),
                                ToastType.Success,
                            )
                        } else {
                            GlobalSideEffect.Snackbar(
                                StringValue.StringResource(R.string.backup_failed),
                                ToastType.Error,
                            )
                        }
                    viewModel.postSideEffect(sideEffect)
                }
            }
            .backup()
    }

    fun performRestore(encrypt: Boolean = false, password: String? = null) {
        roomBackup
            .backupLocation(RoomBackup.BACKUP_FILE_LOCATION_CUSTOM_DIALOG)
            .apply {
                if (encrypt && !password.isNullOrBlank()) {
                    backupIsEncrypted(true)
                    customEncryptPassword(password)
                }
            }
            .onCompleteListener { success, message, exitCode ->
                lifecycleScope.launch {
                    if (success) {
                        viewModel.postSideEffect(
                            GlobalSideEffect.Snackbar(
                                StringValue.StringResource(R.string.restore_success),
                                ToastType.Success,
                            )
                        )
                        roomBackup.restartApp(Intent(this@MainActivity, MainActivity::class.java))
                    } else {
                        Timber.w("Restore failed, exitCode=$exitCode, message=$message")

                        val errorMessage =
                            when (exitCode) {
                                EXIT_CODE_ERROR_WRONG_DECRYPTION_PASSWORD ->
                                    getString(R.string.restore_failed_wrong_password)

                                EXIT_CODE_ERROR,
                                EXIT_CODE_ERROR_DECRYPTION_ERROR,
                                EXIT_CODE_ERROR_RESTORE_BACKUP_IS_ENCRYPTED ->
                                    getString(R.string.restore_failed_invalid_file)

                                else -> getString(R.string.restore_failed)
                            }

                        viewModel.postSideEffect(
                            GlobalSideEffect.Snackbar(
                                StringValue.DynamicString(errorMessage),
                                ToastType.Error,
                            )
                        )
                    }
                }
            }
            .restore()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleConfigFileIntent(intent)
        handleWgDeepLinkIntent(intent)
    }

    private fun handleConfigFileIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW,
            Intent.ACTION_EDIT,
            Intent.ACTION_SEND -> {
                val uri: Uri? = intent.data ?: return
                val name = uri?.lastPathSegment?.lowercase() ?: return
                if (
                    !name.endsWith(FileUtils.CONF_FILE_EXTENSION) &&
                        !name.endsWith(FileUtils.ZIP_FILE_EXTENSION)
                ) {
                    Timber.d("Ignoring non-config URI in handleIncomingIntent: $uri")
                    return
                }
                viewModel.importFromUri(uri)
            }
        }
    }
}
