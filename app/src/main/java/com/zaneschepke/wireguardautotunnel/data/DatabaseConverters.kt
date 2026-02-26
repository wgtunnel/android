package com.zaneschepke.wireguardautotunnel.data

import androidx.room.TypeConverter
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.data.model.DnsProtocol
import com.zaneschepke.wireguardautotunnel.data.model.MaxAttemptsAction
import com.zaneschepke.wireguardautotunnel.data.model.WifiDetectionMethod
import kotlinx.serialization.json.Json

class DatabaseConverters {
    @TypeConverter
    fun listToString(value: List<String>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun stringToList(value: String): List<String> {
        if (value.isBlank() || value.isEmpty()) return mutableListOf()
        return try {
            Json.decodeFromString<List<String>>(value)
        } catch (e: Exception) {
            val list = value.split(",").toMutableList()
            val json = listToString(list)
            Json.decodeFromString<List<String>>(json)
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

    @TypeConverter
    fun setToString(value: Set<String>): String {
        return listToString(value.toList())
    }

    @TypeConverter
    fun stringToSet(value: String): Set<String> {
        return stringToList(value).toSet()
    }

    @TypeConverter fun fromStatus(status: WifiDetectionMethod): Int = status.value

    @TypeConverter
    fun toStatus(value: Int): WifiDetectionMethod = WifiDetectionMethod.fromValue(value)

    @TypeConverter fun toMode(value: Int): AppMode = AppMode.fromValue(value)

    @TypeConverter fun fromMode(mode: AppMode): Int = mode.value

    @TypeConverter fun toDnsProtocol(value: Int): DnsProtocol = DnsProtocol.fromValue(value)

    @TypeConverter fun fromDnsProtocol(mode: DnsProtocol): Int = mode.value

    @TypeConverter fun toMaxAttemptsAction(value: Int): MaxAttemptsAction = MaxAttemptsAction.fromValue(value)

    @TypeConverter fun fromMaxAttemptsAction(action: MaxAttemptsAction): Int = action.value
}
