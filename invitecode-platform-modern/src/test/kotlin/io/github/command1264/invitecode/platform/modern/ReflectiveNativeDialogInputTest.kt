package io.github.command1264.invitecode.platform.modern

import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.InviteCodeFormats
import io.github.command1264.invitecode.platform.bukkit.NativeDialogRequest
import io.github.command1264.invitecode.platform.bukkit.NativeDialogConfirmationRequest
import io.github.command1264.invitecode.platform.bukkit.MessageDecoration
import io.github.command1264.invitecode.platform.bukkit.MessageSegment
import io.github.command1264.invitecode.platform.bukkit.PresentedMessage
import io.github.command1264.invitecode.platform.bukkit.NativeDialogPresentation
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
    fun `styled component builder preserves segment text RGB and decorations`() {
        val component = buildStyledComponent(
            PresentedMessage(listOf(
                MessageSegment("prefix ", 0x12ABEF, setOf(MessageDecoration.BOLD)),
                MessageSegment(
                    "dynamic {value}", 0xCC8844,
                    setOf(MessageDecoration.ITALIC, MessageDecoration.UNDERLINE, MessageDecoration.STRIKETHROUGH, MessageDecoration.OBFUSCATED),
                ),
            )),
            text = { TestTextComponent(it) },
            color = { value, rgb -> value.copy(color = rgb) },
            decorate = { value, decoration -> value.copy(decorations = value.decorations + decoration) },
            append = { left, right -> left.copy(children = left.children + right) },
        )

        assertEquals("prefix dynamic {value}", component.children.joinToString("") { it.text })
        assertEquals(0x12ABEF, component.children[0].color)
        assertEquals(setOf(MessageDecoration.BOLD), component.children[0].decorations)
        assertEquals(0xCC8844, component.children[1].color)
        assertEquals(
            setOf(MessageDecoration.ITALIC, MessageDecoration.UNDERLINE, MessageDecoration.STRIKETHROUGH, MessageDecoration.OBFUSCATED),
            component.children[1].decorations,
        )
    }

    @Test
    fun `styled component text neutralizes legacy markers and controls without reparsing ampersand codes`() {
        val component = buildStyledComponent(
            PresentedMessage(listOf(MessageSegment("name §cRED\u001B[31m\u0007 &c\n\tOK"))),
            text = { TestTextComponent(it) },
            color = { value, rgb -> value.copy(color = rgb) },
            decorate = { value, decoration -> value.copy(decorations = value.decorations + decoration) },
            append = { left, right -> left.copy(children = left.children + right) },
        )

        assertEquals("name &cRED[31m &c\n\tOK", component.children.single().text)
    }

    @Test
    fun `typed Adventure stand-in keeps sibling bold style local after reset`() {
        val component = buildStyledComponent(
            PresentedMessage(listOf(
                MessageSegment("bold", decorations = setOf(MessageDecoration.BOLD)),
                MessageSegment(" reset", decorations = emptySet()),
                MessageSegment(" bold again", decorations = setOf(MessageDecoration.BOLD)),
            )),
            text = { TestTextComponent(it) },
            color = { value, rgb -> value.copy(color = rgb) },
            decorate = { value, decoration -> value.copy(decorations = value.decorations + decoration) },
            append = { left, right -> left.copy(children = left.children + right) },
        )

        assertEquals(listOf("bold", " reset", " bold again"), component.children.map { it.text })
        assertEquals(setOf(MessageDecoration.BOLD), component.children[0].decorations)
        assertTrue(component.children[1].decorations.isEmpty())
        assertEquals(setOf(MessageDecoration.BOLD), component.children[2].decorations)
    }

    @Test
    fun `real Bungee components keep sibling bold style local after reset`() {
        val baseComponent = Class.forName("net.md_5.bungee.api.chat.BaseComponent")
        val textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent")
        val constructor = textComponent.getConstructor(String::class.java)
        val setBold = baseComponent.getMethod("setBold", Boolean::class.javaObjectType)
        val setItalic = baseComponent.getMethod("setItalic", Boolean::class.javaObjectType)
        val setUnderlined = baseComponent.getMethod("setUnderlined", Boolean::class.javaObjectType)
        val setStrikethrough = baseComponent.getMethod("setStrikethrough", Boolean::class.javaObjectType)
        val setObfuscated = baseComponent.getMethod("setObfuscated", Boolean::class.javaObjectType)
        val decorationMethods = mapOf(
            MessageDecoration.BOLD to setBold,
            MessageDecoration.ITALIC to setItalic,
            MessageDecoration.UNDERLINE to setUnderlined,
            MessageDecoration.STRIKETHROUGH to setStrikethrough,
            MessageDecoration.OBFUSCATED to setObfuscated,
        )

        val component = buildStyledComponent(
            PresentedMessage(listOf(
                MessageSegment("bold", decorations = setOf(MessageDecoration.BOLD)),
                MessageSegment(" reset", decorations = emptySet()),
                MessageSegment(" bold again", decorations = setOf(MessageDecoration.BOLD)),
            )),
            text = { constructor.newInstance(it) },
            color = { value, _ -> value },
            decorate = { value, decoration -> decorationMethods.getValue(decoration).invoke(value, true); value },
            append = { left, right -> baseComponent.getMethod("addExtra", baseComponent).invoke(left, right); left },
        )
        val children = baseComponent.getMethod("getExtra").invoke(component) as List<*>
        val boldStates = children.map { baseComponent.getMethod("isBold").invoke(it) as Boolean? }

        assertEquals(3, children.size)
        assertEquals(listOf(true, false, true), boldStates)
    }

    @Test
    fun `presentation request reaches backend unchanged without affecting callbacks`() {
        val backend = FakeDialogBackend()
        val adapter = ReflectiveNativeDialogInput(backend, Logger.getAnonymousLogger())
        val playerId = UUID.randomUUID()
        val presentation = presentation()
        val presentedRequest = request.copy(presentation = presentation)
        var submitted: String? = null

        assertTrue(adapter.show(player(playerId), presentedRequest, { submitted = it }) {})
        assertEquals(presentedRequest, backend.lastInputRequest)
        backend.emit(DialogResponse.Submitted(playerId, "CODE"))
        assertEquals("CODE", submitted)
    }

    @Test
    fun `confirmation presentation request reaches backend unchanged and preserves action callbacks`() {
        val backend = FakeDialogBackend()
        val adapter = ReflectiveNativeDialogInput(backend, Logger.getAnonymousLogger())
        val playerId = UUID.randomUUID()
        val request = confirmationRequest.copy(presentation = presentation())
        var confirmed = 0
        var declined = 0

        assertTrue(adapter.showConfirmation(player(playerId), request, { confirmed++ }, { declined++ }))
        assertEquals(request, backend.lastConfirmationRequest)
        backend.emit(DialogResponse.Confirmed(playerId))
        assertEquals(1, confirmed)
        assertEquals(0, declined)
    }

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

    @Test
    fun `confirmation accepts only the matching dialog action once`() {
        val backend = FakeDialogBackend()
        val adapter = ReflectiveNativeDialogInput(backend, Logger.getAnonymousLogger())
        val playerId = UUID.randomUUID()
        val player = player(playerId)
        var confirmations = 0
        var declines = 0

        assertTrue(adapter.showConfirmation(player, confirmationRequest, { confirmations++ }, { declines++ }))
        backend.emit(DialogResponse.Submitted(playerId, "A1B2C3"))
        backend.emit(DialogResponse.Confirmed(playerId))
        backend.emit(DialogResponse.Confirmed(playerId))
        assertEquals(1, confirmations)
        assertEquals(0, declines)

        assertTrue(adapter.showConfirmation(player, confirmationRequest, { confirmations++ }, { declines++ }))
        backend.emit(DialogResponse.Declined(playerId))
        assertEquals(1, confirmations)
        assertEquals(1, declines)
    }

    @Test
    fun `failed confirmation dialog does not leave an active callback`() {
        val backend = FakeDialogBackend(failShow = true)
        val adapter = ReflectiveNativeDialogInput(backend, Logger.getAnonymousLogger())
        val playerId = UUID.randomUUID()
        var confirmations = 0

        assertFalse(adapter.showConfirmation(player(playerId), confirmationRequest, { confirmations++ }, {}))
        backend.emit(DialogResponse.Confirmed(playerId))

        assertEquals(0, confirmations)
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
        val confirmationRequest = NativeDialogConfirmationRequest(
            "Confirm", "Inviter: Alice", "Code: ", "A1B2C3", 0xA1B2C3,
            "Use this code?", "Choose within 60 seconds:", "Agree", "Disagree", "Click to agree", "Click to disagree",
        )
    }
}

