package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.LocalizedMessages
import io.github.command1264.invitecode.platform.bukkit.MessageAudience
import io.github.command1264.invitecode.platform.bukkit.MessagePresenter
import io.github.command1264.invitecode.platform.bukkit.PrefixPlacement
import io.github.command1264.invitecode.platform.bukkit.PresentedMessage
import java.util.Locale

/** Trusted catalog fragments retain styling; runtime values remain literal diagnostic data. */
internal class DoctorMessageLayouts(
    messages: LocalizedMessages,
    private val locale: String?,
    private val audience: MessageAudience,
) {
    private val presenter = MessagePresenter(messages)

    fun render(key: String, values: Map<String, String>): PresentedMessage {
        val body = presenter.render(locale, key, values, audience, PrefixPlacement.NONE)
        val fragments = values.mapNotNull { (name, value) ->
            val suffix = when (name) {
                "phase" -> known("phase", value, setOf("ACTIVE", "STARTING", "MAINTENANCE", "STOPPING", "STOPPED"))
                "executor" -> known("executor", value, setOf("ACCEPTING", "STOPPED"))
                "placeholder" -> known("enabled", value, setOf("true", "false"))
                "vault" -> known("available", value, setOf("true", "false"))
                "rgb" -> known("supported", value, setOf("true", "false"))
                "dialog" -> known("dialog", value, setOf("PAPER", "NONE"))
                "backend" -> known("backend", value, setOf("SQLITE", "MYSQL", "JSON"))
                "pending", "processing", "uncertain" -> {
                    val count = value.toLongOrNull()
                    when {
                        count == 0L -> "count.zero"
                        count != null && count > 0 -> "count.$name"
                        else -> "unknown"
                    }
                }
                else -> null
            }
            suffix?.let { name to presenter.render(locale, "admin.doctor-value.$it",
                mapOf("value" to value), audience, PrefixPlacement.NONE) }
        }.toMap()
        return PresentedMessage(body.segments.flatMap { segment ->
            fragments[segment.variable]?.segments?.map { it.copy(variable = null) } ?: listOf(segment)
        })
    }

    private fun known(group: String, value: String, allowed: Set<String>): String =
        if (value in allowed) "$group.${value.lowercase(Locale.ROOT)}" else "unknown"
}
