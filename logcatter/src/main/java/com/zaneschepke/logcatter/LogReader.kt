package com.zaneschepke.logcatter

import com.zaneschepke.logcatter.model.LogMessage
import kotlinx.coroutines.flow.Flow

interface LogReader {
    suspend fun start()

    suspend fun stop()

    suspend fun zipLogFiles(path: String)

    suspend fun deleteAndClearLogs()

    // clears the in-memory replay cache only, log files on disk are untouched
    suspend fun clearBufferedLogs()

    val bufferedLogs: Flow<LogMessage>
}
