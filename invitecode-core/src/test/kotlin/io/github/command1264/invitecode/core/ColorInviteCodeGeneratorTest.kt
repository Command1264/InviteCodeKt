package io.github.command1264.invitecode.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ColorInviteCodeGeneratorTest {
    @Test
    fun `generator turns three random bytes into an uppercase color invite code`() {
        val generator = ColorInviteCodeGenerator(
            randomBytes = RandomBytes { destination ->
                byteArrayOf(0x00, 0x0F, 0xFF.toByte()).copyInto(destination)
            },
        )

        val code = generator.generate()

        assertEquals("000FFF", code.normalized)
        assertTrue(code.isColorInviteCode)
    }
}
