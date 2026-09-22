package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InputModeTest {
    @Test
    fun `auto and dialog use native dialog when available`() {
        val capabilities = PlatformCapabilities(nativeDialogInput = true, rgbText = true, dialogProvider = "PAPER")

        assertEquals(ClaimInputChannel.DIALOG, InputMode.AUTO.resolve(capabilities))
        assertEquals(ClaimInputChannel.DIALOG, InputMode.DIALOG.resolve(capabilities))
    }

    @Test
    fun `auto and dialog fall back to private chat when unavailable`() {
        val capabilities = PlatformCapabilities(nativeDialogInput = false, rgbText = false)

        assertEquals(ClaimInputChannel.CHAT, InputMode.AUTO.resolve(capabilities))
        assertEquals(ClaimInputChannel.CHAT, InputMode.DIALOG.resolve(capabilities))
        assertEquals(ClaimInputChannel.CHAT, InputMode.CHAT.resolve(capabilities))
        assertEquals(ClaimInputChannel.COMMAND_ONLY, InputMode.COMMAND_ONLY.resolve(capabilities))
    }
}
