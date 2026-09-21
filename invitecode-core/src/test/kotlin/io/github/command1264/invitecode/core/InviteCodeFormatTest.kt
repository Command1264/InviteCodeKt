package io.github.command1264.invitecode.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InviteCodeFormatTest {
    @Test
    fun `color format accepts exactly six hexadecimal characters`() {
        assertEquals(
            "A25981",
            InviteCode.parse("a2-59-81", InviteCodeFormats.colorHex).normalized,
        )

        assertThrows<IllegalArgumentException> {
            InviteCode.parse("A2598", InviteCodeFormats.colorHex)
        }
        assertThrows<IllegalArgumentException> {
            InviteCode.parse("A2598G", InviteCodeFormats.colorHex)
        }
    }

    @Test
    fun `Crockford Base32 format canonicalizes aliases and rejects excluded letters`() {
        val format = InviteCodeFormats.crockfordBase32(length = 6)

        assertEquals("0111Z2", InviteCode.parse("o1-il-z2", format).normalized)
        assertThrows<IllegalArgumentException> {
            InviteCode.parse("12U456", format)
        }
    }

    @Test
    fun `custom format uses the shared normalization and validation contract`() {
        val format = InviteCodeFormats.custom(
            pattern = Regex("VIP[0-9]{4}"),
            description = "VIP Invite Code",
        )

        assertEquals("VIP1234", InviteCode.parse(" vip-1234 ", format).normalized)
        val error = assertThrows<IllegalArgumentException> {
            InviteCode.parse("MEMBER1234", format)
        }
        assertEquals("VIP Invite Code is invalid.", error.message)
    }
}
