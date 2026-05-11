package com.zaneschepke.tunnel.util

sealed class BackendException : Exception() {
    class StateConflict(override val message: String) : BackendException()

    class InternalError(override val message: String) : BackendException()

    class Unauthorized(override val message: String) : BackendException()
}
