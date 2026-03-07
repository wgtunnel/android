package com.zaneschepke.wireguardautotunnel.di

import com.zaneschepke.wireguardautotunnel.data.network.GitHubApi
import com.zaneschepke.wireguardautotunnel.data.network.KtorClient
import com.zaneschepke.wireguardautotunnel.data.network.KtorGitHubApi
import com.zaneschepke.wireguardautotunnel.data.repository.GitHubUpdateRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.UpdateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.lazyModule

val networkModule = lazyModule {
    single { KtorClient.create() }
    singleOf(::KtorGitHubApi) bind GitHubApi::class

    single<UpdateRepository> {
        val appName = "wgtunnel"
        GitHubUpdateRepository(
            get(),
            get(),
            appName,
            appName,
            androidContext(),
            get(named(Dispatcher.IO)),
        )
    }
}
