package com.zaneschepke.wireguardautotunnel.data

import androidx.room.TypeConverter
import com.zaneschepke.wireguardautotunnel.domain.enums.DnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.enums.WifiDetectionMethod
import kotlinx.serialization.json.Json

class DatabaseConverters {
    @TypeConverter
    fun listToString(value: List<String>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun stringToList(value: String): List<String> {
        if (value.isBlank() || value == "[]") return emptyList()

        return try {
            Json.decodeFromString<List<String>>(value)
        } catch (e: Exception) {
            value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    @TypeConverter
    fun mapToString(map: Map<String, String>): String {
        return Json.encodeToString(map)
    }

    @TypeConverter
    fun stringToMap(json: String): Map<String, String> {
        return if (json.isEmpty() || json == "{}") {
            emptyMap()
        } else {
            try {
                Json.decodeFromString<Map<String, String>>(json)
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    @TypeConverter fun fromStatus(status: WifiDetectionMethod): Int = status.value

    @TypeConverter
    fun toStatus(value: Int): WifiDetectionMethod = WifiDetectionMethod.fromValue(value)

    @TypeConverter fun toMode(value: Int): TunnelMode = TunnelMode.fromValue(value)

    @TypeConverter fun fromMode(mode: TunnelMode): Int = mode.value

    @TypeConverter fun toDnsProtocol(value: Int): DnsProtocol = DnsProtocol.fromValue(value)

    @TypeConverter fun fromDnsProtocol(mode: DnsProtocol): Int = mode.value
}
