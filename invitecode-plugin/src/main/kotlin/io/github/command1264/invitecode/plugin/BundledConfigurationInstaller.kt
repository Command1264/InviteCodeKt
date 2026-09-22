package io.github.command1264.invitecode.plugin

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal class BundledConfigurationInstaller(
    private val dataDirectory: Path,
    private val openResource: (String) -> InputStream?,
) {
    fun installMissing() {
        RESOURCES.forEach { (resourcePath, outputPath) ->
            val target = dataDirectory.resolve(outputPath)
            if (Files.notExists(target)) {
                Files.createDirectories(requireNotNull(target.parent))
                val resource = requireNotNull(openResource(resourcePath)) {
                    "Bundled resource does not exist: $resourcePath"
                }
                resource.use { Files.copy(it, target) }
            }
        }
    }

    companion object {
        private val RESOURCES = mapOf(
            "config/config.yml" to "config.yml",
            "config/storage.yml" to "storage.yml",
            "config/qualifications.yml" to "qualifications.yml",
            "config/rewards.yml" to "rewards.yml",
            "config/messages/zh_TW.yml" to "messages/zh_TW.yml",
            "config/messages/en_US.yml" to "messages/en_US.yml",
        )
    }
}
