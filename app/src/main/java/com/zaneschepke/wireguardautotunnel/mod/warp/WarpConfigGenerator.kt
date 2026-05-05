package com.zaneschepke.wireguardautotunnel.mod.warp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.interfaces.XECPrivateKey
import java.util.Base64

/**
 * Генератор конфигураций WireGuard и WARP
 * Поддерживает регистрацию в сети WARP Cloudflare и генерацию ключей
 * Оптимизировано для работы в РФ с множественными попытками подключения
 */
object WarpConfigGenerator {

    private const val TAG = "WarpGenerator"
    
    // Список зеркал API для обхода блокировок
    private val API_ENDPOINTS = listOf(
        "https://api.cloudflareclient.com/v0a2158/reg",
        "https://api.cloudflareclient.com/v0a4037/reg",
        "https://api.cloudflareclient.com/v0a2209/reg"
    )

    data class WarpIdentity(
        val privateKey: String,
        val publicKey: String,
        val deviceToken: String,
        val warpId: String,
        val reserved: List<Int>,
        val clientV4: String,
        val clientV6: String
    )

    /**
     * Генерирует новую пару ключей Curve25519
     */
    fun generateKeyPair(): Pair<String, String> {
        val kpg = KeyPairGenerator.getInstance("X25519")
        kpg.initialize(256)
        val keyPair = kpg.generateKeyPair()
        
        val privateKeyBytes = (keyPair.private as XECPrivateKey).scalar
        val publicKeyBytes = (keyPair.public as XECPrivateKey).u
        
        val privateB64 = Base64.getEncoder().encodeToString(privateKeyBytes)
        val publicB64 = Base64.getEncoder().encodeToString(publicKeyBytes)
        
        return Pair(privateB64, publicB64)
    }

    /**
     * Регистрирует устройство в WARP и возвращает данные для конфига
     * Реализует множественные попытки подключения через разные эндпоинты
     */
    suspend fun generateWarpConfig(
        installId: String? = null,
        maxRetries: Int = 10
    ): Result<WarpIdentity> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        val keyPair = generateKeyPair()
        val publicKey = keyPair.second
        val privateKey = keyPair.first

        for (attempt in 1..maxRetries) {
            try {
                Log.d(TAG, "Попытка регистрации WARP: $attempt/$maxRetries")
                
                // Выбираем эндпоинт циклически
                val apiUrl = API_ENDPOINTS[(attempt - 1) % API_ENDPOINTS.size]
                
                val jsonPayload = JSONObject().apply {
                    put("key", publicKey)
                    put("install_id", installId ?: generateRandomInstallId())
                    put("fcm_token", "")
                    put("tos", "2024-01-01T00:00:00.000Z") // Accept ToS
                    put("type", "Android")
                    put("model", "SM-G998B") // Популярная модель для маскировки
                    put("locale", "ru_RU")
                    put("warp_enabled", true)
                }

                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("User-Agent", "okhttp/3.12.1")
                    connection.setRequestProperty("CF-Client-Version", "a-6.10-2158")
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.doOutput = true

                    connection.outputStream.write(jsonPayload.toString().toByteArray())

                    val responseCode = connection.responseCode
                    if (responseCode == 200 || responseCode == 201) {
                        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                        val responseJson = JSONObject(responseBody)
                        
                        if (!responseJson.has("account")) {
                            throw Exception("Invalid response structure")
                        }
                        
                        val accountData = responseJson.getJSONObject("account")
                        val configData = accountData.getJSONObject("config")
                        val peers = configData.getJSONArray("peers").getJSONObject(0)
                        val interfaceData = configData.getJSONObject("interface")
                        
                        val warpId = accountData.getString("id")
                        val deviceToken = accountData.getString("token")
                        val publicKeyServer = peers.getString("public_key")
                        
                        // Получаем IPv4 и IPv6
                        val addresses = interfaceData.getJSONArray("addresses")
                        val v4 = addresses.getString(0).split("/")[0]
                        val v6 = addresses.getString(1).split("/")[0]

                        // Reserved bytes для обхода DPI (важно для РФ)
                        // Генерируем случайные байты для лучшей маскировки
                        val reserved = listOf(
                            (Math.random() * 256).toInt(),
                            (Math.random() * 256).toInt(),
                            (Math.random() * 256).toInt()
                        )

                        Log.d(TAG, "WARP успешно зарегистрирован: $warpId")
                        
                        return@withContext Result.success(
                            WarpIdentity(
                                privateKey = privateKey,
                                publicKey = publicKeyServer,
                                deviceToken = deviceToken,
                                warpId = warpId,
                                reserved = reserved,
                                clientV4 = v4,
                                clientV6 = v6
                            )
                        )
                    } else {
                        val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                        Log.e(TAG, "Ошибка API ($responseCode): $errorBody")
                        lastException = Exception("API Error: $responseCode - ${errorBody.take(100)}")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка сети (попытка $attempt): ${e.message}")
                lastException = e
                // Экспоненциальная задержка между попытками
                val delayMs = 1000L * attempt
                kotlinx.coroutines.delay(delayMs)
            }
        }

        Result.failure(lastException ?: Exception("Неизвестная ошибка генерации WARP"))
    }

