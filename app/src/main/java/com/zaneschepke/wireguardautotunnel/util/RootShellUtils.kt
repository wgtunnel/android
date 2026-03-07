package com.zaneschepke.wireguardautotunnel.util

import com.wireguard.android.util.RootShell
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class RootShellUtils(
    private val rootShell: RootShell,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun requestRoot(): Boolean =
        withContext(ioDispatcher) {
            val accepted =
                try {
                    rootShell.start()
                    true
                } catch (_: Exception) {
                    false
                }
            accepted
        }
}
