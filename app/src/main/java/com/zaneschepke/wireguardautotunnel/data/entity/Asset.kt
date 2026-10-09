package com.zaneschepke.wireguardautotunnel.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Asset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L,
)