    /**
     * Генерирует случайный Install ID для маскировки устройства
     */
    private fun generateRandomInstallId(): String {
        val chars = "0123456789abcdef"
        return buildString {
            repeat(16) {
                append(chars[(Math.random() * chars.length).toInt()])
            }
        }
    }

    /**
     * Формирует готовый текст конфига для WireGuard
     * Включает все необходимые настройки для работы в РФ
     */
    fun createWireGuardConfig(
        identity: WarpIdentity,
        endpoint: String = "engage.cloudflareclient.com:2408",
        dnsList: List<String> = listOf("1.1.1.1", "1.0.0.1"),
        mtu: Int = 1280,
        keepAlive: Int = 5,
        reserved: List<Int>? = null
    ): String {
        val allowedIps = "0.0.0.0/0,::/0"
        val dnsString = dnsList.joinToString(",")
        val reservedStr = reserved?.joinToString(",") ?: identity.reserved.joinToString(",")

        return """[Interface]
PrivateKey = ${identity.privateKey}
Address = ${identity.clientV4}/32, ${identity.clientV6}/128
DNS = $dnsString
MTU = $mtu

[Peer]
PublicKey = ${identity.publicKey}
Endpoint = $endpoint
AllowedIPs = $allowedIps
PersistentKeepalive = $keepAlive
# Reserved bytes for DPI bypass (RF optimization)
# Reserved = $reservedStr
""".trimIndent()
    }
    
    /**
     * Создает конфиг с расширенными настройками для продвинутых клиентов
     */
    fun createAdvancedConfig(
        identity: WarpIdentity,
        endpoint: String = "engage.cloudflareclient.com:2408",
        dnsList: List<String> = listOf("1.1.1.1", "1.0.0.1"),
        mtu: Int = 1280,
        keepAlive: Int = 5,
        enableLogging: Boolean = false,
        table: String = "auto"
    ): String {
        val allowedIps = "0.0.0.0/0,::/0"
        val dnsString = dnsList.joinToString(",")
        val reservedStr = identity.reserved.joinToString(",")

        return """[Interface]
PrivateKey = ${identity.privateKey}
Address = ${identity.clientV4}/32, ${identity.clientV6}/128
DNS = $dnsString
MTU = $mtu
Table = $table
${if (enableLogging) "# PostUp = echo 'WARP connected' > /dev/kmsg" else ""}

[Peer]
PublicKey = ${identity.publicKey}
Endpoint = $endpoint
AllowedIPs = $allowedIps
PersistentKeepalive = $keepAlive
# Optimized for Russia region
# Reserved = $reservedStr
""".trimIndent()
    }
}
