package com.zaneschepke.tunnel

import android.content.Context
import com.getkeepsafe.relinker.ReLinker
import timber.log.Timber

internal object TunnelLibraryInitializer {
    fun ensureLoaded(context: Context) {
        ReLinker.loadLibrary(context, "am-go")
        Timber.d("Native library loaded")
    }
}
