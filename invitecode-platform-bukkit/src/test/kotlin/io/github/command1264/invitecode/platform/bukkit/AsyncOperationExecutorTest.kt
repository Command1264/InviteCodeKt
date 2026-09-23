package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class AsyncOperationExecutorTest {
    @Test
    fun `runs blocking work off caller and returns result through main dispatcher`() {
        val callbacks = CopyOnWriteArrayList<() -> Unit>()
        val completed = CountDownLatch(1)
        val caller = Thread.currentThread().name
        var worker = caller
        var result: String? = null
        AsyncOperationExecutor("invitecode-test", callbacks::add).use { executor ->
            assertTrue(executor.submit(
                operation = {
                    worker = Thread.currentThread().name
                    "ok"
                },
                onSuccess = { result = it; completed.countDown() },
                onFailure = { throw AssertionError(it) },
            ))
            waitForCallbacks(callbacks)
            callbacks.removeAt(0).invoke()
            assertTrue(completed.await(1, TimeUnit.SECONDS))
        }

        assertTrue(worker.startsWith("invitecode-test"))
        assertTrue(worker != caller)
        assertEquals("ok", result)
    }

    @Test
    fun `close rejects new work and suppresses late callback`() {
        val callbacks = CopyOnWriteArrayList<() -> Unit>()
        val release = CountDownLatch(1)
        val executor = AsyncOperationExecutor("invitecode-test", callbacks::add)
        assertTrue(executor.submit(
            operation = { release.await(); "late" },
            onSuccess = { throw AssertionError("late callback must be suppressed") },
            onFailure = { },
        ))

        release.countDown()
        assertTrue(executor.close(Duration.ofSeconds(1)))
        assertFalse(executor.submit({ "rejected" }, {}, {}))
        callbacks.forEach { it.invoke() }
    }

    @Test
    fun `routes callbacks through the selected dispatcher`() {
        val global = CopyOnWriteArrayList<() -> Unit>()
        val player = CopyOnWriteArrayList<() -> Unit>()
        val completed = CountDownLatch(1)
        AsyncOperationExecutor("invitecode-test", global::add).use { executor ->
            val playerOperations = executor.callbacksOn(player::add)
            assertTrue(playerOperations.submit(
                operation = { "player result" },
                onSuccess = { assertEquals("player result", it); completed.countDown() },
                onFailure = { throw AssertionError(it) },
            ))
            waitForCallbacks(player)
            assertTrue(global.isEmpty())
            player.removeAt(0).invoke()
            assertTrue(completed.await(1, TimeUnit.SECONDS))
        }
    }

    private fun waitForCallbacks(callbacks: List<() -> Unit>) {
        repeat(100) {
            if (callbacks.isNotEmpty()) return
            Thread.sleep(5)
        }
        throw AssertionError("Timed out waiting for callback")
    }
}
