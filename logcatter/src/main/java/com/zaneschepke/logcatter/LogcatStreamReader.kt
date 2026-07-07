package com.zaneschepke.logcatter

import com.zaneschepke.logcatter.model.LogMessage
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber

class LogcatStreamReader(pid: Int, private val fileManager: LogFileManager) {
    private val bufferSize = 1024
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private val command = buildString {
        append("logcat")
        append(" -v epoch")
        append(" --pid=$pid")
        append(" -b main -b crash")
        append(" -T 300")
    }

    private val clearCommand = "logcat -c"

    private val ioDispatcher = Dispatchers.IO

    private val noisyTags =
        setOf(
            "SamsungIME",
            "SPen",
            "SmartManager",
            "InputMethod",
            "SurfaceFlinger",
            "WindowManager",
            "ActivityManager",
            "SystemServer",
            "PackageManager",
            "ConnectivityService",
        )

    private val noisyPatterns =
        listOf(
            Regex(".*(Samsung|SPen|SmartView| Knox|MDM).*", RegexOption.IGNORE_CASE),
            Regex(".*(Choreographer|HWUI|OpenGL|RenderThread).*"),
            Regex(".*setRequestedFrameRate.*", RegexOption.IGNORE_CASE),
            Regex(
                ".*(qdgralloc|AdrenoVK|BLASTBufferQueue|SurfaceComposerClient|BufferQueueProducer|VRI\\[).*",
                RegexOption.IGNORE_CASE,
            ),
        )

    fun shouldLog(line: String): Boolean {
        if (noisyTags.any { line.contains(it) }) return false
        if (noisyPatterns.any { it.containsMatchIn(line) }) return false
        return true
    }

    fun readLogs(): Flow<LogMessage> =
        flow {
                try {
                    process = Runtime.getRuntime().exec(command)
                    reader = BufferedReader(InputStreamReader(process!!.inputStream), bufferSize)

                    reader!!.lineSequence().forEach { line ->
                        if (line.isNotEmpty() && shouldLog(line)) {
                            fileManager.writeLog(line)
                            emit(LogMessage.from(line))
                        }
                    }
                } catch (_: InterruptedIOException) {
                    Timber.d("Logcat reader has been shut down")
                } catch (e: IOException) {
                    Timber.w(
                        e,
                        "Logcat read failed (process may have been killed or permission issue)",
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Unexpected error in logcat reader")
                } finally {
                    stop()
                }
            }
            .flowOn(ioDispatcher)

    fun start() {
        if (process == null) {
            try {
                process = Runtime.getRuntime().exec(command)
                reader = BufferedReader(InputStreamReader(process!!.inputStream), bufferSize)
            } catch (e: IOException) {
                Timber.w(e, "Failed to start logcat reader")
            }
        }
    }

    fun stop() {
        try {
            process?.destroy()
            reader?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error while stopping logcat process")
        } finally {
            process = null
            reader = null
        }
    }

    fun clearLogs() {
        try {
            Runtime.getRuntime().exec(clearCommand).waitFor()
        } catch (e: Exception) {
            Timber.w(e, "Failed to clear logcat buffers")
        }
    }
}
