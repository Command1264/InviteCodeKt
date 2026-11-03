package io.github.command1264.invitecode.plugin

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.logging.Logger

internal data class BuildProvenance(
    val commit: String? = null,
    val dirty: Boolean? = null,
    val builtAt: Instant? = null,
) {
    fun doctorValues(): Map<String, String> = mapOf(
        "commit" to (commit?.take(12) ?: "unknown"),
        "dirty" to (dirty?.toString() ?: "unknown"),
        "built_at" to (builtAt?.toString() ?: "unknown"),
    )

    companion object {
        private val gitHash = Regex("[0-9a-f]{40}|[0-9a-f]{64}")

        fun read(jar: Path, logger: Logger): BuildProvenance {
            if (!Files.isRegularFile(jar)) return BuildProvenance()
            return try {
                JarFile(jar.toFile()).use { fromAttributes(it.manifest?.mainAttributes) }
            } catch (error: IOException) {
                logger.warning("Cannot read InviteCode build metadata: ${error.javaClass.simpleName}")
                BuildProvenance()
            }
        }

        fun fromAttributes(attributes: Attributes?): BuildProvenance {
            val builtAt = attributes?.getValue("InviteCode-Built-At")?.let {
                try {
                    Instant.parse(it)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
            return BuildProvenance(
                attributes?.getValue("InviteCode-Git-Commit")?.takeIf(gitHash::matches),
                attributes?.getValue("InviteCode-Git-Dirty")?.toBooleanStrictOrNull(),
                builtAt,
            )
        }
    }
}
