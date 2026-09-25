package io.github.command1264.invitecode.plugin

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent

/** Interactive chat affordances isolated from the legacy text and RGB Invite Code formatter. */
internal object PlayerChatActions {
    fun copyCode(code: String, copyLabel: String, selectLabel: String): TextComponent {
        // COPY_TO_CLIPBOARD is absent from the Bungee chat API bundled by Spigot 1.8.8.
        val clipboard = enumValues<ClickEvent.Action>().firstOrNull { it.name == "COPY_TO_CLIPBOARD" }
        val action = clipboard ?: ClickEvent.Action.SUGGEST_COMMAND
        val label = if (clipboard != null) copyLabel else selectLabel
        return TextComponent("[$label]").apply {
            color = ChatColor.AQUA
            clickEvent = ClickEvent(action, code)
        }
    }

    fun confirmation(agreeLabel: String, disagreeLabel: String): Array<TextComponent> = arrayOf(
        TextComponent("[$agreeLabel]").apply {
            color = ChatColor.GREEN
            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/invitecode confirm")
        },
        TextComponent("  [$disagreeLabel]").apply {
            color = ChatColor.RED
            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/invitecode cancel")
        },
    )
}
