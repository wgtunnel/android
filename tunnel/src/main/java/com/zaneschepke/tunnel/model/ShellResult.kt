package com.zaneschepke.tunnel.model

data class ShellResult(
    val code: Int,
    val stdout: List<String>,
    val stderr: List<String> = emptyList(),
) {
    val isSuccess: Boolean
        get() = code == 0

    val isFailure: Boolean
        get() = !isSuccess

    val output: String
        get() = (stdout + stderr).joinToString("\n")
}
