package com.zaneschepke.tunnel.util

import com.topjohnwu.superuser.Shell
import com.zaneschepke.tunnel.model.ShellResult
import timber.log.Timber

object RootShell {

    fun hasRootPermission(): Boolean {
        return Shell.isAppGrantedRoot() == true
    }

    fun requestRootPermission(): Boolean {
        return try {
            val shell = Shell.cmd("su").exec()
            shell.isSuccess
        } catch (e: Exception) {
            Timber.e(e, "Root permission request failed or timed out")
            false
        }
    }

    fun run(command: String): ShellResult {
        try {
            val result = Shell.cmd(command).exec()

            Timber.d("Root shell command result: ${result.out.joinToString("\n")}")

            return ShellResult(code = result.code, stdout = result.out, stderr = result.err)
        } catch (e: Exception) {
            Timber.e(e, "Root command failed")
            throw e
        }
    }
}
