package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.QualificationConditionContext
import io.github.command1264.invitecode.api.QualificationConditionOutcome
import io.github.command1264.invitecode.api.QualificationConditionProvider
import java.math.BigDecimal
import java.time.Duration
import java.util.Locale
import java.util.UUID

fun interface PermissionQualificationLookup {
    /** Returns null when the platform permission integration is unavailable. */
    fun hasPermission(playerId: UUID, permission: String): Boolean?
}

fun interface PlaceholderQualificationLookup {
    /** Returns null when the placeholder or its integration is unavailable. */
    fun resolve(playerId: UUID, placeholder: String): String?
}

object BuiltInQualificationProviders {
    fun create(
        permissionLookup: PermissionQualificationLookup? = null,
        placeholderLookup: PlaceholderQualificationLookup? = null,
    ): List<QualificationConditionProvider> = buildList {
        add(provider("PLAY_TIME") { context, parameters ->
            compareMinimum(context.playTime, parameters.requiredDuration("at-least"))
        })
        add(provider("ELAPSED_TIME") { context, parameters ->
            compareMinimum(
                Duration.between(context.claimedAt, context.evaluatedAt),
                parameters.requiredDuration("at-least"),
            )
        })
        add(provider("LOGIN_DAYS") { context, parameters ->
            val actual = context.loginDays ?: return@provider QualificationConditionOutcome.UNKNOWN
            outcome(actual >= parameters.requiredNonNegativeInt("at-least"))
        })
        add(provider("ADMIN_APPROVAL") { context, _ ->
            context.adminApproved?.let(::outcome) ?: QualificationConditionOutcome.UNKNOWN
        })
        permissionLookup?.let { lookup ->
            add(provider("PERMISSION") { context, parameters ->
                val permission = parameters.required("permission")
                lookup.hasPermission(context.inviteeId, permission)?.let(::outcome)
                    ?: QualificationConditionOutcome.UNKNOWN
            })
        }
        placeholderLookup?.let { lookup ->
            add(provider("PLACEHOLDER") { context, parameters ->
                val placeholder = parameters.required("placeholder")
                val actual = lookup.resolve(context.inviteeId, placeholder)
                    ?: return@provider QualificationConditionOutcome.UNKNOWN
                comparePlaceholder(
                    actual = actual,
                    expected = parameters.required("value"),
                    operator = parameters.required("operator"),
                )
            })
        }
    }

    private fun provider(
        type: String,
        evaluator: (QualificationConditionContext, Map<String, String>) -> QualificationConditionOutcome,
    ): QualificationConditionProvider = object : QualificationConditionProvider {
        override val type = type

        override fun evaluate(
            context: QualificationConditionContext,
            parameters: Map<String, String>,
        ): QualificationConditionOutcome = evaluator(context, parameters)
    }

    private fun compareMinimum(actual: Duration?, expected: Duration): QualificationConditionOutcome =
        actual?.let { outcome(it >= expected) } ?: QualificationConditionOutcome.UNKNOWN

    private fun comparePlaceholder(
        actual: String,
        expected: String,
        operator: String,
    ): QualificationConditionOutcome = when (operator.uppercase(Locale.ROOT)) {
        "EQUALS" -> outcome(actual == expected)
        "NOT_EQUALS" -> outcome(actual != expected)
        "CONTAINS" -> outcome(actual.contains(expected))
        "GREATER_THAN" -> outcome(actual.decimal() > expected.decimal())
        "GREATER_THAN_OR_EQUAL" -> outcome(actual.decimal() >= expected.decimal())
        "LESS_THAN" -> outcome(actual.decimal() < expected.decimal())
        "LESS_THAN_OR_EQUAL" -> outcome(actual.decimal() <= expected.decimal())
        else -> throw IllegalArgumentException(
            "Unsupported Placeholder comparison '$operator'.",
        )
    }

    private fun String.decimal(): BigDecimal = try {
        BigDecimal(this)
    } catch (error: NumberFormatException) {
        throw IllegalArgumentException("Placeholder numeric comparison requires numeric values.", error)
    }

    private fun Map<String, String>.required(name: String): String =
        this[name]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Qualification Condition requires non-blank '$name'.")

    private fun Map<String, String>.requiredDuration(name: String): Duration {
        val value = required(name)
        if (value.startsWith("P", ignoreCase = true)) {
            val duration = try {
                Duration.parse(value.uppercase(Locale.ROOT))
            } catch (error: Exception) {
                throw IllegalArgumentException("Qualification duration '$value' is invalid.", error)
            }
            require(!duration.isNegative) { "Qualification duration '$value' must not be negative." }
            return duration
        }
        val match = COMPACT_DURATION.matchEntire(value.lowercase(Locale.ROOT))
            ?: throw IllegalArgumentException(
                "Qualification duration '$value' must use ms, s, m, h, d, or ISO-8601 syntax.",
            )
        val amount = match.groupValues[1].toLong()
        return when (match.groupValues[2]) {
            "ms" -> Duration.ofMillis(amount)
            "s" -> Duration.ofSeconds(amount)
            "m" -> Duration.ofMinutes(amount)
            "h" -> Duration.ofHours(amount)
            "d" -> Duration.ofDays(amount)
            else -> error("Unsupported compact duration unit.")
        }
    }

    private fun Map<String, String>.requiredNonNegativeInt(name: String): Int {
        val value = required(name).toIntOrNull()
            ?: throw IllegalArgumentException("Qualification Condition '$name' must be an integer.")
        require(value >= 0) { "Qualification Condition '$name' must not be negative." }
        return value
    }

    private fun outcome(value: Boolean): QualificationConditionOutcome =
        if (value) QualificationConditionOutcome.SATISFIED else QualificationConditionOutcome.UNSATISFIED

    private val COMPACT_DURATION = Regex("^(\\d+)(ms|s|m|h|d)$")
}
