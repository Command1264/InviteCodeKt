package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.api.ClaimEligibilitySnapshot
import io.github.command1264.invitecode.api.InvitationSnapshot
import io.github.command1264.invitecode.api.InviteCodeApi
import io.github.command1264.invitecode.api.QualificationConditionProvider
import io.github.command1264.invitecode.api.QualificationEvaluationSnapshot
import io.github.command1264.invitecode.api.QualificationProviderRegistration
import io.github.command1264.invitecode.core.ClaimEligibilityPolicy
import io.github.command1264.invitecode.core.InviteCodeStorage
import io.github.command1264.invitecode.core.QualificationProviderRegistry
import io.github.command1264.invitecode.platform.bukkit.AsyncOperationExecutor
import io.github.command1264.invitecode.platform.bukkit.AtomicRuntimeSnapshot
import io.github.command1264.invitecode.platform.bukkit.InviteCodeRuntimeConfiguration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicReference

class BukkitInviteCodeApi(
    private val configuration: AtomicRuntimeSnapshot<InviteCodeRuntimeConfiguration>,
    private val storage: AtomicReference<InviteCodeStorage?>,
    private val operations: AsyncOperationExecutor,
    private val providers: QualificationProviderRegistry,
    private val requestEvaluation: (UUID) -> CompletionStage<QualificationEvaluationSnapshot>,
    private val now: () -> Instant = Instant::now,
) : InviteCodeApi {
    override fun getClaimEligibility(playerId: UUID): CompletionStage<ClaimEligibilitySnapshot> = submit {
        val evaluatedAt = now()
        val store = requireNotNull(storage.get()) { "SQLite storage is unavailable." }
        val eligibility = ClaimEligibilityPolicy(configuration.current().value.general.claim.window)
            .evaluate(store.claimState(playerId), evaluatedAt)
        ClaimEligibilitySnapshot(
            playerId,
            eligibility.eligible,
            eligibility.reasons.map { it.name }.sorted(),
            evaluatedAt,
        )
    }

    override fun getInvitation(invitationId: UUID): CompletionStage<InvitationSnapshot?> = submit {
        val store = requireNotNull(storage.get()) { "SQLite storage is unavailable." }
        store.invitation(invitationId)?.let { invitation ->
            InvitationSnapshot(
                invitation.id,
                invitation.inviterId,
                invitation.inviteeId,
                invitation.status,
                invitation.claimedAt,
                invitation.qualifiedAt,
            )
        }
    }

    override fun registerQualificationConditionProvider(
        provider: QualificationConditionProvider,
    ): QualificationProviderRegistration = providers.register(provider)

    override fun requestQualificationEvaluation(
        invitationId: UUID,
    ): CompletionStage<QualificationEvaluationSnapshot> = requestEvaluation(invitationId)

    private fun <T> submit(operation: () -> T): CompletionStage<T> {
        val future = CompletableFuture<T>()
        val accepted = operations.submit(operation, future::complete, future::completeExceptionally)
        if (!accepted) future.completeExceptionally(IllegalStateException("InviteCode is stopping."))
        return future
    }
}
