package io.github.command1264.invitecode.platform.modern

import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.InviteCodeFormats
import io.github.command1264.invitecode.platform.bukkit.NativeDialogRequest
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.logging.Logger

class ReflectiveNativeDialogInputTest {
    @Test
    fun `successful submission is delivered once`() {
        val backend = FakeDialogBackend()
        val adapter = ReflectiveNativeDialogInput(backend, Logger.getAnonymousLogger())
        val playerId = UUID.randomUUID()
        val player = player(playerId)
        val submitted = mutableListOf<String>()

        assertTrue(adapter.show(player, request, submitted::add) {})
        backend.emit(DialogResponse.Submitted(playerId, " A1B2C3 "))
        backend.emit(DialogResponse.Submitted(playerId, "FFFFFF"))

        assertEquals(listOf(" A1B2C3 "), submitted)
    }

    @Test
    fun `missing input is delivered as an invalid empty value`() {
        val backend = FakeDialogBackend()
        val adapter = ReflectiveNativeDialogInput(backend, Logger.getAnonymousLogger())
        val playerId = UUID.randomUUID()
        var submitted: String? = null

        adapter.show(player(playerId), request, { submitted = it }) {}
        backend.emit(DialogResponse.Submitted(playerId, null))

        assertEquals("", submitted)
    }

    @Test
    fun `malformed input is delivered to domain validation unchanged`() {
        val backend = FakeDialogBackend()
        val adapter = ReflectiveNativeDialogInput(backend, Logger.getAnonymousLogger())
        val playerId = UUID.randomUUID()
        var submitted: String? = null

        adapter.show(player(playerId), request, { submitted = it }) {}
        backend.emit(DialogResponse.Submitted(playerId, "XYZ"))

        assertEquals("XYZ", submitted)
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            InviteCode.parse(requireNotNull(submitted), InviteCodeFormats.colorHex)
        }
    }

    @Test
    fun `cancel and disconnect discard callbacks without submitting`() {
        val backend = FakeDialogBackend()
        val adapter = ReflectiveNativeDialogInput(backend, Logger.getAnonymousLogger())
        val cancelledId = UUID.randomUUID()
        val disconnectedId = UUID.randomUUID()
        var cancellations = 0
        val submissions = mutableListOf<String>()

        adapter.show(player(cancelledId), request, submissions::add) { cancellations++ }
        backend.emit(DialogResponse.Cancelled(cancelledId))
        adapter.show(player(disconnectedId), request, submissions::add) { cancellations++ }
        adapter.disconnect(disconnectedId)
        backend.emit(DialogResponse.Submitted(disconnectedId, "A1B2C3"))

        assertEquals(1, cancellations)
        assertTrue(submissions.isEmpty())
    }

    @Test
    fun `cancel only closes a dialog owned by the active callback`() {
        val backend = FakeDialogBackend()
        val adapter = ReflectiveNativeDialogInput(backend, Logger.getAnonymousLogger())
        val player = player(UUID.randomUUID())

        adapter.cancel(player)
        adapter.show(player, request, {}) {}
        adapter.cancel(player)
        adapter.cancel(player)

        assertEquals(1, backend.closeCount)
    }

    @Test
    fun `backend failure returns false and removes pending callback`() {
        val backend = FakeDialogBackend(failShow = true)
        val adapter = ReflectiveNativeDialogInput(backend, Logger.getAnonymousLogger())
        val playerId = UUID.randomUUID()
        val submissions = mutableListOf<String>()

        assertFalse(adapter.show(player(playerId), request, submissions::add) {})
        backend.emit(DialogResponse.Submitted(playerId, "A1B2C3"))

        assertTrue(submissions.isEmpty())
    }

    private fun player(playerId: UUID): Player = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(Player::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getUniqueId" -> playerId
            "isOnline" -> true
            "toString" -> "Player($playerId)"
            else -> defaultValue(method.returnType)
        }
    } as Player

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Byte::class.javaPrimitiveType -> 0.toByte()
        Short::class.javaPrimitiveType -> 0.toShort()
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0F
        Double::class.javaPrimitiveType -> 0.0
        Char::class.javaPrimitiveType -> '\u0000'
        else -> null
    }

    private companion object {
        val request = NativeDialogRequest("Title", "Code", "Confirm", "Cancel", 64)
    }
}

private class FakeDialogBackend(
    private val failShow: Boolean = false,
) : DialogBackend {
    override val provider: String = "FAKE"
    var closeCount: Int = 0
        private set
    private lateinit var listener: (DialogResponse) -> Unit

    override fun register(listener: (DialogResponse) -> Unit) {
        this.listener = listener
    }

    override fun show(player: Player, request: NativeDialogRequest) {
        if (failShow) throw NoSuchMethodException("showDialog")
    }

    override fun close(player: Player) {
        closeCount++
    }

    fun emit(response: DialogResponse) {
        listener(response)
    }
}
