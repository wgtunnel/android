package com.zaneschepke.wireguardautotunnel.di

import android.content.Context
import android.os.PowerManager
import android.os.StrictMode
import com.zaneschepke.logcatter.LogReader
import com.zaneschepke.logcatter.LogcatReader
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.core.shortcut.DynamicShortcutManager
import com.zaneschepke.wireguardautotunnel.core.shortcut.ShortcutManager
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.SelectedTunnelsRepository
import com.zaneschepke.wireguardautotunnel.notification.AndroidNotificationService
import com.zaneschepke.wireguardautotunnel.notification.NotificationService
import com.zaneschepke.wireguardautotunnel.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.service.autotunnel.AutoTunnelStateHolder
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import com.zaneschepke.wireguardautotunnel.util.network.NetworkUtils
import com.zaneschepke.wireguardautotunnel.viewmodel.AutoTunnelViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.ConfigEditViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.DnsViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.LicenseViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.LockdownViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.LoggerViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.MonitoringViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.ProxySettingsViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SettingsViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SplitTunnelViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SupportViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.TunnelViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

@OptIn(KoinExperimentalAPI::class)
val appModule = module {
    single<CoroutineScope>(named(Scope.APPLICATION)) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single<LogReader> {
        if (BuildConfig.DEBUG) {
            val readPolicy = StrictMode.allowThreadDiskReads()
            val writePolicy = StrictMode.allowThreadDiskWrites()
            try {
                val storageDir = androidContext().filesDir.absolutePath
                LogcatReader.init(storageDir = storageDir)
            } finally {
                StrictMode.setThreadPolicy(readPolicy)
                StrictMode.setThreadPolicy(writePolicy)
            }
        } else {
            val storageDir = androidContext().filesDir.absolutePath
            LogcatReader.init(storageDir = storageDir)
        }
    }

    single<PowerManager> {
        androidContext().getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    singleOf(::AndroidNotificationService) bind NotificationService::class
    single { ServiceManager(androidContext()) }

    singleOf(::GlobalEffectRepository)

    single { FileUtils(androidContext(), get(named(Dispatcher.IO))) }
    single<ShortcutManager> { DynamicShortcutManager(androidContext(), get(named(Dispatcher.IO))) }
    singleOf(::SelectedTunnelsRepository)

    single { NetworkUtils(get(named(Dispatcher.IO))) }

    viewModelOf(::AutoTunnelViewModel)
    viewModel { (id: Int?) -> ConfigEditViewModel(get(), get(), get(), get(), get(), id) }
    viewModelOf(::DnsViewModel)
    viewModelOf(::LicenseViewModel)
    viewModelOf(::LockdownViewModel)
    viewModelOf(::LoggerViewModel)
    viewModelOf(::MonitoringViewModel)
    viewModelOf(::ProxySettingsViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::SharedAppViewModel)
    viewModel { (id: Int) -> SplitTunnelViewModel(get(), get(), get(), id) }
    viewModel { SupportViewModel(get(), get(named(Dispatcher.MAIN)), get()) }
    viewModel { (id: Int) -> TunnelViewModel(get(), get(), id) }

    singleOf(::AutoTunnelStateHolder)
}
