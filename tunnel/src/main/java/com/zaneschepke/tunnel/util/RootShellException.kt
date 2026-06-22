package com.zaneschepke.tunnel.util

sealed class RootShellException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    class NoRootAccess :
        RootShellException("Root access is not granted. Please grant root permissions.")

    class CommandFailed(val command: String, val exitCode: Int, val stderr: String? = null) :
        RootShellException(
            buildString {
                append("Root command failed")
                append(" (exit code: $exitCode)")
                append(": $command")

                if (!stderr.isNullOrBlank()) {
                    append("\n$stderr")
                }
            }
        )

    class CommandTimedOut(val command: String, val timeoutMs: Long) :
        RootShellException("Root command timed out after ${timeoutMs}ms: $command")

    class ShellDied(cause: Throwable? = null) :
        RootShellException("Root shell terminated unexpectedly", cause)
}
