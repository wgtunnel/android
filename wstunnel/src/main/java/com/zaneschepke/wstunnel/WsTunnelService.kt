package com.zaneschepke.wstunnel

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Manages the lifecycle of the bundled `wstunnel` client binary as a subprocess.
 *
 * The binary is shipped as `libwstunnel.so` under `jniLibs/<abi>/` purely so Android's package
 * installer places it in the app's `nativeLibraryDir` - the one location on the filesystem an app
 * is still permitted to execute from on Android 10+ (W^X enforcement blocks executing from any
 * app-writable directory such as filesDir/cacheDir). This is a plain native executable, not a JNI
 * library - we never call System.loadLibrary on it, we just exec it directly.
 *
 * IMPORTANT: for this trick to work, the app module must set
 * `android.packaging.jniLibs.useLegacyPackaging = true`, otherwise AGP may not extract native libs
 * to disk at all on newer Android versions (they can be mmap'd straight out of the APK instead).
 */
object WsTunnelService {

    private const val TAG = "WsTunnelService"
    private const val BINARY_NAME = "libwstunnel.so"

    @Volatile private var process: Process? = null

    val isRunning: Boolean
        get() = process?.isAlive == true

    /**
     * wstunnel only ships a prebuilt Android binary for arm64-v8a (as of v10.6.1) - there's no
     * armeabi-v7a/x86/x86_64 upstream release. The app layer should use this to hide/disable the
     * WSTunnel option on unsupported devices rather than let [start] fail.
     */
    fun isSupported(context: Context): Boolean = binaryFile(context).exists()

    private fun binaryFile(context: Context) = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    /**
     * Starts the wstunnel client, listening on `127.0.0.1:[WsTunnelConfig.localPort]` and relaying
     * UDP datagrams to [WsTunnelConfig.serverUrl], which forwards to
     * [WsTunnelConfig.remoteHost]:[WsTunnelConfig.remotePort].
     *
     * Safe to call again after [stop]; throws if a bridge is already running.
     */
    @Throws(IllegalStateException::class)
    fun start(context: Context, config: WsTunnelConfig) {
        check(!isRunning) { "WSTunnel bridge is already running" }

        val binaryPath = binaryFile(context)
        check(binaryPath.exists()) {
            "wstunnel binary not found at $binaryPath - this device's ABI (${Build.SUPPORTED_ABIS.joinToString()}) " +
                "is likely unsupported, since upstream only publishes an arm64-v8a Android build. " +
                "Check WsTunnelService.isSupported() before offering this feature in the UI."
        }

        val localForward =
            buildString {
                append("udp://127.0.0.1:${config.localPort}:${config.remoteHost}:${config.remotePort}")
                append("?timeout_sec=${config.udpTimeoutSec}")
            }

        val args =
            buildList {
                add(binaryPath.absolutePath)
                add("client")
                add("-L")
                add(localForward)
                config.httpUpgradePathPrefix?.let {
                    add("--http-upgrade-path-prefix")
                    add(it)
                }
                config.tlsSni?.let {
                    add("--tls-sni-override")
                    add(it)
                }
                add(config.serverUrl)
            }

        Log.d(TAG, "Starting wstunnel bridge on 127.0.0.1:${config.localPort}")

        val builder =
            ProcessBuilder(args)
                .redirectErrorStream(true)
                .directory(context.filesDir)

        val started = builder.start()
        process = started

        // Drain stdout/stderr so the process doesn't block once its pipe buffer fills, and
        // surface anything interesting to logcat for debugging.
        Thread({ pipeOutputToLogcat(started) }, "wstunnel-log-pump").apply { isDaemon = true }.start()
    }

    /** Stops the wstunnel bridge if running. Safe to call when not running. */
    fun stop() {
        val current = process ?: return
        process = null
        Log.d(TAG, "Stopping wstunnel bridge")
        current.destroy()
        // give it a moment to exit cleanly, then force it
        Thread {
                if (!current.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    current.destroyForcibly()
                }
            }
            .apply { isDaemon = true }
            .start()
    }

    private fun pipeOutputToLogcat(process: Process) {
        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.forEachLine { line -> Log.d(TAG, line) }
            }
        } catch (_: Exception) {
            // process was killed out from under us, nothing to do
        }
    }
}
