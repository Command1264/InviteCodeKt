package io.github.command1264.invitecode.platform.modern

import io.github.command1264.invitecode.platform.bukkit.MessageDecoration
import io.github.command1264.invitecode.platform.bukkit.PresentedMessage

private val componentControlCodes = Regex("[\\u0000-\\u0008\\u000B-\\u001F\\u007F]")

/** Builds styled native text components from already parsed presentation segments. */
internal fun <T : Any> buildStyledComponent(
    message: PresentedMessage,
    text: (String) -> T,
    color: (T, Int) -> T,
    decorate: (T, MessageDecoration) -> T,
    append: (T, T) -> T,
): T {
    val root = text("")
    return message.segments.fold(root) { result, segment ->
        val safeText = segment.text.replace('§', '&').replace(componentControlCodes, "")
        var component = text(safeText)
        segment.color?.let { component = color(component, it) }
        segment.decorations.forEach { decoration -> component = decorate(component, decoration) }
        append(result, component)
    }
}
