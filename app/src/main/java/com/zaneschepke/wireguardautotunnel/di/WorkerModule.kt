package com.zaneschepke.wireguardautotunnel.di

import com.zaneschepke.wireguardautotunnel.core.worker.ServiceWorker
import com.zaneschepke.wireguardautotunnel.core.worker.UpdateCheckWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val workerModule = module {
    workerOf(::ServiceWorker)
    workerOf(::UpdateCheckWorker)
}
