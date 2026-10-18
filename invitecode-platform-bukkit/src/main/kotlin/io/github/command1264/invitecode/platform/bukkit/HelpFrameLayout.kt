package io.github.command1264.invitecode.platform.bukkit

import kotlin.math.roundToInt

/** Approximate default-font alignment; resource packs and client font settings can differ. */
object HelpFrameLayout {
    fun align(header: PresentedMessage, divider: PresentedMessage, audience: MessageAudience = MessageAudience.RGB_PLAYER): Pair<PresentedMessage, PresentedMessage> {
        val console = audience == MessageAudience.CONSOLE
        val top = width(header, console)
        val bottom = width(divider, console)
        val target = maxOf(top, bottom)
        return extend(header, target - top, console) to extend(divider, target - bottom, console)
    }

    private fun extend(message: PresentedMessage, missing: Int, console: Boolean): PresentedMessage {
        val tail = message.segments.lastOrNull()
        val strokeWidth = if (console) 1.0 else if (tail != null && MessageDecoration.BOLD in tail.decorations) 9.0 else 8.0
        val count = (missing / strokeWidth).roundToInt().coerceIn(0, 256)
        if (count == 0) return message
        return PresentedMessage(message.segments + MessageSegment("─".repeat(count), tail?.color,
            tail?.decorations ?: emptySet()))
    }

    private fun width(message: PresentedMessage, console: Boolean): Int = message.segments.sumOf { segment ->
        segment.text.codePoints().toArray().sumOf { point ->
            val size = when {
                console -> if (point >= 0x2E80) 2 else 1
                point == 32 -> 4
                point < 128 && point.toChar() in "!.,:;|'i" -> 2
                point < 128 && point.toChar() in "[]It" -> 4
                point < 128 && point.toChar() in "fl{}" -> 5
                point == 0x2500 || point >= 0x2E80 -> 8
                else -> 6
            }
            size + if (!console && point != 32 && MessageDecoration.BOLD in segment.decorations) 1 else 0
        }
    }
}
