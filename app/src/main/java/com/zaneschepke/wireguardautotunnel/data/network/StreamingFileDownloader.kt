package com.zaneschepke.wireguardautotunnel.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.IOException

class StreamingFileDownloader(
    private val httpClient: HttpClient,
    private val stallTimeoutMs: Long = STALL_TIMEOUT_MS,
) {

    suspend fun download(
        url: String,
        destination: File,
        expectedSize: Long,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit,
    ) {
        val part = File(destination.parentFile, destination.name + PART_SUFFIX)
        try {
            part.delete()
            httpClient
                .prepareGet(url) {
                    timeout {
                        // The client's request timeout covers the whole call and would cut off a
                        // slow download, only a stalled connection should fail
                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        socketTimeoutMillis = stallTimeoutMs
                    }
                }
                .execute { response ->
                    if (!response.status.isSuccess()) {
                        throw IOException("Download failed with HTTP ${response.status.value}")
                    }
                    val total = response.contentLength() ?: expectedSize.takeIf { it > 0 } ?: -1L
                    val channel = response.bodyAsChannel()
                    onProgress(0L, total)

                    var copied = 0L
                    var lastReport = System.nanoTime()
                    part.outputStream().buffered().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = channel.readAvailable(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            // Reads are small and frequent, the UI needs a few updates a second
                            val now = System.nanoTime()
                            if (now - lastReport >= REPORT_INTERVAL_NS) {
                                lastReport = now
                                onProgress(copied, total)
                            }
                        }
                    }
                    if (total > 0 && copied != total) {
                        throw IOException("Connection closed after $copied of $total bytes")
                    }
                    onProgress(copied, total)
                }

            val length = part.length()
            if (length <= 0L) throw IOException("Downloaded file is empty")
            if (expectedSize > 0 && length != expectedSize) {
                throw IOException("Downloaded $length bytes, expected $expectedSize")
            }
            destination.delete()
            if (!part.renameTo(destination)) throw IOException("Could not move the download")
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
    }

    companion object {
        const val PART_SUFFIX = ".part"
        private const val BUFFER_SIZE = 64 * 1024
        private const val REPORT_INTERVAL_NS = 200_000_000L
        private const val STALL_TIMEOUT_MS = 30_000L
    }
}
