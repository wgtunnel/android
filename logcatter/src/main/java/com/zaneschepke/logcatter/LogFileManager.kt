package com.zaneschepke.logcatter

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogFileManager(
    private val logDir: String,
    private val maxFileSize: Long,
    private val maxFolderSize: Long,
    private val appVersion: String,
    private val flavor: String,
) {
    private var currentFile: File? = null
    private var outputStream: FileOutputStream? = null

    val ioDispatcher = Dispatchers.IO

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    suspend fun writeLog(line: String) =
        withContext(ioDispatcher) {
            ensureCurrentFile()
            rotateIfNeeded()
            outputStream?.write((line + System.lineSeparator()).toByteArray())
            outputStream?.flush()
        }

    suspend fun zipLogs(zipFilePath: String) =
        withContext(ioDispatcher) {
            closeCurrentFile()
            val sourceDir = File(logDir)
            if (!sourceDir.exists() || !sourceDir.isDirectory) return@withContext

            val outputZipFile = File(zipFilePath)
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
                sourceDir.walkTopDown().forEach { file ->
                    val relativePath =
                        file.absolutePath.removePrefix(sourceDir.absolutePath).removePrefix("/")
                    val entry = ZipEntry("$relativePath${if (file.isDirectory) "/" else ""}")
                    zos.putNextEntry(entry)
                    if (file.isFile) {
                        file.inputStream().use { it.copyTo(zos) }
                    }
                }
            }
        }

    suspend fun deleteAllLogs() =
        withContext(ioDispatcher) {
            closeCurrentFile()
            File(logDir).listFiles()?.forEach { it.delete() }
            currentFile = null
        }

    fun close() {
        closeCurrentFile()
    }

    private fun closeCurrentFile() {
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        outputStream = null
        currentFile = null
    }

    private suspend fun ensureCurrentFile() =
        withContext(ioDispatcher) {
            if (currentFile != null && outputStream != null) return@withContext

            val dir = File(logDir).apply { mkdirs() }

            // Reuse latest file if it is still small enough
            val latest =
                dir.listFiles { f -> f.isFile && f.name.startsWith("logcat_") }
                    ?.maxByOrNull { it.lastModified() }

            if (latest != null && latest.length() < maxFileSize) {
                currentFile = latest
                outputStream = FileOutputStream(currentFile!!, true)
            } else {
                createNewLogFile(dir)
            }
        }

    private fun createNewLogFile(dir: File) {
        val timestamp = LocalDateTime.now().format(dateFormatter)
        val fileName = "logcat_${timestamp}_v${appVersion}_$flavor.txt"

        currentFile = File(dir, fileName)
        outputStream = FileOutputStream(currentFile!!)

        writeHeader()
    }

    private fun writeHeader() {
        val header = buildString {
            appendLine("=== WG Tunnel Logcat ===")
            appendLine("Version : $appVersion ($flavor)")
            appendLine("Started : ${LocalDateTime.now()}")
            appendLine("=========================")
            appendLine()
        }
        outputStream?.write(header.toByteArray())
        outputStream?.flush()
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return size
    }

    private fun deleteOldestFile() {
        File(logDir).listFiles { f -> f.isFile }?.minByOrNull { it.lastModified() }?.delete()
    }

    private fun rotateIfNeeded() {
        val folderSize = getFolderSize(File(logDir))
        if (folderSize >= maxFolderSize) {
            deleteOldestFile()
        }

        val size = currentFile?.length() ?: 0L
        if (size >= maxFileSize) {
            closeCurrentFile()
            createNewLogFile(File(logDir))
        }
    }
}
