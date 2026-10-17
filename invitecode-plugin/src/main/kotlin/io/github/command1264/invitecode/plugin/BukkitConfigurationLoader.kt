package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.InviteCodeRuntimeConfiguration
import io.github.command1264.invitecode.platform.bukkit.RuntimeConfigurationAssembler
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

class BukkitConfigurationLoader(
    private val dataDirectory: Path,
    private val onMissingMessage: (String) -> Unit,
) {
    fun load(): InviteCodeRuntimeConfiguration {
        BundledConfigurationInstaller(dataDirectory, ::openBundledResource).installMissing()
        return RuntimeConfigurationAssembler.parse(
            general = loadDocument(dataDirectory.resolve("config.yml")),
            storage = loadDocument(dataDirectory.resolve("storage.yml")),
            qualifications = loadDocument(dataDirectory.resolve("qualifications.yml")),
            rewards = loadDocument(dataDirectory.resolve("rewards.yml")),
            messageCatalogs = loadMessageCatalogs(),
            onMissingMessage = onMissingMessage,
            bundledMessageCatalogs = loadBundledMessageCatalogs(),
        )
    }

    private fun openBundledResource(resourcePath: String): InputStream? =
        javaClass.classLoader.getResourceAsStream(resourcePath)

    private fun loadMessageCatalogs(): Map<String, Map<String, Any?>> {
        val messagesDirectory = dataDirectory.resolve("messages")
        require(Files.isDirectory(messagesDirectory)) {
            "Message catalog directory does not exist: ${messagesDirectory.toAbsolutePath()}."
        }
        Files.newDirectoryStream(messagesDirectory, "*.yml").use { files ->
            return files.associate { file ->
                file.nameWithoutExtension to restoreBooleanLikeMessageKeys(loadDocument(file))
            }
        }
    }

    private fun loadBundledMessageCatalogs(): Map<String, Map<String, Any?>> =
        BUNDLED_MESSAGE_CATALOGS.mapValues { (_, resourcePath) ->
            val input = openBundledResource(resourcePath)
                ?: throw IllegalArgumentException("Bundled message catalog resource does not exist: $resourcePath")
            input.use { resource -> parseBundledMessageCatalog(resource, resourcePath) }
        }

    private fun parseBundledMessageCatalog(input: InputStream, resourcePath: String): Map<String, Any?> {
        val yaml = YamlConfiguration()
        try {
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader -> yaml.load(reader) }
        } catch (error: InvalidConfigurationException) {
            throw IllegalArgumentException("Bundled message catalog has invalid YAML: $resourcePath", error)
        }
        return restoreBooleanLikeMessageKeys(
            yaml.getValues(false).mapValues { (_, value) -> normalize(value) },
        )
    }

    private fun restoreBooleanLikeMessageKeys(document: Map<String, Any?>): Map<String, Any?> {
        val values = document["value"] as? Map<*, *> ?: return document
        if ("yes" in values || "no" in values || "true" !in values || "false" !in values) return document

        val restoredValues = values.entries.associate { (key, value) ->
            when (key) {
                "true" -> "yes" to value
                "false" -> "no" to value
                else -> key to value
            }
        }
        return document + ("value" to restoredValues)
    }

    private fun loadDocument(path: Path): Map<String, Any?> {
        require(Files.isRegularFile(path)) { "Configuration file does not exist: ${path.toAbsolutePath()}." }
        val yaml = YamlConfiguration()
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
            try {
                yaml.load(reader)
            } catch (error: InvalidConfigurationException) {
                if (path.fileName.toString() == "storage.yml") {
                    throw IllegalArgumentException(
                        "storage.yml has invalid YAML; check its syntax without sharing the file because it may contain credentials.",
                    )
                }
                throw error
            }
        }
        return yaml.getValues(false).mapValues { (_, value) -> normalize(value) }
    }

    private fun normalize(value: Any?): Any? = when (value) {
        is ConfigurationSection -> value.getValues(false).mapValues { (_, child) -> normalize(child) }
        is Map<*, *> -> value.entries.associate { (key, child) ->
            require(key is String) { "YAML configuration keys must be strings." }
            key to normalize(child)
        }
        is List<*> -> value.map(::normalize)
        else -> value
    }

    companion object {
        private val BUNDLED_MESSAGE_CATALOGS = mapOf(
            "zh_TW" to "config/messages/zh_TW.yml",
            "en_US" to "config/messages/en_US.yml",
        )
    }
}
