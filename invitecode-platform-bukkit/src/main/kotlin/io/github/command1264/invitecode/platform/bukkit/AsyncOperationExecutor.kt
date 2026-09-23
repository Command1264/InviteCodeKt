package io.github.command1264.invitecode.platform.bukkit

import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface BackgroundOperationExecutor {
    fun <T> submit(
        operation: () -> T,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): Boolean
}

class AsyncOperationExecutor(
    threadName: String,
    private val dispatchMain: (() -> Unit) -> Unit,
) : BackgroundOperationExecutor, AutoCloseable {
    private val accepting = AtomicBoolean(true)
    private val executor = Executors.newSingleThreadExecutor { operation ->
        Thread(operation, threadName).apply { isDaemon = true }
    }

    val acceptingOperations: Boolean
        get() = accepting.get()

    fun callbacksOn(dispatch: (() -> Unit) -> Unit): BackgroundOperationExecutor =
        object : BackgroundOperationExecutor {
            override fun <T> submit(
                operation: () -> T,
                onSuccess: (T) -> Unit,
                onFailure: (Throwable) -> Unit,
            ): Boolean = submitWithDispatcher(dispatch, operation, onSuccess, onFailure)
        }

    override fun <T> submit(
        operation: () -> T,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): Boolean = submitWithDispatcher(dispatchMain, operation, onSuccess, onFailure)

    private fun <T> submitWithDispatcher(
        dispatcher: (() -> Unit) -> Unit,
        operation: () -> T,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): Boolean {
        if (!accepting.get()) return false
        return try {
            executor.execute {
                val result = runCatching(operation)
                if (accepting.get()) {
                    dispatcher {
                        if (!accepting.get()) return@dispatcher
                        result.fold(onSuccess, onFailure)
                    }
                }
            }
            true
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            false
        }
    }

    fun close(timeout: Duration): Boolean {
        require(!timeout.isNegative) { "Executor shutdown timeout must not be negative." }
        if (accepting.getAndSet(false)) executor.shutdown()
        val terminated = executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!terminated) executor.shutdownNow()
        return terminated
    }

    override fun close() {
        close(Duration.ofSeconds(5))
    }
}
