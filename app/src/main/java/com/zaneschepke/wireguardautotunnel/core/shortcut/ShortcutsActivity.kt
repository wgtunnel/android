package com.zaneschepke.wireguardautotunnel.core.shortcut

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.zaneschepke.wireguardautotunnel.core.orchestration.ShortcutCoordinator
import com.zaneschepke.wireguardautotunnel.di.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named

class ShortcutsActivity : ComponentActivity() {

    private val shortcutCoordinator: ShortcutCoordinator by inject()

    private val applicationScope: CoroutineScope by inject(named(Scope.APPLICATION))

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        finish()

        applicationScope.launch { shortcutCoordinator.handle(intent) }
    }
}
