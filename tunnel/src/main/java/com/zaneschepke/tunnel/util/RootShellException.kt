package com.zaneschepke.tunnel.util

sealed class RootShellException(override val message: String) : Exception(message) {

    class NoRootAccess : RootShellException("Root access is not granted. Please grant root permissions.")

    class ShellStartFailed(
        exitCode: Int? = null,
        cause: Throwable? = null
    ) : RootShellException(
        message = "Failed to start root shell${exitCode?.let { " (exit code: $it)" } ?: "${cause?.message}"}",
    )

    class DirectoryCreationFailed(
        val directory: String
    ) : RootShellException("Failed to create directory: $directory")
}