package com.zaneschepke.logcatter

import java.io.File

object LogcatReader {
    private const val MAX_FILE_SIZE = 2_097_152L // 2MB
    private const val MAX_FOLDER_SIZE = 10_485_760L // 10MB

    private lateinit var logcatManager: LogcatManager
    private var isInitialized = false

    fun init(
        maxFileSize: Long = MAX_FILE_SIZE,
        maxFolderSize: Long = MAX_FOLDER_SIZE,
        storageDir: String,
        appVersion: String,
        appFlavor: String,
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
                    appVersion,
                    appFlavor,
                )
            isInitialized = true
            return logcatManager
        }
    }
}
