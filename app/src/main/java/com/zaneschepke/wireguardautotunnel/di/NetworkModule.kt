package com.zaneschepke.wireguardautotunnel.di

import android.os.StrictMode
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.data.network.GitHubApi
import com.zaneschepke.wireguardautotunnel.data.network.KtorClient
import com.zaneschepke.wireguardautotunnel.data.network.KtorGitHubApi
import com.zaneschepke.wireguardautotunnel.data.network.StreamingFileDownloader
import com.zaneschepke.wireguardautotunnel.data.repository.DownloadManagerUpdateDownloader
import com.zaneschepke.wireguardautotunnel.data.repository.GitHubUpdateRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.UpdateDownloader
import com.zaneschepke.wireguardautotunnel.domain.repository.UpdateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.lazyModule

val networkModule = lazyModule {
    single {
        val client =
            if (BuildConfig.DEBUG) {
                val oldPolicy = StrictMode.allowThreadDiskReads()
                try {
                    KtorClient.create()
                } finally {
                    StrictMode.setThreadPolicy(oldPolicy)
                }
            } else {
                KtorClient.create()
            }
        client
    }
    singleOf(::KtorGitHubApi) bind GitHubApi::class

    single<UpdateRepository> {
        GitHubUpdateRepository(get(), "wgtunnel", "android", get(named(Dispatcher.IO)))
    }

    single<UpdateDownloader> {
        DownloadManagerUpdateDownloader(
            androidContext(),
            get(named(Scope.APPLICATION)),
            get(named(Dispatcher.IO)),
            get(),
            get(),
            get(),
            StreamingFileDownloader(get()),
        )
    }
}
