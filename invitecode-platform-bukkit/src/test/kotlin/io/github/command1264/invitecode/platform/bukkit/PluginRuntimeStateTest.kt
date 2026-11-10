package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PluginRuntimeStateTest {
    @Test
    fun `startup can become active and then stop`() {
        val runtime = PluginRuntimeState()

        runtime.activate()
        assertEquals(RuntimePhase.ACTIVE, runtime.phase)
        assertTrue(runtime.acceptsGameplayCommands())

        runtime.beginStopping()
        assertEquals(RuntimePhase.STOPPING, runtime.phase)
        assertFalse(runtime.acceptsGameplayCommands())

        runtime.finishStopping()
        assertEquals(RuntimePhase.STOPPED, runtime.phase)
    }

    @Test
    fun `startup failure enters maintenance and can recover`() {
        val runtime = PluginRuntimeState()

        runtime.enterMaintenance("storage unavailable")
        assertEquals(RuntimePhase.MAINTENANCE, runtime.phase)
        assertEquals("storage unavailable", runtime.maintenanceReason)
        assertFalse(runtime.acceptsGameplayCommands())

        runtime.activate()
        assertEquals(RuntimePhase.ACTIVE, runtime.phase)
        assertEquals(null, runtime.maintenanceReason)
    }

    @Test
    fun `stopped runtime cannot become active again`() {
        val runtime = PluginRuntimeState()
        runtime.beginStopping()
        runtime.finishStopping()

        assertThrows(IllegalStateException::class.java) { runtime.activate() }
    }

    @Test
    fun `configuration snapshots swap as one generation`() {
        val snapshots = AtomicRuntimeSnapshot("first")

        val updated = snapshots.replace("second")

        assertEquals(1, updated.generation)
        assertEquals("second", snapshots.current().value)
    }
}
