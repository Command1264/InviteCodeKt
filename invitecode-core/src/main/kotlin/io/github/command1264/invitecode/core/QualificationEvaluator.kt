package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import io.github.command1264.invitecode.api.QualificationConditionContext
import io.github.command1264.invitecode.api.QualificationConditionOutcome
import io.github.command1264.invitecode.api.QualificationConditionProvider
import io.github.command1264.invitecode.api.QualificationProviderRegistration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class QualificationProviderRegistry(
    providers: Iterable<QualificationConditionProvider> = emptyList(),
) {
    private val providersByType = ConcurrentHashMap<String, QualificationConditionProvider>()

    init {
        providers.forEach(::register)
    }

    fun register(provider: QualificationConditionProvider): QualificationProviderRegistration {
        val type = normalizeType(provider.type)
        require(providersByType.putIfAbsent(type, provider) == null) {
            "Qualification Condition provider '$type' is already registered."
        }
        return object : QualificationProviderRegistration {
            override fun close() {
                providersByType.remove(type, provider)
            }
        }
    }

    internal fun evaluate(
        condition: QualificationCondition,
        context: QualificationConditionContext,
    ): QualificationConditionOutcome {
        val provider = providersByType[normalizeType(condition.type)]
            ?: return when (condition.onUnavailable) {
                MissingQualificationProviderOutcome.UNKNOWN -> QualificationConditionOutcome.UNKNOWN
                MissingQualificationProviderOutcome.UNSATISFIED -> QualificationConditionOutcome.UNSATISFIED
            }
        return try {
            provider.evaluate(context, condition.parameters)
        } catch (error: Exception) {
            QualificationConditionOutcome.UNKNOWN
        }
    }

    private fun normalizeType(type: String): String {
        require(type.isNotBlank()) { "Qualification Condition provider type must not be blank." }
        return type.uppercase(Locale.ROOT)
    }
}

data class QualificationEvaluation(
    val invitation: Invitation,
    val facts: List<QualificationFact>,
    val qualified: Boolean,
)

class QualificationEvaluator(
    private val providers: QualificationProviderRegistry,
    private val factIdFactory: () -> UUID = UUID::randomUUID,
) {
    fun evaluate(
        invitation: Invitation,
        policy: QualificationPolicy,
        context: QualificationConditionContext,
    ): QualificationEvaluation {
        require(invitation.id == context.invitationId) {
            "Qualification context must target Invitation ${invitation.id}."
        }
        require(invitation.inviterId == context.inviterId && invitation.inviteeId == context.inviteeId) {
            "Qualification context players must match Invitation ${invitation.id}."
        }
        require(invitation.claimedAt == context.claimedAt) {
            "Qualification context Claim timestamp must match Invitation ${invitation.id}."
        }
        check(invitation.status == InvitationStatus.CLAIMED) {
            "Only a claimed Invitation can be evaluated; current status is ${invitation.status}."
        }

        val facts = mutableListOf<QualificationFact>()
        var qualified = false
        for (group in policy.anyOf) {
            var groupSatisfied = true
            for (condition in group.allOf) {
                val outcome = providers.evaluate(condition, context)
                facts += QualificationFact(
                    id = factIdFactory(),
                    invitationId = invitation.id,
                    policyId = policy.id,
                    qualificationRevision = policy.revision,
                    conditionId = condition.id,
                    outcome = outcome.toFactOutcome(),
                    recordedAt = context.evaluatedAt,
                )
                if (outcome != QualificationConditionOutcome.SATISFIED) {
                    groupSatisfied = false
                    break
                }
            }
            if (groupSatisfied) {
                qualified = true
                break
            }
        }

        return QualificationEvaluation(
            invitation = if (qualified) {
                invitation.qualify(context.evaluatedAt, policy.id, policy.revision)
            } else {
                invitation
            },
            facts = facts,
            qualified = qualified,
        )
    }

    private fun QualificationConditionOutcome.toFactOutcome(): QualificationFactOutcome = when (this) {
        QualificationConditionOutcome.SATISFIED -> QualificationFactOutcome.SATISFIED
        QualificationConditionOutcome.UNSATISFIED -> QualificationFactOutcome.UNSATISFIED
        QualificationConditionOutcome.UNKNOWN -> QualificationFactOutcome.UNKNOWN
    }
}
