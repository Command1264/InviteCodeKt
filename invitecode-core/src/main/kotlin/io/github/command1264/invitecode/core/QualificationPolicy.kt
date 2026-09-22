package io.github.command1264.invitecode.core

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

enum class MissingQualificationProviderOutcome {
    UNKNOWN,
    UNSATISFIED,
}

data class QualificationCondition(
    val id: String,
    val type: String,
    val parameters: Map<String, String>,
    val onUnavailable: MissingQualificationProviderOutcome,
) {
    init {
        require(id.isNotBlank()) { "Qualification Condition ID must not be blank." }
        require(type.isNotBlank()) { "Qualification Condition type must not be blank." }
        require(parameters.keys.none { it.isBlank() }) { "Qualification Condition parameter names must not be blank." }
    }
}

data class QualificationConditionGroup(
    val allOf: List<QualificationCondition>,
) {
    init {
        require(allOf.isNotEmpty()) { "Qualification Condition group must contain at least one condition." }
        require(allOf.map { it.id }.distinct().size == allOf.size) {
            "Qualification Condition IDs must be unique within a group."
        }
    }
}

data class QualificationPolicy(
    val id: String,
    val revision: String,
    val anyOf: List<QualificationConditionGroup>,
) {
    init {
        require(id.isNotBlank()) { "Qualification Policy ID must not be blank." }
        require(revision.startsWith("sha256:") && revision.length == 71) {
            "Qualification Policy revision must be a SHA-256 identifier."
        }
        require(anyOf.isNotEmpty()) { "Qualification Policy must contain at least one OR group." }
        val conditionIds = anyOf.flatMap { group -> group.allOf.map { it.id } }
        require(conditionIds.distinct().size == conditionIds.size) {
            "Qualification Condition IDs must be unique within a policy."
        }
    }
}

data class QualificationConfiguration(
    val version: Int,
    val policies: Map<String, QualificationPolicy>,
) {
    init {
        require(policies.isNotEmpty()) { "Qualification configuration must contain at least one policy." }
    }
}

object QualificationConfigurationParser {
    const val CURRENT_VERSION = 1

    fun parse(value: Map<String, Any?>): QualificationConfiguration {
        require(value.keys == setOf("config-version", "policies")) {
            "Qualification configuration must contain only 'config-version' and 'policies'."
        }
        val version = (value["config-version"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("Qualification configuration requires numeric 'config-version'.")
        require(version == CURRENT_VERSION) {
            "Qualification config-version $version is unsupported; expected $CURRENT_VERSION."
        }
        val rawPolicies = value["policies"]
        require(rawPolicies is Map<*, *> && rawPolicies.isNotEmpty()) {
            "Qualification configuration requires a non-empty 'policies' map."
        }
        require(rawPolicies.keys.all { it is String }) { "Qualification Policy IDs must be strings." }
        val policies = rawPolicies.entries.associate { (rawId, rawPolicy) ->
            val id = rawId as String
            require(rawPolicy is Map<*, *> && rawPolicy.keys.all { it is String }) {
                "Qualification Policy $id must be a map with string keys."
            }
            @Suppress("UNCHECKED_CAST")
            id to QualificationPolicyParser.parse(id, rawPolicy as Map<String, Any?>)
        }
        return QualificationConfiguration(version, policies)
    }
}

object QualificationPolicyParser {
    fun parse(policyId: String, value: Map<String, Any?>): QualificationPolicy {
        require(policyId.isNotBlank()) { "Qualification Policy ID must not be blank." }
        require(value.keys == setOf("any-of")) {
            "Qualification Policy $policyId must contain only 'any-of'."
        }
        val groups = value.requiredList("any-of", "Qualification Policy $policyId")
            .mapIndexed { groupIndex, rawGroup ->
                val group = rawGroup.requiredMap("OR group ${groupIndex + 1} in policy $policyId")
                require(group.keys == setOf("all-of")) {
                    "OR group ${groupIndex + 1} in policy $policyId must contain only 'all-of'."
                }
                val conditions = group.requiredList(
                    "all-of",
                    "OR group ${groupIndex + 1} in policy $policyId",
                ).mapIndexed { conditionIndex, rawCondition ->
                    parseCondition(policyId, groupIndex, conditionIndex, rawCondition)
                }
                QualificationConditionGroup(conditions)
            }
        val canonical = canonical(policyId, groups)
        return QualificationPolicy(
            id = policyId,
            revision = "sha256:${sha256(canonical)}",
            anyOf = groups,
        )
    }

    private fun parseCondition(
        policyId: String,
        groupIndex: Int,
        conditionIndex: Int,
        rawCondition: Any?,
    ): QualificationCondition {
        val location = "condition ${conditionIndex + 1} in OR group ${groupIndex + 1} of policy $policyId"
        val condition = rawCondition.requiredMap(location)
        val type = condition.requiredString("type", location).uppercase(Locale.ROOT)
        val id = condition.optionalString("id", location)
            ?: "group-${groupIndex + 1}-condition-${conditionIndex + 1}"
        val onUnavailable = condition.optionalString("on-unavailable", location)
            ?.let {
                try {
                    MissingQualificationProviderOutcome.valueOf(it.uppercase(Locale.ROOT))
                } catch (error: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "$location has unsupported on-unavailable value '$it'; use UNKNOWN or UNSATISFIED.",
                        error,
                    )
                }
            }
            ?: MissingQualificationProviderOutcome.UNKNOWN
        val parameters = condition
            .filterKeys { it !in CONDITION_KEYS }
            .mapValues { (name, rawValue) ->
                require(rawValue is String || rawValue is Number || rawValue is Boolean) {
                    "$location parameter '$name' must be a string, number, or boolean."
                }
                rawValue.toString()
            }
        return QualificationCondition(id, type, parameters, onUnavailable)
    }

    private fun canonical(
        policyId: String,
        groups: List<QualificationConditionGroup>,
    ): String = buildString {
        appendPart(policyId)
        groups.forEach { group ->
            append("|")
            group.allOf.forEach { condition ->
                append("[")
                appendPart(condition.id)
                appendPart(condition.type)
                appendPart(condition.onUnavailable.name)
                condition.parameters.toSortedMap().forEach { (name, value) ->
                    appendPart(name)
                    appendPart(value)
                }
                append("]")
            }
        }
    }

    private fun StringBuilder.appendPart(value: String) {
        append(value.length).append(":").append(value)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun Map<String, Any?>.requiredList(name: String, location: String): List<Any?> {
        val value = this[name]
        require(value is List<*>) { "$location requires '$name' to be a list." }
        require(value.isNotEmpty()) { "$location requires '$name' to contain at least one entry." }
        return value
    }

    private fun Any?.requiredMap(location: String): Map<String, Any?> {
        require(this is Map<*, *>) { "$location must be a map." }
        require(keys.all { it is String }) { "$location keys must be strings." }
        @Suppress("UNCHECKED_CAST")
        return this as Map<String, Any?>
    }

    private fun Map<String, Any?>.requiredString(name: String, location: String): String =
        optionalString(name, location)
            ?: throw IllegalArgumentException("$location requires non-blank '$name'.")

    private fun Map<String, Any?>.optionalString(name: String, location: String): String? {
        val value = this[name] ?: return null
        require(value is String && value.isNotBlank()) { "$location requires '$name' to be a non-blank string." }
        return value
    }

    private val CONDITION_KEYS = setOf("id", "type", "on-unavailable")
}
