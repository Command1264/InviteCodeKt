package io.github.command1264.invitecode.platform.modern

import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.InviteCodeFormats
import org.bukkit.ChatColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RgbInviteCodeDisplayFormatterTest {
    @Test
    fun `color invite code uses its exact six digit RGB value`() {
        val formatter = RgbInviteCodeDisplayFormatter { color -> "<$color>" }

        assertEquals(
            "<#A25981>A25981${ChatColor.RESET}",
            formatter.display(InviteCode.parse("A25981", InviteCodeFormats.colorHex)),
        )
    }

    @Test
    fun `non color format remains plain text`() {
        val formatter = RgbInviteCodeDisplayFormatter { color -> "<$color>" }

        assertEquals("VIP1234", formatter.display(InviteCode.parse("VIP1234")))
    }
}
