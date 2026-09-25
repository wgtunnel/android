package com.zaneschepke.wireguardautotunnel.service.autotunnel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnreachableTunnelMonitorTest {

    @Test
    fun `stops after grace and releases after cooldown`() = runTest {
        var stops = 0
        var notifications = 0
        val monitor =
            monitor(
                stopTunnel = {
                    stops++
                    Result.success(Unit)
                },
                onStopped = { notifications++ },
            )

        monitor.update(failing())
        advanceTimeBy(GRACE_MS)
        runCurrent()

        assertEquals(1, stops)
        assertEquals(1, notifications)
        assertTrue(ID in monitor.suspended.value)

        advanceTimeBy(COOLDOWN_MS)
        runCurrent()

        assertFalse(ID in monitor.suspended.value)
    }

    @Test
    fun `uses the grace period from the snapshot`() = runTest {
        var stops = 0
        val monitor =
            monitor(
                stopTunnel = {
                    stops++
                    Result.success(Unit)
                }
            )

        monitor.update(failing(gracePeriodMs = GRACE_MS * 3))
        advanceTimeBy(GRACE_MS * 2)
        runCurrent()
        assertEquals(0, stops)

        advanceTimeBy(GRACE_MS)
        runCurrent()
        assertEquals(1, stops)
    }

    @Test
    fun `disabled monitoring never starts grace`() = runTest {
        var stops = 0
        val monitor =
            monitor(
                stopTunnel = {
                    stops++
                    Result.success(Unit)
                }
            )

        monitor.update(failing(enabled = false))
        advanceTimeBy(GRACE_MS * 2)
        runCurrent()

        assertEquals(0, stops)
        assertTrue(monitor.suspended.value.isEmpty())
    }

    @Test
    fun `network loss cancels grace`() = runTest {
        var stops = 0
        val monitor =
            monitor(
                stopTunnel = {
                    stops++
                    Result.success(Unit)
                }
            )

        monitor.update(failing())
        advanceTimeBy(GRACE_MS / 2)
        monitor.update(failing(armFailureIds = emptySet(), keepFailureIds = emptySet()))
        advanceTimeBy(GRACE_MS)
        runCurrent()

        assertEquals(0, stops)
        assertTrue(monitor.suspended.value.isEmpty())
    }

    @Test
    fun `network loss keeps the cooldown`() = runTest {
        val monitor = monitor()

        monitor.update(failing())
        advanceTimeBy(GRACE_MS)
        runCurrent()
        assertTrue(ID in monitor.suspended.value)

        monitor.update(failing(armFailureIds = emptySet(), keepFailureIds = emptySet()))
        runCurrent()

        assertTrue(ID in monitor.suspended.value)
    }

    @Test
    fun `network change lets an in-flight stop finish without notifying`() = runTest {
        val stopStarted = CompletableDeferred<Unit>()
        val allowStop = CompletableDeferred<Unit>()
        var stopFinished = false
        var notifications = 0
        val monitor =
            monitor(
                stopTunnel = {
                    stopStarted.complete(Unit)
                    allowStop.await()
                    stopFinished = true
                    Result.success(Unit)
                },
                onStopped = { notifications++ },
            )

        monitor.update(failing(networkKey = "network-a"))
        advanceTimeBy(GRACE_MS)
        runCurrent()
        stopStarted.await()
        assertTrue(ID in monitor.suspended.value)

        monitor.update(failing(networkKey = "network-b"))
        runCurrent()
        allowStop.complete(Unit)
        runCurrent()

        assertTrue(stopFinished)
        assertTrue(monitor.suspended.value.isEmpty())
        assertEquals(0, notifications)
    }

    @Test
    fun `notifies once across repeated stop cycles`() = runTest {
        var stops = 0
        var notifications = 0
        val monitor =
            monitor(
                stopTunnel = {
                    stops++
                    Result.success(Unit)
                },
                onStopped = { notifications++ },
            )

        monitor.update(failing())
        advanceTimeBy(GRACE_MS)
        runCurrent()
        monitor.update(stopped())
        advanceTimeBy(COOLDOWN_MS)
        runCurrent()
        assertFalse(ID in monitor.suspended.value)

        monitor.update(failing())
        advanceTimeBy(GRACE_MS)
        runCurrent()

        assertEquals(2, stops)
        assertEquals(1, notifications)
    }

    @Test
    fun `successful reconnect clears cooldown`() = runTest {
        val monitor = monitor()

        monitor.update(failing())
        advanceTimeBy(GRACE_MS)
        runCurrent()
        assertTrue(ID in monitor.suspended.value)

        monitor.update(connected())

        assertTrue(monitor.suspended.value.isEmpty())
    }

    @Test
    fun `failed stop rolls back suspension and retries`() = runTest {
        var attempts = 0
        val monitor =
            monitor(
                stopTunnel = {
                    attempts++
                    if (attempts == 1) Result.failure(IllegalStateException("stop failed"))
                    else Result.success(Unit)
                }
            )

        monitor.update(failing())
        advanceTimeBy(GRACE_MS)
        runCurrent()

        assertEquals(1, attempts)
        assertTrue(monitor.suspended.value.isEmpty())

        advanceTimeBy(STOP_RETRY_MS)
        runCurrent()
        assertEquals(2, attempts)
        assertTrue(ID in monitor.suspended.value)
    }

    @Test
    fun `failed notification keeps cooldown and is retried next cycle`() = runTest {
        var notificationAttempts = 0
        val monitor =
            monitor(
                onStopped = {
                    notificationAttempts++
                    throw IllegalStateException("notify failed")
                }
            )

        monitor.update(failing())
        advanceTimeBy(GRACE_MS)
        runCurrent()

        assertEquals(1, notificationAttempts)
        assertTrue(ID in monitor.suspended.value)

        advanceTimeBy(COOLDOWN_MS)
        runCurrent()
        assertTrue(monitor.suspended.value.isEmpty())

        monitor.update(stopped())
        monitor.update(failing())
        advanceTimeBy(GRACE_MS)
        runCurrent()

        assertEquals(2, notificationAttempts)
        assertTrue(ID in monitor.suspended.value)
    }

    @Test
    fun `grace continues across a recovery bounce`() = runTest {
        var stops = 0
        val monitor =
            monitor(
                stopTunnel = {
                    stops++
                    Result.success(Unit)
                }
            )

        monitor.update(failing())
        advanceTimeBy(GRACE_MS / 2)
        monitor.update(failing(armFailureIds = emptySet()))
        advanceTimeBy(GRACE_MS / 2)
        runCurrent()

        assertEquals(1, stops)
    }

    private fun TestScope.monitor(
        stopTunnel: suspend (Int) -> Result<Unit> = { Result.success(Unit) },
        onStopped: suspend (Int) -> Unit = {},
    ) =
        UnreachableTunnelMonitor(
            scope = backgroundScope,
            stopTunnel = stopTunnel,
            onStopped = onStopped,
            cooldownMs = COOLDOWN_MS,
            stopRetryDelayMs = STOP_RETRY_MS,
        )

    private fun failing(
        enabled: Boolean = true,
        networkKey: String = NETWORK,
        gracePeriodMs: Long = GRACE_MS,
        armFailureIds: Set<Int> = setOf(ID),
        keepFailureIds: Set<Int> = setOf(ID),
    ) =
        UnreachableTunnelSnapshot(
            enabled = enabled,
            networkKey = networkKey,
            armFailureGracePeriodsMs = armFailureIds.associateWith { gracePeriodMs },
            keepFailureIds = keepFailureIds,
        )

    private fun stopped() = UnreachableTunnelSnapshot(enabled = true, networkKey = NETWORK)

    private fun connected() =
        UnreachableTunnelSnapshot(enabled = true, networkKey = NETWORK, connectedIds = setOf(ID))

    companion object {
        private const val ID = 42
        private const val NETWORK = "network"
        private const val GRACE_MS = 1_000L
        private const val COOLDOWN_MS = 5_000L
        private const val STOP_RETRY_MS = 2_000L
    }
}
