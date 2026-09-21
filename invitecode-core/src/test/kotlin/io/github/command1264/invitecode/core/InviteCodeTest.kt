package io.github.command1264.invitecode.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InviteCodeTest {
    @Test
    fun `six digit hex code is a color invite code`() {
        val code = InviteCode.parse("a2-59-81")

        assertEquals("A25981", code.normalized)
        assertTrue(code.isColorInviteCode)
    }

    @Test
    fun `codes compare by their normalized representation`() {
        assertEquals(
            InviteCode.parse("a2-59-81"),
            InviteCode.parse("A25981"),
        )
    }

    @Test
    fun `punctuation other than display separator is rejected`() {
        assertThrows<IllegalArgumentException> {
            InviteCode.parse("ABC/123")
        }
    }

    @Test
    fun `color invite code exposes RGB and nearest legacy color`() {
        val color = InviteCode.parse("FEA901", InviteCodeFormats.colorHex).color

        requireNotNull(color)
        assertEquals(254, color.red)
        assertEquals(169, color.green)
        assertEquals(1, color.blue)
        assertEquals(0xFEA901, color.rgb)
        assertEquals(LegacyInviteCodeColor.GOLD, color.nearestLegacyColor())
        assertNull(InviteCode.parse("VIP1234").color)
    }
}
