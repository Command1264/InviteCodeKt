package io.github.command1264.invitecode.platform.bukkit

import io.github.command1264.invitecode.core.ClaimConfirmation
import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.InviteCodeFormats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ClaimInteractionSessionsTest {
    @Test
    fun `chat input does not consume an active confirmation`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val playerId = UUID.randomUUID()
        val confirmation = ClaimConfirmation.present(
            playerId,
            UUID.randomUUID(),
            "Inviter",
            InviteCode.parse("ABCDEF", InviteCodeFormats.colorHex),
            now,
            Duration.ofSeconds(30),
        )
        val sessions = ClaimInteractionSessions { now }
        sessions.startConfirmation(confirmation)

        assertEquals(null, sessions.consumeChatInput(playerId, "not a code"))
        assertEquals(confirmation, sessions.consumeConfirmation(playerId))
    }

    @Test
    fun `expired chat input is removed exactly once`() {
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val playerId = UUID.randomUUID()
        val sessions = ClaimInteractionSessions { now }
        sessions.startChatInput(playerId, Duration.ofSeconds(1))

        assertFalse(sessions.expireChatInput(playerId))
        now = now.plusSeconds(2)
        assertTrue(sessions.expireChatInput(playerId))
        assertFalse(sessions.expireChatInput(playerId))
    }

    @Test
    fun `chat input is private one shot and expires`() {
        var now = Instant.EPOCH
        val sessions = ClaimInteractionSessions { now }

        sessions.startChatInput(playerId, Duration.ofSeconds(60))
        assertTrue(sessions.hasChatInput(playerId))
        assertEquals("a1b2c3", sessions.consumeChatInput(playerId, "  a1b2c3  "))
        assertNull(sessions.consumeChatInput(playerId, "second"))

        sessions.startChatInput(playerId, Duration.ofSeconds(60))
        now = now.plusSeconds(61)
        assertNull(sessions.consumeChatInput(playerId, "expired"))
        assertFalse(sessions.hasChatInput(playerId))
    }

    @Test
    fun `confirmation replaces input and is consumed once`() {
        val sessions = ClaimInteractionSessions { Instant.EPOCH }
        sessions.startChatInput(playerId, Duration.ofSeconds(60))
        val confirmation = ClaimConfirmation.present(
            inviteeId = playerId,
            inviterId = inviterId,
            inviterName = "Inviter",
            inviteCode = InviteCode.parse("A1B2C3"),
            presentedAt = Instant.EPOCH,
            validity = Duration.ofSeconds(30),
        )

        sessions.startConfirmation(confirmation)

        assertFalse(sessions.hasChatInput(playerId))
        assertEquals(confirmation, sessions.consumeConfirmation(playerId))
        assertNull(sessions.consumeConfirmation(playerId))
    }

    @Test
    fun `dialog input is private one shot and expires`() {
        var now = Instant.EPOCH
        val sessions = ClaimInteractionSessions { now }

        sessions.startDialogInput(playerId, Duration.ofSeconds(60))
        assertEquals("A1B2C3", sessions.consumeDialogInput(playerId, "  A1B2C3  "))
        assertNull(sessions.consumeDialogInput(playerId, "second"))

        sessions.startDialogInput(playerId, Duration.ofSeconds(60))
        now = now.plusSeconds(61)
        assertTrue(sessions.expireDialogInput(playerId))
        assertFalse(sessions.expireDialogInput(playerId))
    }

    @Test
    fun `cancel and disconnect clear every session`() {
        val sessions = ClaimInteractionSessions { Instant.EPOCH }
        sessions.startChatInput(playerId, Duration.ofSeconds(60))
        assertTrue(sessions.cancel(playerId))
        assertFalse(sessions.cancel(playerId))

        sessions.startChatInput(playerId, Duration.ofSeconds(60))
        sessions.disconnect(playerId)
        assertFalse(sessions.hasChatInput(playerId))
    }

    companion object {
        private val playerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private val inviterId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }
}
