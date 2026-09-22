package io.github.command1264.invitecode.platform.bukkit

import io.github.command1264.invitecode.core.ColorInviteCodeGenerator
import io.github.command1264.invitecode.core.CrockfordInviteCodeGenerator
import io.github.command1264.invitecode.core.InviteCodeFormat
import io.github.command1264.invitecode.core.InviteCodeFormats
import io.github.command1264.invitecode.core.InviteCodeGenerator
import io.github.command1264.invitecode.core.MatchingNetworkAction
import java.time.Duration
import java.time.ZoneId
import java.util.Locale

enum class InviteCodeRuntimeFormat {
    HEX_COLOR,
    CROCKFORD_BASE32,
}

fun InviteCodeRuntimeFormat.parser(): InviteCodeFormat = when (this) {
    InviteCodeRuntimeFormat.HEX_COLOR -> InviteCodeFormats.colorHex
    InviteCodeRuntimeFormat.CROCKFORD_BASE32 -> InviteCodeFormats.crockfordBase32(8)
}

fun InviteCodeRuntimeFormat.generator(): InviteCodeGenerator = when (this) {
    InviteCodeRuntimeFormat.HEX_COLOR -> ColorInviteCodeGenerator()
    InviteCodeRuntimeFormat.CROCKFORD_BASE32 -> CrockfordInviteCodeGenerator()
}

enum class ExistingPlayerPolicy {
    START_WINDOW,
    EXPIRED,
}

data class LanguageConfiguration(
    val defaultLocale: String,
    val useClientLocale: Boolean,
)

data class InviteCodeConfiguration(
    val format: InviteCodeRuntimeFormat,
    val customCodesEnabled: Boolean,
)

data class ClaimWorkflowConfiguration(
    val window: Duration,
    val inputMode: InputMode,
    val confirmationTimeout: Duration,
    val chatInputTimeout: Duration,
    val existingPlayerPolicy: ExistingPlayerPolicy,
)

data class AntiAbuseConfiguration(
    val matchingIpAction: MatchingNetworkAction,
    val ipHashRetention: Duration,
)

data class BukkitPluginConfiguration(
    val version: Int,
    val language: LanguageConfiguration,
    val inviteCode: InviteCodeConfiguration,
    val claim: ClaimWorkflowConfiguration,
    val antiAbuse: AntiAbuseConfiguration,
    val timeZone: ZoneId,
)

object BukkitPluginConfigurationParser {
    const val CURRENT_VERSION = 1

    fun parse(value: Map<String, Any?>): BukkitPluginConfiguration {
        require(value.keys == ROOT_KEYS) {
            "General configuration must contain only ${ROOT_KEYS.sorted().joinToString()} ."
        }
        val version = value.requiredInteger("config-version", "General configuration")
        require(version == CURRENT_VERSION) {
            "General config-version $version is unsupported; expected $CURRENT_VERSION."
        }
        val language = value.requiredMap("language", "General configuration")
        requireKeys(language, LANGUAGE_KEYS, "language")
        val inviteCode = value.requiredMap("invite-code", "General configuration")
        requireKeys(inviteCode, INVITE_CODE_KEYS, "invite-code")
        val claim = value.requiredMap("claim", "General configuration")
        requireKeys(claim, CLAIM_KEYS, "claim")
        val antiAbuse = value.requiredMap("anti-abuse", "General configuration")
        requireKeys(antiAbuse, ANTI_ABUSE_KEYS, "anti-abuse")

        val inputMode = claim.requiredEnum<InputMode>("input-mode", "claim")
        return BukkitPluginConfiguration(
            version = version,
            language = LanguageConfiguration(
                normalizeLocale(language.requiredString("default", "language")),
                language.requiredBoolean("use-client-locale", "language"),
            ),
            inviteCode = InviteCodeConfiguration(
                inviteCode.requiredEnum("format", "invite-code"),
                inviteCode.requiredBoolean("custom-codes-enabled", "invite-code"),
            ),
            claim = ClaimWorkflowConfiguration(
                parsePositiveDuration(claim.requiredString("window", "claim"), "claim.window"),
                inputMode,
                parsePositiveDuration(
                    claim.requiredString("confirmation-timeout", "claim"),
                    "claim.confirmation-timeout",
                ),
                parsePositiveDuration(
                    claim.requiredString("chat-input-timeout", "claim"),
                    "claim.chat-input-timeout",
                ),
                claim.requiredEnum("existing-player-policy", "claim"),
            ),
            antiAbuse = AntiAbuseConfiguration(
                antiAbuse.requiredEnum("matching-ip-action", "anti-abuse"),
                parsePositiveDuration(
                    antiAbuse.requiredString("ip-hash-retention", "anti-abuse"),
                    "anti-abuse.ip-hash-retention",
                ),
            ),
            timeZone = try {
                ZoneId.of(value.requiredString("time-zone", "General configuration"))
            } catch (error: Exception) {
                throw IllegalArgumentException("General configuration time-zone is invalid.", error)
            },
        )
    }

