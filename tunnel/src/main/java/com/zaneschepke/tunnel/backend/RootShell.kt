package com.zaneschepke.tunnel.backend

import android.content.Context
import com.topjohnwu.superuser.Shell
import com.zaneschepke.tunnel.model.ShellResult
import com.zaneschepke.tunnel.util.RootShellException
import java.io.File

class RootShell(private val context: Context) {

    private val localBinaryDir = File(context.codeCacheDir, "bin")
    private val localTemporaryDir = File(context.cacheDir, "tmp")

    private val preamble: String by lazy {
        val packageName = context.packageName
        val binPath = localBinaryDir.absolutePath
        val tmpPath = localTemporaryDir.absolutePath

        $$"""
        export CALLING_PACKAGE='$$packageName'
        export PATH="$$binPath:$PATH"
        export TMPDIR='$$tmpPath'
        """.trimIndent()
    }

    @Volatile
    private var initialized = false

    init {
        ensureDirs()
    }

    private fun ensureDirs() {
        if (!localBinaryDir.exists() && !localBinaryDir.mkdirs()) {
            throw RootShellException.DirectoryCreationFailed(localBinaryDir.absolutePath)
        }
        if (!localTemporaryDir.exists() && !localTemporaryDir.mkdirs()) {
            throw RootShellException.DirectoryCreationFailed(localTemporaryDir.absolutePath)
        }
    }

    fun requestRootPermission(): Boolean {
        if (Shell.isAppGrantedRoot() == true) {
            return true
        }

        // Triggers the root permission dialog
        val shell = Shell.getShell()

        return shell.isRoot
    }

    @Synchronized
    @Throws(RootShellException::class)
    fun start() {
        if (Shell.isAppGrantedRoot() == false) {
            throw RootShellException.NoRootAccess()
        }

        if (!initialized) {
            val shell = Shell.getShell()
            if (!shell.isAlive) {
                throw RootShellException.ShellStartFailed()
            }

            val result = shell.newJob().add(preamble).exec()
            if (result.code != 0) {
                throw RootShellException.ShellStartFailed(exitCode = result.code)
            }
            initialized = true
        }
    }

    @Throws(RootShellException::class)
    fun run(vararg command: String): ShellResult {
        start() // make sure preamble has already run for this session

        val result = Shell.cmd(*command).exec()

        return ShellResult(
            code = result.code,
            stdout = result.out,
            stderr = result.err
        )
    }

    fun stop() {
        if (initialized) {
            try {
                Shell.getShell().waitAndClose()
            } catch (_: Exception) {
            }
            initialized = false
        }
    }
}