private class FakeDialogBackend(
    private val failShow: Boolean = false,
) : DialogBackend {
    override val provider: String = "FAKE"
    var closeCount: Int = 0
        private set
    private lateinit var listener: (DialogResponse) -> Unit
    var lastInputRequest: NativeDialogRequest? = null
        private set
    var lastConfirmationRequest: NativeDialogConfirmationRequest? = null
        private set

    override fun register(listener: (DialogResponse) -> Unit) {
        this.listener = listener
    }

    override fun show(player: Player, request: NativeDialogRequest) {
        lastInputRequest = request
        if (failShow) throw NoSuchMethodException("showDialog")
    }

    override fun showConfirmation(player: Player, request: NativeDialogConfirmationRequest) {
        lastConfirmationRequest = request
        if (failShow) throw NoSuchMethodException("showDialog")
    }

    override fun close(player: Player) {
        closeCount++
    }

    fun emit(response: DialogResponse) {
        listener(response)
    }
}

private data class TestTextComponent(
    val text: String,
    val color: Int? = null,
    val decorations: Set<MessageDecoration> = emptySet(),
    val children: List<TestTextComponent> = emptyList(),
)

private fun presentation() = NativeDialogPresentation(
    title = PresentedMessage(listOf(MessageSegment("[Invite] ", 0x40C0FF, setOf(MessageDecoration.BOLD)), MessageSegment("Title"))),
    body = listOf(PresentedMessage(listOf(MessageSegment("Invite from "), MessageSegment("Alice", 0x55FF55)))),
    confirm = PresentedMessage(listOf(MessageSegment("Accept", 0x55FF55, setOf(MessageDecoration.BOLD)))),
    cancel = PresentedMessage(listOf(MessageSegment("Cancel", 0xFF5555))),
    inputLabel = PresentedMessage(listOf(MessageSegment("Invite Code: ", 0xAABBCC))),
    confirmHint = PresentedMessage(listOf(MessageSegment("Confirm hint", 0x55FF55))),
    cancelHint = PresentedMessage(listOf(MessageSegment("Cancel hint", 0xFF5555))),
)
