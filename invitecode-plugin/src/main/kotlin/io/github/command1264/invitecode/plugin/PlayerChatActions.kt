package io.github.command1264.invitecode.plugin

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent

/** Interactive chat affordances isolated from the legacy text and RGB Invite Code formatter. */
internal object PlayerChatActions {
    fun codeMessage(
        template: String,
        displayedCode: String,
        code: String,
        copyHint: String,
        selectHint: String,
    ): Array<BaseComponent> {
        // COPY_TO_CLIPBOARD is absent from the Bungee chat API bundled by Spigot 1.8.8.
        val clipboard = enumValues<ClickEvent.Action>().firstOrNull { it.name == "COPY_TO_CLIPBOARD" }
        val action = clipboard ?: ClickEvent.Action.SUGGEST_COMMAND
        val marker = "{code}"
        val before = if (marker in template) template.substringBefore(marker) else template + " "
        val after = if (marker in template) template.substringAfter(marker) else ""
        val codeComponents = TextComponent.fromLegacyText(displayedCode)
        codeComponents.forEach { component ->
            component.clickEvent = ClickEvent(action, code)
            component.hoverEvent = HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                arrayOf(TextComponent(if (clipboard != null) copyHint else selectHint)),
            )
        }
        return TextComponent.fromLegacyText(before) + codeComponents + TextComponent.fromLegacyText(after)
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
