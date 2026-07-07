package com.zaneschepke.wireguardautotunnel.util.extensions

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText

suspend fun HttpResponse.isHtmlResponse(): Boolean {
    val contentType = headers["Content-Type"] ?: ""
    if (contentType.contains("text/html", ignoreCase = true)) return true

    val bodyStart = bodyAsText().trimStart()
    return bodyStart.startsWith("<!DOCTYPE", ignoreCase = true) ||
        bodyStart.startsWith("<html", ignoreCase = true)
}
