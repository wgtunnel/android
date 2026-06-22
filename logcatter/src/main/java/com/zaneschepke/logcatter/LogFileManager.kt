package com.zaneschepke.logcatter

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogFileManager(
    private val logDir: String,
    private val maxFileSize: Long,
    private val maxFolderSize: Long,
) {
    private var currentFile: File? = null
    private var outputStream: FileOutputStream? = null

    val ioDispatcher = Dispatchers.IO

    suspend fun writeLog(line: String) =
        withContext(ioDispatcher) {
            rotateIfNeeded()
            outputStream?.write((line + System.lineSeparator()).toByteArray())
            outputStream?.flush()
        }

    suspend fun zipLogs(zipFilePath: String) =
        withContext(ioDispatcher) {
            outputStream?.close()

            val sourceDir = File(logDir)
            if (!sourceDir.exists() || !sourceDir.isDirectory) return@withContext

            val outputZipFile = File(zipFilePath)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
                sourceDir.walkTopDown().forEach { file ->
                    val zipFileName =
                        file.absolutePath.removePrefix(sourceDir.absolutePath).removePrefix("/")

                    val entry = ZipEntry("$zipFileName${if (file.isDirectory) "/" else ""}")
                    zos.putNextEntry(entry)

                    if (file.isFile) {
                        file.inputStream().use { it.copyTo(zos) }
                    }
                }
            }

            rotateIfNeeded()
        }

    suspend fun deleteAllLogs() =
        withContext(ioDispatcher) {
            outputStream?.close()
            File(logDir).listFiles()?.forEach { it.delete() }
            rotateIfNeeded()
        }

    fun close() {
        outputStream?.close()
        outputStream = null
        currentFile = null
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        if (dir.isDirectory && dir.listFiles() != null) {
            dir.listFiles()!!.forEach { file ->
                size += if (file.isDirectory) getFolderSize(file) else file.length()
            }
        }
        return size
    }

    private fun deleteOldestFile() {
        File(logDir).listFiles()?.toList()?.minByOrNull { it.lastModified() }?.delete()
    }

    private fun rotateIfNeeded() {
        val folderSize = getFolderSize(File(logDir))
        if (folderSize >= maxFolderSize) {
            deleteOldestFile()
        }

        val fileSize = currentFile?.length() ?: 0L
        if (currentFile == null || fileSize >= maxFileSize) {
            outputStream?.close()
            currentFile = File(logDir, "logcat_${System.currentTimeMillis()}.txt")
            outputStream = FileOutputStream(currentFile!!)
        }
    }
}
