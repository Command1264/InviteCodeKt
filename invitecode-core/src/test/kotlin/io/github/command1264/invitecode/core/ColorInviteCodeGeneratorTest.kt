package io.github.command1264.invitecode.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ColorInviteCodeGeneratorTest {
    @Test
    fun `crockford generator uses configured length and alphabet`() {
        val generator = CrockfordInviteCodeGenerator(8) { destination ->
            byteArrayOf(0, 1, 10, 17, 24, 31, 32, 255.toByte()).copyInto(destination)
        }

        assertEquals("01AHRZ0Z", generator.generate().displayValue())
    }

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