    private fun parsePositiveDuration(raw: String, location: String): Duration {
        val duration = SHORT_DURATION.matchEntire(raw)?.let { match ->
            val amount = match.groupValues[1].toLongOrNull()
                ?: throw IllegalArgumentException("$location duration is outside the supported range.")
            when (match.groupValues[2]) {
                "ms" -> Duration.ofMillis(amount)
                "s" -> Duration.ofSeconds(amount)
                "m" -> Duration.ofMinutes(amount)
                "h" -> Duration.ofHours(amount)
                "d" -> Duration.ofDays(amount)
                else -> error("Unreachable duration unit")
            }
        } ?: try {
            Duration.parse(raw)
        } catch (error: Exception) {
            throw IllegalArgumentException("$location must be a duration such as 30s, 24h, or PT30S.", error)
        }
        require(!duration.isZero && !duration.isNegative) { "$location must be greater than zero." }
        return duration
    }

    private fun normalizeLocale(raw: String): String {
        val match = LOCALE.matchEntire(raw)
            ?: throw IllegalArgumentException("Locale '$raw' must use language_COUNTRY format.")
        return match.groupValues[1].lowercase(Locale.ROOT) + "_" +
            match.groupValues[2].uppercase(Locale.ROOT)
    }

    private fun requireKeys(value: Map<String, Any?>, keys: Set<String>, location: String) {
        require(value.keys == keys) { "$location must contain only ${keys.sorted().joinToString()} ." }
    }

    private fun Map<String, Any?>.requiredMap(name: String, location: String): Map<String, Any?> {
        val value = this[name]
        require(value is Map<*, *> && value.keys.all { it is String }) {
            "$location requires map $name with string keys."
        }
        @Suppress("UNCHECKED_CAST")
        return value as Map<String, Any?>
    }

    private fun Map<String, Any?>.requiredString(name: String, location: String): String {
        val value = this[name]
        require(value is String && value.isNotBlank()) { "$location requires non-blank string $name." }
        return value
    }

    private fun Map<String, Any?>.requiredBoolean(name: String, location: String): Boolean =
        this[name] as? Boolean
            ?: throw IllegalArgumentException("$location requires boolean $name.")

    private fun Map<String, Any?>.requiredInteger(name: String, location: String): Int =
        (this[name] as? Number)?.toString()?.toIntOrNull()
            ?: throw IllegalArgumentException("$location requires integer $name.")

    private inline fun <reified T : Enum<T>> Map<String, Any?>.requiredEnum(
        name: String,
        location: String,
    ): T {
        val raw = requiredString(name, location)
        return try {
            enumValueOf(raw.uppercase(Locale.ROOT))
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("$location has unsupported $name '$raw'.", error)
        }
    }

    private val ROOT_KEYS = setOf(
        "config-version",
        "language",
        "invite-code",
        "claim",
        "anti-abuse",
        "time-zone",
    )
    private val LANGUAGE_KEYS = setOf("default", "use-client-locale")
    private val INVITE_CODE_KEYS = setOf("format", "custom-codes-enabled")
    private val CLAIM_KEYS = setOf(
        "window",
        "input-mode",
        "confirmation-timeout",
        "chat-input-timeout",
        "existing-player-policy",
    )
    private val ANTI_ABUSE_KEYS = setOf("matching-ip-action", "ip-hash-retention")
    private val SHORT_DURATION = Regex("^(\\d+)(ms|s|m|h|d)$")
    private val LOCALE = Regex("^([A-Za-z]{2,3})[_-]([A-Za-z]{2})$")
}
