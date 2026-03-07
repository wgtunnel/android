package com.zaneschepke.wireguardautotunnel.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dispatchersModule = module {
    single<CoroutineDispatcher>(named(Dispatcher.DEFAULT)) { Dispatchers.Default }
    single<CoroutineDispatcher>(named(Dispatcher.IO)) { Dispatchers.IO }
    single<CoroutineDispatcher>(named(Dispatcher.MAIN)) { Dispatchers.Main }
    single<CoroutineDispatcher>(named(Dispatcher.MAIN_IMMEDIATE)) { Dispatchers.Main.immediate }
}
