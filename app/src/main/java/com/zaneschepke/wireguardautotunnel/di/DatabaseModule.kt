package com.zaneschepke.wireguardautotunnel.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.data.AppDatabase
import com.zaneschepke.wireguardautotunnel.data.DataStoreManager
import com.zaneschepke.wireguardautotunnel.data.DatabaseCallback
import com.zaneschepke.wireguardautotunnel.data.migrations.MIGRATION_23_24
import com.zaneschepke.wireguardautotunnel.data.migrations.MIGRATION_25_26
import com.zaneschepke.wireguardautotunnel.data.migrations.MIGRATION_28_29
import com.zaneschepke.wireguardautotunnel.data.repository.DataStoreAppStateRepository
import com.zaneschepke.wireguardautotunnel.data.repository.InstalledAndroidPackageRepository
import com.zaneschepke.wireguardautotunnel.data.repository.RoomAutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.data.repository.RoomDnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.data.repository.RoomLockdownSettingsRepository
import com.zaneschepke.wireguardautotunnel.data.repository.RoomMonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.data.repository.RoomProxySettingsRepository
import com.zaneschepke.wireguardautotunnel.data.repository.RoomSettingsRepository
import com.zaneschepke.wireguardautotunnel.data.repository.RoomTunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.AppStateRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.DnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.InstalledPackageRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.LockdownSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.ProxySettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val databaseModule = module {
    single<RoomDatabase.Callback> { DatabaseCallback(lazy { get() }) }

    single {
        Room.databaseBuilder(
                androidContext(),
                AppDatabase::class.java,
                get<Context>().getString(R.string.db_name),
            )
            .addMigrations(
                MIGRATION_23_24(get<DataStoreManager>().dataStore),
                MIGRATION_25_26,
                MIGRATION_28_29,
            )
            .fallbackToDestructiveMigration(true)
            .addCallback(get())
            .build()
    }

    single { get<AppDatabase>().generalSettingsDao() }
    single { get<AppDatabase>().lockdownSettingsDao() }
    single { get<AppDatabase>().dnsSettingsDao() }
    single { get<AppDatabase>().autoTunnelSettingsDao() }
    single { get<AppDatabase>().monitoringSettingsDao() }
    single { get<AppDatabase>().proxySettingsDoa() }
    single { get<AppDatabase>().tunnelConfigDoa() }

    single { DataStoreManager(androidContext(), get(named(Dispatcher.IO))) }

    single<AppStateRepository> {
        DataStoreAppStateRepository(get(), get(named(Scope.APPLICATION)), get(named(Dispatcher.IO)))
    }
    singleOf(::RoomAutoTunnelSettingsRepository) bind AutoTunnelSettingsRepository::class
    singleOf(::RoomDnsSettingsRepository) bind DnsSettingsRepository::class
    singleOf(::RoomLockdownSettingsRepository) bind LockdownSettingsRepository::class
    singleOf(::RoomMonitoringSettingsRepository) bind MonitoringSettingsRepository::class
    singleOf(::RoomProxySettingsRepository) bind ProxySettingsRepository::class
    singleOf(::RoomSettingsRepository) bind GeneralSettingRepository::class
    singleOf(::RoomTunnelRepository) bind TunnelRepository::class
    single<InstalledPackageRepository> {
        InstalledAndroidPackageRepository(androidContext(), get(named(Dispatcher.IO)))
    }
}
