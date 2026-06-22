package com.zaneschepke.networkmonitor.shizuku

import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import timber.log.Timber

class ShizukuShell(private val applicationScope: CoroutineScope) {
    interface CommandResultListener {
        /*
         * Runs after the command executes, at least partially. Does not run with 'done' if the command throws an error.
         * output: The output of the command
         * done: If the command has finished executing
         */
        fun onCommandResult(output: String, done: Boolean) {}

        /*
         * Runs if the command throws an error.
         * error: The error message
         */
        fun onCommandError(error: String) {}
    }

    fun command(command: String, listener: CommandResultListener, lineBundle: Int = 50) {
        applicationScope.launch(Dispatchers.IO) {
            var process: IRemoteProcess? = null
            var inputStreamPfd: ParcelFileDescriptor? = null
            var errorStreamPfd: ParcelFileDescriptor? = null

            try {
                process =
                    IShizukuService.Stub.asInterface(Shizuku.getBinder())
                        .newProcess(arrayOf("sh", "-c", command), null, null)
                inputStreamPfd = process.inputStream
                errorStreamPfd = process.errorStream

                FileInputStream(inputStreamPfd.fileDescriptor).use { inputStream ->
                    FileInputStream(errorStreamPfd.fileDescriptor).use { errorStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            BufferedReader(InputStreamReader(errorStream)).use { err ->
                                val output = StringBuilder()
                                val errorData = StringBuilder()
                                var line: String?
                                var lineCount = 0

                                // Read output first
                                while (reader.readLine().also { line = it } != null) {
                                    lineCount++
                                    output.append(line).append("\n")
                                    if (lineCount == lineBundle) {
                                        lineCount = 0
                                        listener.onCommandResult(
                                            output.toString().trim().replace(Regex("[\n\r]"), ""),
                                            false,
                                        )
                                        output.clear()
                                    }
                                }
                                // Send any remaining buffered output
                                if (output.isNotBlank()) {
                                    listener.onCommandResult(
                                        output.toString().trim().replace(Regex("[\n\r]"), ""),
                                        false,
                                    )
                                }

                                // Read error stream
                                while (err.readLine().also { line = it } != null) {
                                    errorData.append(line).append("\n")
                                }

                                if (errorData.isNotBlank()) {
                                    listener.onCommandError(
                                        errorData.toString().trim().replace(Regex("[\n\r]"), "")
                                    )
                                } else {
                                    listener.onCommandResult(
                                        output.toString().trim().replace(Regex("[\n\r]"), ""),
                                        true,
                                    )
                                }

                                // Wait for the process to complete
                                process.waitFor()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "ShizukuShell command failed for command: $command")
                listener.onCommandError(e.message ?: "Unknown Shizuku command error")
            } finally {
                inputStreamPfd?.close()
                errorStreamPfd?.close()
                process?.destroy()
            }
        }
    }

    suspend fun singleResponseCommand(command: String) =
        suspendCancellableCoroutine { continuation ->
            command(
                command,
                object : CommandResultListener {
                    override fun onCommandResult(output: String, done: Boolean) {
                        if (done) continuation.resumeWith(Result.success(output))
                    }

                    override fun onCommandError(error: String) {
                        continuation.resumeWithException(Exception(error))
                    }
                },
            )
        }
}
