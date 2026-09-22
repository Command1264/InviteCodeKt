package io.github.command1264.invitecode.plugin

import org.bukkit.entity.Player

internal object BukkitLocaleCompatibility {
    fun locale(player: Player): String? = resolveLocale(player) { player.spigot() }

    internal fun resolveLocale(
        directReceiver: Any,
        legacyReceiver: () -> Any,
    ): String? = readLocale(directReceiver) ?: readLocale(legacyReceiver())

    private fun readLocale(receiver: Any): String? {
        return try {
            val method = receiver.javaClass.methods.firstOrNull {
                it.name == "getLocale" && it.parameterTypes.isEmpty() && it.returnType == String::class.java
            } ?: return null
            (method.invoke(receiver) as? String)?.takeIf(String::isNotBlank)
        } catch (_: ReflectiveOperationException) {
            null
        }
    }
}
