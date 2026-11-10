package io.github.command1264.invitecode.platform.bukkit

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class RuntimePhase {
    STARTING,
    ACTIVE,
    MAINTENANCE,
    STOPPING,
    STOPPED,
}

class PluginRuntimeState {
    private val currentPhase = AtomicReference(RuntimePhase.STARTING)
    private val currentMaintenanceReason = AtomicReference<String?>(null)

    val phase: RuntimePhase
        get() = currentPhase.get()

    val maintenanceReason: String?
        get() = currentMaintenanceReason.get()

    fun activate() {
        transition(setOf(RuntimePhase.STARTING, RuntimePhase.MAINTENANCE), RuntimePhase.ACTIVE)
        currentMaintenanceReason.set(null)
    }

    fun enterMaintenance(reason: String) {
        require(reason.isNotBlank()) { "Maintenance reason must not be blank." }
        while (true) {
            val existing = currentPhase.get()
            check(existing in setOf(RuntimePhase.STARTING, RuntimePhase.ACTIVE, RuntimePhase.MAINTENANCE)) {
                "Cannot enter maintenance from $existing."
            }
            if (currentPhase.compareAndSet(existing, RuntimePhase.MAINTENANCE)) {
                currentMaintenanceReason.set(reason)
                return
            }
        }
    }

    fun beginStopping() {
        while (true) {
            val existing = currentPhase.get()
            if (existing == RuntimePhase.STOPPING) return
            check(existing != RuntimePhase.STOPPED) { "Runtime has already stopped." }
            if (currentPhase.compareAndSet(existing, RuntimePhase.STOPPING)) return
        }
    }

    fun finishStopping() {
        transition(setOf(RuntimePhase.STOPPING), RuntimePhase.STOPPED)
        currentMaintenanceReason.set(null)
    }

    fun acceptsGameplayCommands(): Boolean = phase == RuntimePhase.ACTIVE

    private fun transition(allowed: Set<RuntimePhase>, target: RuntimePhase) {
        while (true) {
            val existing = currentPhase.get()
            check(existing in allowed) { "Cannot transition runtime from $existing to $target." }
            if (currentPhase.compareAndSet(existing, target)) return
        }
    }
}

data class RuntimeSnapshot<T>(
    val generation: Long,
    val value: T,
)

class AtomicRuntimeSnapshot<T> private constructor(initialSnapshot: RuntimeSnapshot<T>?) {
    constructor(initialValue: T) : this(RuntimeSnapshot(0, initialValue))
    constructor() : this(null)

    private val generation = AtomicLong(initialSnapshot?.generation ?: -1)
    private val snapshot = AtomicReference(initialSnapshot)

    fun current(): RuntimeSnapshot<T> = requireNotNull(snapshot.get()) {
        "Runtime snapshot has not been installed."
    }

    fun currentOrNull(): RuntimeSnapshot<T>? = snapshot.get()

    fun replace(candidate: T): RuntimeSnapshot<T> {
        val replacement = RuntimeSnapshot(generation.incrementAndGet(), candidate)
        snapshot.set(replacement)
        return replacement
    }
}
