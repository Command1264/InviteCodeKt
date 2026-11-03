package io.github.command1264.invitecode.plugin

import java.io.InputStream
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class BundledConfigurationInstaller(
    private val dataDirectory: Path,
    private val openResource: (String) -> InputStream?,
) {
    fun installMissing() {
        RESOURCES.forEach { (resourcePath, outputPath) ->
            val target = dataDirectory.resolve(outputPath)
            if (Files.notExists(target)) {
                val parent = requireNotNull(target.parent)
                try {
                    Files.createDirectories(parent)
                } catch (error: IOException) {
                    throw IOException(
                        "Could not create directory '$parent' for bundled resource '$resourcePath'.",
                        error,
                    )
                }
                val resource = requireNotNull(openResource(resourcePath)) {
                    "Bundled resource does not exist: $resourcePath"
                }
                var temporary: Path? = null
                var installFailure: IOException? = null
                try {
                    resource.use { input ->
                        val staged = Files.createTempFile(parent, ".invitecode-config-", ".tmp")
                        temporary = staged
                        Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING)
                    }
                    try {
                        Files.move(requireNotNull(temporary), target)
                    } catch (_: FileAlreadyExistsException) {
                        if (!Files.isRegularFile(target)) {
                            throw IOException("Cannot publish bundled resource '$resourcePath' to '$target'.")
                        }
                    }
                } catch (error: IOException) {
                    val contextual = IOException(
                        "Could not install bundled resource '$resourcePath' at '$target'.",
                        error,
                    )
                    installFailure = contextual
                    throw contextual
                } finally {
                    temporary?.let { staged ->
                        try {
                            Files.deleteIfExists(staged)
                        } catch (error: IOException) {
                            val previous = installFailure
                            if (previous != null) {
                                previous.addSuppressed(error)
                            } else {
                                throw IOException("Could not remove temporary configuration file '$staged'.", error)
                            }
                        }
                    }
                }
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
