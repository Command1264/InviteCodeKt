package io.github.command1264.invitecode.platform.bukkit

import io.github.command1264.invitecode.core.LegacyInviteCodeColor
import io.github.command1264.invitecode.core.InviteCodeColor
import kotlin.math.pow

enum class MessageAudience { LEGACY_PLAYER, RGB_PLAYER, CONSOLE, PLAIN }
enum class PrefixPlacement { INLINE, OWN_LINE, NONE }
enum class MessageDecoration { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, OBFUSCATED }

data class MessageSegment(
    val text: String,
    val color: Int? = null,
    val decorations: Set<MessageDecoration> = emptySet(),
)

data class PresentedMessage(val segments: List<MessageSegment>) {
    val plainText: String get() = segments.joinToString("") { it.text }
}

/** Presentation boundary. Callers keep click payloads separate from displayed text. */
class MessagePresenter(private val messages: LocalizedMessages) {
    fun render(
        locale: String?,
        key: String,
        variables: Map<String, String> = emptyMap(),
        audience: MessageAudience = MessageAudience.LEGACY_PLAYER,
        prefix: PrefixPlacement = PrefixPlacement.INLINE,
    ): PresentedMessage {
        val body = parse(messages.resolve(locale, key), variables)
        val heading = if (prefix == PrefixPlacement.NONE) emptyList() else {
            parse(messages.resolve(locale, "presentation.prefix"), emptyMap())
        }
        val segments = if (heading.isEmpty()) body else {
            heading + MessageSegment(if (prefix == PrefixPlacement.INLINE) " " else "\n") + body
        }
        return PresentedMessage(segments.map { segment ->
            when (audience) {
                MessageAudience.RGB_PLAYER -> segment
                MessageAudience.LEGACY_PLAYER -> segment.copy(color = segment.color?.let {
                    InviteCodeColor.fromRgb(it).nearestLegacyColor().color.rgb
                })
                MessageAudience.CONSOLE -> segment.copy(
                    color = segment.color?.let(::consoleColor), decorations = emptySet(),
                )
                MessageAudience.PLAIN -> segment.copy(color = null, decorations = emptySet())
            }
        })
    }

    private fun parse(template: String, variables: Map<String, String>): List<MessageSegment> {
        require(variables.keys.all { it.matches(Regex("[a-z][a-z0-9_]*")) }) {
            "Message variable names must use lowercase snake_case."
        }
        val result = mutableListOf<MessageSegment>()
        var color: Int? = null
        var decorations = emptySet<MessageDecoration>()
        var offset = 0
        fun append(text: String) {
            if (text.isNotEmpty()) result.add(MessageSegment(text, color, decorations))
        }
        TOKEN.findAll(template).forEach { match ->
            append(template.substring(offset, match.range.first))
            val hex = match.groupValues[1]
            val code = match.groupValues[2].firstOrNull()?.lowercaseChar()
            when {
                hex.isNotEmpty() -> {
                    color = hex.toInt(16)
                    decorations = emptySet()
                }
                code == 'r' -> {
                    color = null
                    decorations = emptySet()
                }
                code in DECORATIONS -> decorations = decorations + DECORATIONS.getValue(requireNotNull(code))
                code != null -> {
                    color = LegacyInviteCodeColor.entries.first { it.code == code }.color.rgb
                    decorations = emptySet()
                }
                else -> append(variables[match.groupValues[3]] ?: match.value)
            }
            offset = match.range.last + 1
        }
        append(template.substring(offset))
        return result.toList()
    }

    private fun consoleColor(rgb: Int): Int {
        val nearest = InviteCodeColor.fromRgb(rgb).nearestLegacyColor()
        if (nearest in CONSOLE_COLORS) return nearest.color.rgb
        // Avoid turning near-black neutral text into a dark/colored invisible label.
        if (nearest == LegacyInviteCodeColor.BLACK || nearest == LegacyInviteCodeColor.DARK_GRAY) {
            return LegacyInviteCodeColor.GRAY.color.rgb
        }
        val desired = InviteCodeColor.fromRgb(rgb)
        return CONSOLE_COLORS.minByOrNull { legacy ->
            val red = desired.red - legacy.color.red
            val green = desired.green - legacy.color.green
            val blue = desired.blue - legacy.color.blue
            red * red + green * green + blue * blue
        }!!.color.rgb
    }

    private companion object {
        val TOKEN = Regex("&#([0-9a-fA-F]{6})|[&§]([0-9a-fk-orA-FK-OR])|\\{([a-z][a-z0-9_]*)}")
        val DECORATIONS = mapOf(
            'k' to MessageDecoration.OBFUSCATED, 'l' to MessageDecoration.BOLD,
            'm' to MessageDecoration.STRIKETHROUGH, 'n' to MessageDecoration.UNDERLINE,
            'o' to MessageDecoration.ITALIC,
        )
        // Minimum 4.5:1 contrast against standard black, not a claim about custom terminal themes.
        val CONSOLE_COLORS = LegacyInviteCodeColor.entries.filter { legacy ->
            fun linear(channel: Int): Double {
                val value = channel / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            val color = legacy.color
            val luminance = 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
            (luminance + 0.05) / 0.05 >= 4.5
        }
    }
}
