package com.zaneschepke.logcatter

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import java.io.File

object LogcatReader {
    private const val MAX_FILE_SIZE = 2097152L // 2MB
    private const val MAX_FOLDER_SIZE = 10485760L // 10MB

    private lateinit var logcatManager: LogcatManager
    private var isInitialized = false

    fun init(
        maxFileSize: Long = MAX_FILE_SIZE,
        maxFolderSize: Long = MAX_FOLDER_SIZE,
        storageDir: String,
    ): LogReader {
        if (maxFileSize > maxFolderSize) {
            throw IllegalStateException("maxFileSize must be less than maxFolderSize")
        }
        synchronized(this) {
            if (isInitialized) return logcatManager
            val logDir = "$storageDir${File.separator}logs"
            File(logDir).mkdirs()
            logcatManager =
                LogcatManager(
                    pid = android.os.Process.myPid(),
                    logDir = logDir,
                    maxFileSize = maxFileSize,
                    maxFolderSize = maxFolderSize,
                )
            if (Looper.myLooper() == Looper.getMainLooper()) {
                ProcessLifecycleOwner.get().lifecycle.addObserver(logcatManager)
            } else {
                Handler(Looper.getMainLooper()).post {
                    ProcessLifecycleOwner.get().lifecycle.addObserver(logcatManager)
                }
            }
            isInitialized = true
            return logcatManager
        }
    }
}
