package io.github.command1264.invitecode.plugin

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import io.github.command1264.invitecode.platform.bukkit.MessageSegment
import io.github.command1264.invitecode.platform.bukkit.PresentedMessage

/** Interactive chat affordances isolated from the legacy text and RGB Invite Code formatter. */
internal object PlayerChatActions {
    fun codeMessage(
        message: PresentedMessage,
        code: String,
        copyHint: PresentedMessage,
        selectHint: PresentedMessage,
    ): Array<BaseComponent> {
        // COPY_TO_CLIPBOARD is absent from the Bungee chat API bundled by Spigot 1.8.8.
        val clipboard = enumValues<ClickEvent.Action>().firstOrNull { it.name == "COPY_TO_CLIPBOARD" }
        val action = clipboard ?: ClickEvent.Action.SUGGEST_COMMAND
        val components = BukkitMessageOutput.components(message)
        components.zip(message.segments).forEach { (component, segment) ->
            if (segment.variable == "code") {
                component.clickEvent = ClickEvent(action, code)
                component.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    BukkitMessageOutput.components(if (clipboard != null) copyHint else selectHint))
            }
        }
        return components
    }

    fun confirmation(
        agreeLabel: PresentedMessage,
        disagreeLabel: PresentedMessage,
        agreeHint: PresentedMessage,
        disagreeHint: PresentedMessage,
    ): Array<BaseComponent> = arrayOf(
        button(agreeLabel, agreeHint, "/invitecode confirm", false),
        button(disagreeLabel, disagreeHint, "/invitecode cancel", true),
    )

    private fun button(label: PresentedMessage, hint: PresentedMessage, command: String, separated: Boolean): BaseComponent {
        val bracketColor = if (separated) 0xFF5555 else 0x55FF55
        val content = PresentedMessage(listOf(MessageSegment(if (separated) "  [" else "[", bracketColor)) +
            label.segments + MessageSegment("]", bracketColor))
        return TextComponent(*BukkitMessageOutput.components(content)).apply {
            color = if (separated) ChatColor.RED else ChatColor.GREEN
            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, command)
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, BukkitMessageOutput.components(hint))
        }
    }
}
