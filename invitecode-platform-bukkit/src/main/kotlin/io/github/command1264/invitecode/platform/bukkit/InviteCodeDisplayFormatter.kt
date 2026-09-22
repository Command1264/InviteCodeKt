package io.github.command1264.invitecode.platform.bukkit

import io.github.command1264.invitecode.core.InviteCode
import org.bukkit.ChatColor

fun interface InviteCodeDisplayFormatter {
    fun display(code: InviteCode): String
}

object LegacyInviteCodeDisplayFormatter : InviteCodeDisplayFormatter {
    override fun display(code: InviteCode): String {
        val legacy = code.color?.nearestLegacyColor() ?: return code.displayValue()
        return "${ChatColor.COLOR_CHAR}${legacy.code}${code.displayValue()}${ChatColor.RESET}"
    }
}
