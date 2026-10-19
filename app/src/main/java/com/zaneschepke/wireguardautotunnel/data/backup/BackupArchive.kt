package com.zaneschepke.wireguardautotunnel.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.BadPaddingException

internal data class BackupManifest(
    val platform: String,
    val schemaVersion: Int,
    val appVersionName: String,
    val encrypted: Boolean,
) {
    companion object
}

internal sealed interface DecodedBackup {
    data class Ok(val database: ByteArray, val manifest: BackupManifest?) : DecodedBackup

    data object Invalid : DecodedBackup

    data object WrongPassword : DecodedBackup

    data object NewerVersion : DecodedBackup

    data object WrongPlatform : DecodedBackup
}

/**
 * A new backup is a zip of `manifest.json` and the SQLite file. Password protection encrypts only
 * the database entry, using [BackupCipher], so a file saved by the old library still restores.
 */
internal object BackupArchive {
    const val PLATFORM_ANDROID = "android"
    private const val MANIFEST = "manifest.json"
    private const val DATABASE = "database.sqlite3"
    private const val DATABASE_AES = "database.sqlite3.aes"
    private const val MAX_ENTRY_BYTES = 64 * 1024 * 1024
    private val SQLITE_MAGIC = "SQLite format 3\u0000".encodeToByteArray()

    fun write(
        out: OutputStream,
        database: ByteArray,
        manifest: BackupManifest,
        password: String?,
    ) {
        val secret = password?.takeIf { it.isNotBlank() }
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.copy(encrypted = secret != null).toJson().encodeToByteArray())
            zip.closeEntry()
            val entry = if (secret == null) DATABASE else DATABASE_AES
            val payload = if (secret == null) database else BackupCipher.encrypt(database, secret)
            zip.putNextEntry(ZipEntry(entry))
            zip.write(payload)
            zip.closeEntry()
        }
    }

    fun read(
        bytes: ByteArray,
        displayName: String?,
        password: String?,
        appSchemaVersion: Int,
    ): DecodedBackup {
        val extension = extension(displayName) ?: return DecodedBackup.Invalid
        val secret = password?.takeIf { it.isNotBlank() }
        val payload =
            when (extension) {
                "aes" -> {
                    if (secret == null) return DecodedBackup.Invalid
                    decrypt(bytes, secret) ?: return DecodedBackup.WrongPassword
                }
                "sqlite3" -> {
                    if (secret != null) return DecodedBackup.Invalid
                    bytes
                }
                "zip" -> bytes
                else -> return DecodedBackup.Invalid
            }
        val unpacked =
            if (extension == "zip") readZip(payload, secret) else Unpacked(payload, manifest = null)
        val database = unpacked.database ?: return unpacked.failure ?: DecodedBackup.Invalid
        val manifest = unpacked.manifest
        if (manifest != null && manifest.platform != PLATFORM_ANDROID) {
            return DecodedBackup.WrongPlatform
        }
        val userVersion = sqliteUserVersion(database) ?: return DecodedBackup.Invalid
        if (manifest != null && manifest.schemaVersion != userVersion) return DecodedBackup.Invalid
        if (userVersion > appSchemaVersion || (manifest?.schemaVersion ?: 0) > appSchemaVersion) {
            return DecodedBackup.NewerVersion
        }
        return DecodedBackup.Ok(database, manifest)
    }

    fun sqliteUserVersion(bytes: ByteArray): Int? {
        if (
            bytes.size < 64 || !bytes.copyOfRange(0, SQLITE_MAGIC.size).contentEquals(SQLITE_MAGIC)
        ) {
            return null
        }
        return ByteBuffer.wrap(bytes).getInt(60)
    }

    private fun readZip(bytes: ByteArray, password: String?): Unpacked {
        var manifest: BackupManifest? = null
        var database: ByteArray? = null
        var databaseName: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name != MANIFEST && name != DATABASE && name != DATABASE_AES) {
                    return Unpacked(failure = DecodedBackup.Invalid)
                }
                val data = readEntry(zip) ?: return Unpacked(failure = DecodedBackup.Invalid)
                if (name == MANIFEST) {
                    manifest =
                        BackupManifest.fromJson(data.decodeToString())
                            ?: return Unpacked(failure = DecodedBackup.Invalid)
                } else {
                    if (database != null) return Unpacked(failure = DecodedBackup.Invalid)
                    database = data
                    databaseName = name
                }
            }
        }
        val manifestValue = manifest ?: return Unpacked(failure = DecodedBackup.Invalid)
        val payload = database ?: return Unpacked(failure = DecodedBackup.Invalid)
        val encryptedEntry = databaseName == DATABASE_AES
        if (manifestValue.encrypted != encryptedEntry)
            return Unpacked(failure = DecodedBackup.Invalid)
        if (!encryptedEntry) {
            if (password != null) return Unpacked(failure = DecodedBackup.Invalid)
            return Unpacked(payload, manifestValue)
        }
        if (password == null) return Unpacked(failure = DecodedBackup.Invalid)
        val clear =
            decrypt(payload, password) ?: return Unpacked(failure = DecodedBackup.WrongPassword)
        return Unpacked(clear, manifestValue)
    }

    private fun decrypt(data: ByteArray, password: String): ByteArray? =
        try {
            BackupCipher.decrypt(data, password)
        } catch (_: BadPaddingException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun readEntry(zip: ZipInputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = zip.read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_ENTRY_BYTES) return null
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }

    private fun extension(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase(Locale.US)
    }

    private data class Unpacked(
        val database: ByteArray? = null,
        val manifest: BackupManifest? = null,
        val failure: DecodedBackup? = null,
    )
}

private fun BackupManifest.toJson(): String = buildString {
    append("{\"platform\":")
    append(jsonString(platform))
    append(",\"schemaVersion\":")
    append(schemaVersion)
    append(",\"appVersionName\":")
    append(jsonString(appVersionName))
    append(",\"encrypted\":")
    append(encrypted)
    append('}')
}

private fun BackupManifest.Companion.fromJson(text: String): BackupManifest? {
    val body = text.trim()
    if (!body.startsWith("{") || !body.endsWith("}")) return null
    val platform = stringField(body, "platform") ?: return null
    val schema = rawField(body, "schemaVersion")?.toIntOrNull() ?: return null
    val appVersion = stringField(body, "appVersionName") ?: return null
    val encrypted = rawField(body, "encrypted")?.toBooleanStrictOrNull() ?: return null
    return BackupManifest(platform, schema, appVersion, encrypted)
}

private fun stringField(json: String, key: String): String? {
    val raw = rawField(json, key) ?: return null
    if (raw.length < 2 || raw.first() != '"' || raw.last() != '"') return null
    return unescape(raw.substring(1, raw.lastIndex))
}

private fun rawField(json: String, key: String): String? {
    val needle = "\"$key\":"
    val start = json.indexOf(needle)
    if (start < 0) return null
    val from = start + needle.length
    val end = json.indexOfAny(charArrayOf(',', '}'), from)
    if (end < 0) return null
    return json.substring(from, end)
}

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            else -> append(char)
        }
    }
    append('"')
}

private fun unescape(value: String): String =
    value.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
