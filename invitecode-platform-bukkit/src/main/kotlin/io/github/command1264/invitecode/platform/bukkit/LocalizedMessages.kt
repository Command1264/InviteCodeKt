package io.github.command1264.invitecode.platform.bukkit

import java.util.Locale

class LocalizedMessages(
    private val defaultLocale: String,
    private val useClientLocale: Boolean,
    catalogs: Map<String, Map<String, String>>,
    private val onMissingKey: (String) -> Unit = {},
) {
    private val catalogs = catalogs.mapKeys { normalizeLocale(it.key) }

    init {
        require(this.catalogs.containsKey(normalizeLocale(defaultLocale))) {
            "Default locale $defaultLocale does not have a message catalog."
        }
    }

    fun resolve(clientLocale: String?, key: String): String {
        require(key.isNotBlank()) { "Message key must not be blank." }
        val preferred = if (useClientLocale && clientLocale != null) {
            catalogs[normalizeLocale(clientLocale)]
        } else {
            null
        }
        preferred?.get(key)?.let { return it }
        catalogs.getValue(normalizeLocale(defaultLocale))[key]?.let { return it }
        onMissingKey(key)
        return "[missing: $key]"
    }

    fun resolve(clientLocale: String?, key: String, variables: Map<String, String>): String {
        require(variables.keys.all(VARIABLE_NAME::matches)) { "Message variable names must use lowercase snake_case." }
        return variables.entries.fold(resolve(clientLocale, key)) { message, (name, value) ->
            message.replace("{$name}", value)
        }
    }

    private fun normalizeLocale(raw: String): String {
        val parts = raw.replace('-', '_').split('_', limit = 2)
        if (parts.size != 2) return raw
        return parts[0].lowercase(Locale.ROOT) + "_" + parts[1].uppercase(Locale.ROOT)
    }

    private companion object {
        val VARIABLE_NAME = Regex("[a-z][a-z0-9_]*")
    }
}
