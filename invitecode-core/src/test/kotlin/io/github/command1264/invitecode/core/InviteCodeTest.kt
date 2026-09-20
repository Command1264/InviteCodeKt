package io.github.command1264.invitecode.core

import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `punctuation other than display separator is rejected`() {
        assertThrows<IllegalArgumentException> {
            InviteCode.parse("ABC/123")
        }
    }
}
