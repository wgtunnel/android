package com.zaneschepke.logcatter

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.zaneschepke.logcatter.model.LogMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LogcatManager(pid: Int, logDir: String, maxFileSize: Long, maxFolderSize: Long) :
    LogReader, DefaultLifecycleObserver {
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val fileManager = LogFileManager(logDir, maxFileSize, maxFolderSize)
    private val logcatReader = LogcatStreamReader(pid, fileManager)
    private var logJob: Job? = null
    private var isStarted = false

    private val mutex = Mutex()

    private val _bufferedLogs =
        MutableSharedFlow<LogMessage>(
            replay = 10_000,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val _liveLogs =
        MutableSharedFlow<LogMessage>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val bufferedLogs: Flow<LogMessage> = _bufferedLogs.asSharedFlow()
    override val liveLogs: Flow<LogMessage> = _liveLogs.asSharedFlow()

    override fun onCreate(owner: LifecycleOwner) {
        // for auto start
        // logScope.launch { start() }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        logScope.launch { stop() }
        logScope.cancel()
    }

    override suspend fun start() {
        mutex.withLock {
            if (isStarted) return
            stopInternal()
            logJob =
                logScope.launch {
                    logcatReader.readLogs().collect { logMessage ->
                        _bufferedLogs.emit(logMessage)
                        _liveLogs.emit(logMessage)
                    }
                }
            isStarted = true
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun stop() {
        mutex.withLock {
            if (!isStarted) return
            stopInternal()
            isStarted = false
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun stopInternal() {
        logJob?.cancel()
        logcatReader.stop()
        fileManager.close()
        _bufferedLogs.resetReplayCache()
        logJob = null
    }

    override suspend fun zipLogFiles(path: String) {
        val wasStarted = mutex.withLock { isStarted }
        stop()
        fileManager.zipLogs(path)
        if (wasStarted) {
            logcatReader.clearLogs()
            start()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun deleteAndClearLogs() {
        val wasStarted = mutex.withLock { isStarted }
        stop()
        _bufferedLogs.resetReplayCache()
        fileManager.deleteAllLogs()
        if (wasStarted) start()
    }
}
