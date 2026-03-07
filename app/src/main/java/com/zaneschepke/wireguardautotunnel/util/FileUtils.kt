package com.zaneschepke.wireguardautotunnel.util

import LicenseFileEntry
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import com.zaneschepke.wireguardautotunnel.util.extensions.QuickConfig
import com.zaneschepke.wireguardautotunnel.util.extensions.TunnelName
import com.zaneschepke.wireguardautotunnel.util.extensions.getInputStreamFromUri
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

class FileUtils(private val context: Context, private val ioDispatcher: CoroutineDispatcher) {

    /**
     * Creates a configuration file for a given tunnel if the config string is valid.
     *
     * @param fileName The name of the file.
     * @param data THe string data for the file.
     * @return The created File if successful and non-empty, else null.
     * @throws IOException If file creation or writing fails (e.g., no space or permissions).
     */
    suspend fun createFile(fileName: String, data: String): File? =
        withContext(ioDispatcher) {
            val file = File(context.cacheDir, "${fileName}.conf")
            file.outputStream().use { it.write(data.toByteArray()) }
            Timber.d("Created file: ${file.path}, size: ${file.length()} bytes")

            if (file.length() == 0L) {
                Timber.w("Config file ${file.path} is empty")
                return@withContext null
            }
            file
        }

    /**
     * Zips a list of files into a single ZIP file.
     *
     * @param zipFile The target ZIP file to create or overwrite.
     * @param files The list of files to include in the ZIP.
     * @return Result.success(Unit) on success, or Result.failure with the exception on error.
     */
    suspend fun zipAll(zipFile: File, files: List<File>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                    if (zipFile.exists() && !zipFile.delete()) {
                        throw IOException("Failed to delete existing ZIP file: ${zipFile.path}")
                    }
                    if (!zipFile.createNewFile()) {
                        throw IOException("Failed to create ZIP file: ${zipFile.path}")
                    }

                    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                        files.forEach { file ->
                            if (!file.exists() || file.length() == 0L) {
                                Timber.w("Skipping file ${file.path}: does not exist or empty")
                                return@forEach
                            }
                            val entryName = file.name
                            val entry = ZipEntry(entryName)
                            zos.putNextEntry(entry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                                Timber.d(
                                    "Added ${file.path} to zip as $entryName, size: ${file.length()} bytes"
                                )
                            }
                            zos.closeEntry()
                        }
                        zos.flush()
                        Timber.d(
                            "Finished zipping: ${zipFile.path}, size: ${zipFile.length()} bytes"
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Error zipping files into ${zipFile.path}")
                    if (zipFile.exists()) zipFile.delete()
                }
        }

    suspend fun createNewShareFile(name: String): Result<File> =
        withContext(ioDispatcher) {
            runCatching {
                    val cacheDir =
                        context.cacheDir ?: throw IOException("Cache directory unavailable")
                    val sharePath = File(cacheDir, "external_files")
                    if (!sharePath.exists() && !sharePath.mkdirs()) {
                        throw IOException("Failed to create share directory: ${sharePath.path}")
                    }
                    val file = File(sharePath, name)
                    if (file.exists() && !file.delete()) {
                        throw IOException("Failed to delete existing file: ${file.path}")
                    }
                    if (!file.createNewFile()) {
                        throw IOException("Failed to create new file: ${file.path}")
                    }
                    Timber.d("Created share file: ${file.path}")
                    file
                }
                .onFailure { error -> Timber.e(error, "Error creating share file") }
        }

