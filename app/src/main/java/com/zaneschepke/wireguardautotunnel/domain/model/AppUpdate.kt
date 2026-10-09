package com.zaneschepke.wireguardautotunnel.domain.model

data class AppUpdate(
    val version: String,
    val releaseUrl: String?,
    val apkUrl: String?,
    val apkFileName: String?,
    val apkSize: Long? = null,
)
