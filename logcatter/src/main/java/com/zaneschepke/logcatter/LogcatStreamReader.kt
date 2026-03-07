package com.zaneschepke.logcatter

import com.zaneschepke.logcatter.model.LogMessage
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class LogcatStreamReader(pid: Int, private val fileManager: LogFileManager) {
    private val bufferSize = 1024
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private val command = "logcat -v epoch --pid=$pid"
    private val clearCommand = "logcat -c"

    private val ioDispatcher = Dispatchers.IO

    fun readLogs(): Flow<LogMessage> =
        flow {
                try {
                    clearLogs()
                    process = Runtime.getRuntime().exec(command)
                    reader = BufferedReader(InputStreamReader(process!!.inputStream), bufferSize)
                    reader!!.lineSequence().forEach { line ->
                        if (line.isNotEmpty()) {
                            fileManager.writeLog(line)
                            emit(LogMessage.from(line))
                        }
                    }
                } catch (_: IOException) {
                    // do nothing
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
            } catch (_: IOException) {
                // do nothing
            }
        }
    }

    fun stop() {
        process?.destroy()
        reader?.close()
        process = null
        reader = null
    }

    fun clearLogs() {
        Runtime.getRuntime().exec(clearCommand)
    }
}