    suspend fun exportFile(file: File, uri: Uri?, mimeType: String): Result<Uri> {
        return if (uri != null) {
            copyFileToUri(file, uri).map { uri }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    saveToDownloadsWithMediaStore(file, mimeType)
                        ?: throw IOException("Failed to insert into MediaStore")
                }
            } else {
                Result.failure(
                    IOException(
                        "Auto-export to Downloads not supported on this device (pre-Android 10). Use a file picker (SAF) to provide a URI."
                    )
                )
            }
        }
    }

    suspend fun copyFileToUri(sourceFile: File, destinationUri: Uri): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                if (!sourceFile.exists()) {
                    Timber.e("Source file does not exist: ${sourceFile.path}")
                    return@withContext Result.failure(IOException("Source file does not exist"))
                }
                if (!sourceFile.canRead()) {
                    Timber.e("Source file is not readable: ${sourceFile.path}")
                    return@withContext Result.failure(IOException("Source file is not readable"))
                }
                if (sourceFile.length() == 0L) {
                    Timber.e("Source file is empty: ${sourceFile.path}")
                    return@withContext Result.failure(IOException("Source file is empty"))
                }

                Timber.d("Copying file: ${sourceFile.path}, size: ${sourceFile.length()} bytes")
                var bytesCopied = 0L
                FileInputStream(sourceFile).use { inputStream ->
                    context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        val buffer = ByteArray(1024 * 1024) // 1MB buffer
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            bytesCopied += bytesRead
                            Timber.d("Copied $bytesCopied bytes so far")
                        }
                        outputStream.flush()
                        Timber.d("Total bytes copied: $bytesCopied")
                        Result.success(Unit)
                    }
                        ?: run {
                            Timber.e("Failed to open OutputStream for Uri: $destinationUri")
                            Result.failure(IOException("Failed to open OutputStream for Uri"))
                        }
                }
            } catch (e: IOException) {
                Timber.e(e, "Error copying file to Uri: ${e.message}")
                Result.failure(e)
            }
        }

    private fun getDisplayNameColumnIndex(cursor: Cursor): Int? {
        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (columnIndex == -1) return null
        return columnIndex
    }

    private fun getDisplayNameByCursor(cursor: Cursor): String? {
        val move = cursor.moveToFirst()
        if (!move) return null
        val index = getDisplayNameColumnIndex(cursor)
        if (index == null) return index
        return cursor.getString(index)
    }

    private fun getFileName(uri: Uri): String {
        return getFileNameByCursor(uri) ?: NumberUtils.generateRandomTunnelName()
    }

    private fun getNameFromFileName(fileName: String): String {
        return fileName.take(fileName.lastIndexOf('.'))
    }

    private fun getFileExtensionFromFileName(fileName: String): String? {
        return try {
            fileName.substring(fileName.lastIndexOf('.'))
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }

    private fun getFileNameByCursor(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use {
            getDisplayNameByCursor(it)
        }
    }

    /**
     * Reads configuration data from a URI pointing to a .conf file or .zip archive containing .conf
     * files.
     *
     * @param uri The URI to the file or archive.
     * @return Result.success with a Map where keys are config names (without .conf extension) and
     *   values are the raw string contents, or Result.failure with an exception (e.g.,
     *   FileNotFoundException, IOException).
     */
    suspend fun readConfigsFromUri(uri: Uri): Result<Map<QuickConfig, TunnelName>> =
        withContext(ioDispatcher) {
            runCatching {
                    val fileName = getFileName(uri).lowercase()
                    val extension = getFileExtensionFromFileName(fileName)
                    val inputStream =
                        context.getInputStreamFromUri(uri)
                            ?: throw IOException("Failed to open input stream for URI: $uri")

                    when (extension?.lowercase()) {
                        CONF_FILE_EXTENSION.lowercase() -> {
                            inputStream.use { stream ->
                                val content = stream.bufferedReader().readText()
                                val name = getNameFromFileName(fileName)
                                mapOf(content to name)
                            }
                        }
                        ZIP_FILE_EXTENSION.lowercase() -> {
                            ZipInputStream(inputStream).use { zip ->
                                val configs = mutableMapOf<String, String>()
                                var entry: ZipEntry? = zip.nextEntry
                                while (entry != null) {
                                    if (
                                        !entry.isDirectory &&
                                            getFileExtensionFromFileName(entry.name.lowercase()) ==
                                                CONF_FILE_EXTENSION.lowercase()
                                    ) {
                                        val content = zip.bufferedReader().readText()
                                        val name = getNameFromFileName(entry.name)
                                        configs[content] = name
                                    }
                                    zip.closeEntry()
                                    entry = zip.nextEntry
                                }
                                configs
                            }
                        }
                        else ->
                            throw FileNotFoundException("Unsupported file extension: $extension")
                    }
                }
                .onFailure { error -> Timber.e(error, "Error reading configs from URI: $uri") }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun saveToDownloadsWithMediaStore(file: File, mimeType: String): Uri? =
        withContext(ioDispatcher) {
            val contentValues =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, contentValues) ?: return@withContext null

            resolver.openOutputStream(itemUri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            // Mark as finished
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)

            return@withContext itemUri
        }

    suspend fun readLibraryLicensesFromAssets(): List<LicenseFileEntry> =
        withContext(ioDispatcher) {
            val json = Json { ignoreUnknownKeys = true }

            val jsonResult =
                context.assets.open("licenses.json").bufferedReader().use { it.readText() }
            json.decodeFromString(jsonResult)
        }

    companion object {
        const val CONF_FILE_EXTENSION = ".conf"
        const val ZIP_FILE_EXTENSION = ".zip"
        private const val TEXT_MIME_TYPE = "text/plain"
        const val ZIP_FILE_MIME_TYPE = "application/zip"
        const val ALL_FILE_TYPES = "*/*"

        const val ALLOWED_TV_FILE_TYPES = "${TEXT_MIME_TYPE}|${ZIP_FILE_MIME_TYPE}"

        const val URI_CONTENT_SCHEME = "content"
        const val GOOGLE_TV_EXPLORER_STUB = "com.google.android.tv.frameworkpackagestubs"
        const val ANDROID_TV_EXPLORER_STUB = "com.android.tv.frameworkpackagestubs"
    }
}
