package io.github.command1264.invitecode.platform.bukkit

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class LocalizedMessages(
    private val defaultLocale: String,
    private val useClientLocale: Boolean,
    catalogs: Map<String, Map<String, String>>,
    private val onMissingKey: (String) -> Unit = {},
    bundledCatalogs: Map<String, Map<String, String>> = emptyMap(),
) {
    private val catalogs = catalogs.mapKeys { normalizeLocale(it.key) }.mapValues { it.value.toMap() }
    private val bundledCatalogs = bundledCatalogs.mapKeys { normalizeLocale(it.key) }.mapValues { it.value.toMap() }
    private val reportedFallbacks = ConcurrentHashMap.newKeySet<String>()

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
        val fallbackLocale = if (useClientLocale && clientLocale != null) normalizeLocale(clientLocale) else null
        val fallback = fallbackLocale?.let { bundledCatalogs[it]?.get(key) }
            ?: bundledCatalogs[normalizeLocale(defaultLocale)]?.get(key)
        if (fallback != null) {
            // Catalogs are immutable snapshots; report once per lookup locale and key.
            if (reportedFallbacks.add("$fallbackLocale:$key")) onMissingKey(key)
            return fallback
        }
        onMissingKey(key)
        return "[missing: $key]"
    }

    fun resolve(clientLocale: String?, key: String, variables: Map<String, String>): String {
        require(variables.keys.all(VARIABLE_NAME::matches)) { "Message variable names must use lowercase snake_case." }
        // Match only the template: a replacement may itself contain another placeholder.
        return PLACEHOLDER.replace(resolve(clientLocale, key)) { match ->
            variables[match.groupValues[1]] ?: match.value
        }
    }

    private fun normalizeLocale(raw: String): String {
        val parts = raw.replace('-', '_').split('_', limit = 2)
        if (parts.size != 2) return raw
        return parts[0].lowercase(Locale.ROOT) + "_" + parts[1].uppercase(Locale.ROOT)
    }

    private companion object {
        val VARIABLE_NAME = Regex("[a-z][a-z0-9_]*")
        val PLACEHOLDER = Regex("\\{([a-z][a-z0-9_]*)}")
    }
}
