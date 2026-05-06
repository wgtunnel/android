package com.zaneschepke.wireguardautotunnel.util

import android.content.Context
import android.net.Uri
import com.zaneschepke.wireguardautotunnel.domain.model.*
import com.zaneschepke.wireguardautotunnel.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

data class BackupOptions(
    val backupTunnels: Boolean = true,
    val backupSettings: Boolean = true,
    val backupProxySettings: Boolean = true,
    val backupMonitoringSettings: Boolean = true,
    val backupDnsSettings: Boolean = true,
    val backupAutoTunnelSettings: Boolean = true,
    val backupLockdownSettings: Boolean = true
)

class BackupManager(
    private val context: Context,
    private val tunnelsRepository: TunnelRepository,
    private val settingsRepository: GeneralSettingRepository,
    private val proxySettingsRepository: ProxySettingsRepository,
    private val monitoringSettingsRepository: MonitoringSettingsRepository,
    private val dnsSettingsRepository: DnsSettingsRepository,
    private val autoTunnelSettingsRepository: AutoTunnelSettingsRepository,
    private val lockdownSettingsRepository: LockdownSettingsRepository
) {

    suspend fun createBackup(uri: Uri, options: BackupOptions): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val outputStream = context.contentResolver.openOutputStream(uri) ?: return@withContext Result.failure(IOException("Cannot open output stream"))
                val zipOutput = java.util.zip.ZipOutputStream(BufferedOutputStream(outputStream))

                // Сохраняем выбранные данные
                if (options.backupTunnels) {
                    val tunnels = tunnelsRepository.getAll()
                    if (tunnels.isNotEmpty()) {
                        val json = Gson().toJson(tunnels)
                        zipOutput.putNextEntry(java.util.zip.ZipEntry("tunnels.json"))
                        zipOutput.write(json.toByteArray())
                        zipOutput.closeEntry()
                    }
                }

                if (options.backupSettings) {
                    val settings = settingsRepository.getGeneralSettings()
                    val json = Gson().toJson(settings)
                    zipOutput.putNextEntry(java.util.zip.ZipEntry("settings.json"))
                    zipOutput.write(json.toByteArray())
                    zipOutput.closeEntry()
                }

                if (options.backupProxySettings) {
                    val proxySettings = proxySettingsRepository.getProxySettings()
                    val json = Gson().toJson(proxySettings)
                    zipOutput.putNextEntry(java.util.zip.ZipEntry("proxy_settings.json"))
                    zipOutput.write(json.toByteArray())
                    zipOutput.closeEntry()
                }

                // Аналогично для других настроек...

                zipOutput.finish()
                zipOutput.close()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun restoreBackup(uri: Uri, options: BackupOptions): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext Result.failure(IOException("Cannot open input stream"))
                val zipInput = java.util.zip.ZipInputStream(BufferedInputStream(inputStream))
                var entry = zipInput.nextEntry

                while (entry != null) {
                    when (entry.name) {
                        "tunnels.json" -> {
                            if (options.backupTunnels) {
                                val json = zipInput.bufferedReader().readText()
                                val tunnels = Gson().fromJson(json, Array<TunnelConfig>::class.java).toList()
                                tunnels.forEach { tunnel ->
                                    tunnelsRepository.upsert(tunnel)
                                }
                            }
                        }
                        "settings.json" -> {
                            if (options.backupSettings) {
                                val json = zipInput.bufferedReader().readText()
                                val settings = Gson().fromJson(json, GeneralSettings::class.java)
                                settingsRepository.upsert(settings)
                            }
                        }
                        // Аналогично для других файлов...
                    }
                    entry = zipInput.nextEntry
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
