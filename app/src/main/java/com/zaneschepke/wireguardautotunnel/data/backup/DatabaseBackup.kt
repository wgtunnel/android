package com.zaneschepke.wireguardautotunnel.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.AutoTunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.data.AppDatabase
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

sealed interface BackupWriteResult {
    data object Success : BackupWriteResult

    data class SavedToDownloads(val fileName: String) : BackupWriteResult

    data object Failed : BackupWriteResult
}

sealed interface RestoreResult {
    data object Success : RestoreResult

    data object WrongPassword : RestoreResult

    data object InvalidFile : RestoreResult

    data object NewerVersion : RestoreResult

    data object Failed : RestoreResult
}

class DatabaseBackup(
    private val context: Context,
    private val database: AppDatabase,
    private val tunnelCoordinator: TunnelCoordinator,
    private val autoTunnelCoordinator: AutoTunnelCoordinator,
    private val tunnelProvider: TunnelProvider,
    private val fileUtils: FileUtils,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun newBackupFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
        return "wgtunnel-backup-$stamp.zip"
    }

    suspend fun backupTo(uri: Uri?, password: String?): BackupWriteResult =
        withContext(ioDispatcher) {
            val snapshot = runCatching {
                snapshotDatabase()
            }
                .getOrElse {
                    Timber.e(it, "Backup snapshot failed")
                    return@withContext BackupWriteResult.Failed
                }
            try {
                val bytes = snapshot.readBytes()
                val version =
                    BackupArchive.sqliteUserVersion(bytes)
                        ?: return@withContext BackupWriteResult.Failed
                val manifest =
                    BackupManifest(
                        platform = BackupArchive.PLATFORM_ANDROID,
                        schemaVersion = version,
                        appVersionName = BuildConfig.VERSION_NAME,
                        encrypted = !password.isNullOrBlank(),
                    )
                if (uri != null) {
                    val stream =
                        context.contentResolver.openOutputStream(uri)
                            ?: return@withContext BackupWriteResult.Failed
                    stream.use { BackupArchive.write(it, bytes, manifest, password) }
                    BackupWriteResult.Success
                } else {
                    val name = newBackupFileName()
                    val file = File(context.cacheDir, name)
                    file.outputStream().use { BackupArchive.write(it, bytes, manifest, password) }
                    val exported =
                        fileUtils.exportFile(file, uri = null, FileUtils.ZIP_FILE_MIME_TYPE)
                    file.delete()
                    if (exported.isSuccess) BackupWriteResult.SavedToDownloads(name)
                    else BackupWriteResult.Failed
                }
            } catch (error: Exception) {
                Timber.e(error, "Backup write failed")
                BackupWriteResult.Failed
            } finally {
                snapshot.delete()
                File(snapshot.path + "-wal").delete()
                File(snapshot.path + "-shm").delete()
            }
        }

    suspend fun restore(uri: Uri, password: String?): RestoreResult =
        withContext(ioDispatcher) {
            val bytes = readLimited(uri) ?: return@withContext RestoreResult.InvalidFile
            val appSchema = database.openHelper.readableDatabase.version
            when (val decoded = BackupArchive.read(bytes, displayName(uri), password, appSchema)) {
                is DecodedBackup.Ok ->
                    runCatching { stopVpnAndReplace(decoded.database) }
                        .fold(
                            onSuccess = { RestoreResult.Success },
                            onFailure = {
                                Timber.e(it, "Restore failed")
                                RestoreResult.Failed
                            },
                        )
                DecodedBackup.WrongPassword -> RestoreResult.WrongPassword
                DecodedBackup.NewerVersion,
                DecodedBackup.WrongPlatform -> RestoreResult.NewerVersion
                DecodedBackup.Invalid -> RestoreResult.InvalidFile
            }
        }

    /**
     * Copies the open database without closing it. A transaction on the live connection blocks
     * writers while the files are copied. `wal_checkpoint` inside that transaction is a no-op, so
     * the copy is checkpointed on its own connection.
     */
    private fun snapshotDatabase(): File {
        val dbFile = context.getDatabasePath(context.getString(R.string.db_name))
        val sqlite = database.openHelper.writableDatabase
        val snapshot = File.createTempFile("wgtunnel-backup-", ".sqlite3", context.cacheDir)
        runCatching { sqlite.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() } }
        sqlite.beginTransaction()
        try {
            dbFile.copyTo(snapshot, overwrite = true)
            copySidecar(dbFile, snapshot, "-wal")
            copySidecar(dbFile, snapshot, "-shm")
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
        foldWal(snapshot)
        return snapshot
    }

    private suspend fun stopVpnAndReplace(databaseBytes: ByteArray) {
        val dbFile = context.getDatabasePath(context.getString(R.string.db_name))
        val staged = File(dbFile.parentFile, "${dbFile.name}.restore")
        staged.writeBytes(databaseBytes)
        try {
            autoTunnelCoordinator.disable()
            tunnelProvider.disableLockDown().getOrThrow()
            tunnelCoordinator.stopActiveTunnels()
            database.close()
            if (!dbFile.delete() || !staged.renameTo(dbFile)) {
                staged.copyTo(dbFile, overwrite = true)
                staged.delete()
            }
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                File(dbFile.path + suffix).delete()
            }
        } catch (error: Throwable) {
            if (!dbFile.exists() && staged.exists()) staged.copyTo(dbFile, overwrite = true)
            staged.delete()
            throw error
        }
    }

    private fun displayName(uri: Uri): String? {
        val cursor =
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            ) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) return null
            return it.getString(index)
        }
    }

    private fun readLimited(uri: Uri): ByteArray? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(chunk)
                if (read < 0) break
                total += read
                if (total > MAX_BACKUP_BYTES) return null
                out.write(chunk, 0, read)
            }
            out.toByteArray()
        }
    }

    private fun foldWal(snapshot: File) {
        val wal = File(snapshot.path + "-wal")
        val shm = File(snapshot.path + "-shm")
        if (!wal.exists() || wal.length() == 0L) {
            wal.delete()
            shm.delete()
            return
        }
        SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            .use { copy ->
                copy.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
            }
        wal.delete()
        shm.delete()
    }

    private fun copySidecar(dbFile: File, snapshot: File, suffix: String) {
        val source = File(dbFile.path + suffix)
        if (source.exists()) source.copyTo(File(snapshot.path + suffix), overwrite = true)
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    }
}
