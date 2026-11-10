package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.LocalizedMessages
import io.github.command1264.invitecode.platform.bukkit.MessageAudience
import io.github.command1264.invitecode.platform.bukkit.MessageDecoration
import io.github.command1264.invitecode.platform.bukkit.MessagePresenter
import io.github.command1264.invitecode.platform.bukkit.PrefixPlacement
import io.github.command1264.invitecode.platform.bukkit.PresentedMessage
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.ChatColor
import io.github.command1264.invitecode.core.InviteCodeColor
import io.github.command1264.invitecode.core.LegacyInviteCodeColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

internal object BukkitMessageOutput {
    fun send(
        sender: CommandSender,
        messages: LocalizedMessages,
        key: String,
        variables: Map<String, String> = emptyMap(),
        prefix: PrefixPlacement = PrefixPlacement.INLINE,
    ) {
        val presenter = MessagePresenter(messages)
        val audience = audience(sender)
        val locale = (sender as? Player)?.let(BukkitLocaleCompatibility::locale)
        if (sender is Player || prefix == PrefixPlacement.NONE) {
            sendPresented(sender, presenter.render(locale, key, variables, audience, prefix))
        } else {
            sendFormatted(sender, messages, presenter.render(locale, key, variables, audience, PrefixPlacement.NONE), prefix)
        }
    }

    fun sendReport(sender: CommandSender, messages: LocalizedMessages, title: String,
        sections: List<Pair<String, Map<String, String>>>,
        formatter: ((String, Map<String, String>) -> PresentedMessage)? = null) {
        send(sender, messages, title)
        sections.forEach { (key, variables) ->
            val placement = if (sender is Player) PrefixPlacement.NONE else PrefixPlacement.INLINE
            if (formatter == null) send(sender, messages, key, variables, placement)
            else sendFormatted(sender, messages, formatter(key, variables), placement)
        }
    }

    private fun sendFormatted(sender: CommandSender, messages: LocalizedMessages,
        body: PresentedMessage, prefix: PrefixPlacement) {
        if (prefix == PrefixPlacement.NONE) {
            sendPresented(sender, body)
            return
        }
        val heading = MessagePresenter(messages).render((sender as? Player)?.let(BukkitLocaleCompatibility::locale),
            "presentation.prefix", audience = audience(sender), prefix = PrefixPlacement.NONE)
        lines(body).forEach { line ->
            sendPresented(sender, PresentedMessage(if (heading.plainText.isEmpty()) line.segments
                else heading.segments + io.github.command1264.invitecode.platform.bukkit.MessageSegment(" ") + line.segments))
        }
    }

    private fun lines(message: PresentedMessage): List<PresentedMessage> {
        val result = mutableListOf<PresentedMessage>()
        var current = mutableListOf<io.github.command1264.invitecode.platform.bukkit.MessageSegment>()
        message.segments.forEach { segment ->
            segment.text.split(Regex("\\r\\n|\\r|\\n")).forEachIndexed { index, part ->
                if (index > 0) {
                    result.add(PresentedMessage(current.toList()))
                    current = mutableListOf()
                }
                if (part.isNotEmpty()) current.add(segment.copy(text = part))
            }
        }
        result.add(PresentedMessage(current.toList()))
        return result
    }

    fun sendLiteral(sender: CommandSender, messages: LocalizedMessages, text: String, prefix: PrefixPlacement = PrefixPlacement.INLINE) =
        send(sender, messages, "runtime.raw-output", mapOf("text" to text), prefix)

    fun sendPresented(sender: CommandSender, message: PresentedMessage) {
        if (sender is Player) sender.spigot().sendMessage(*components(message))
        else lines(message).forEach { line -> sender.sendMessage(buildString {
            line.segments.forEach { segment ->
                append("§r")
                segment.color?.let { append('§').append(InviteCodeColor.fromRgb(it).nearestLegacyColor().code) }
                append(segment.text.replace('§', '&').replace(CONTROLS, ""))
            }
            append("§r")
        }) }
    }

    fun audience(sender: CommandSender): MessageAudience = if (sender !is Player) MessageAudience.CONSOLE
        else if (rgbColor != null) MessageAudience.RGB_PLAYER else MessageAudience.LEGACY_PLAYER

    fun components(message: PresentedMessage): Array<BaseComponent> = message.segments.map { segment ->
        // Legacy section signs and terminal controls must never be reinterpreted by a downstream renderer.
        TextComponent(segment.text.replace('§', '&').replace(CONTROLS, "")).apply {
            color = segment.color?.let { rgb ->
                LegacyInviteCodeColor.entries.firstOrNull { it.color.rgb == rgb }?.let { ChatColor.getByChar(it.code) }
                    ?: rgbColor?.invoke(null, "#%06X".format(rgb)) as? ChatColor
                    ?: ChatColor.getByChar(InviteCodeColor.fromRgb(rgb).nearestLegacyColor().code)
            } ?: ChatColor.WHITE
            setBold(MessageDecoration.BOLD in segment.decorations)
            setItalic(MessageDecoration.ITALIC in segment.decorations)
            setUnderlined(MessageDecoration.UNDERLINE in segment.decorations)
            setStrikethrough(MessageDecoration.STRIKETHROUGH in segment.decorations)
            setObfuscated(MessageDecoration.OBFUSCATED in segment.decorations)
        }
    }.toTypedArray()

    private val rgbColor = try { ChatColor::class.java.getMethod("of", String::class.java) }
        catch (_: NoSuchMethodException) { null }
    private val CONTROLS = Regex("[\\u0000-\\u0008\\u000B-\\u001F\\u007F]")
}
