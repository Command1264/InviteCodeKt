package io.github.command1264.invitecode.core

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class StoredPlayer(
    val id: UUID,
    val firstSeenAt: Instant,
    val lastKnownName: String? = null,
) {
    init {
        require(lastKnownName == null || lastKnownName.isNotBlank()) {
            "Last known player name must not be blank."
        }
    }
}

data class PlayerNetworkHash(
    val playerId: UUID,
    val keyId: String,
    val hash: String,
    val observedAt: Instant,
    val expiresAt: Instant,
) {
    init {
        require(HASH_PATTERN.matches(keyId)) { "Network hash key ID must be a lowercase SHA-256 hex digest." }
        require(HASH_PATTERN.matches(hash)) { "Player network hash must be a lowercase SHA-256 hex digest." }
        require(expiresAt.isAfter(observedAt)) { "Player network hash expiry must be after observation." }
    }

    companion object {
        private val HASH_PATTERN = Regex("[0-9a-f]{64}")
    }
}

enum class QualificationFactOutcome {
    SATISFIED,
    UNSATISFIED,
    UNKNOWN,
}

data class QualificationFact(
    val id: UUID,
    val invitationId: UUID,
    val policyId: String,
    val qualificationRevision: String,
    val conditionId: String,
    val outcome: QualificationFactOutcome,
    val recordedAt: Instant,
) {
    init {
        require(policyId.isNotBlank()) { "Qualification policy ID must not be blank." }
        require(qualificationRevision.isNotBlank()) { "Qualification revision must not be blank." }
        require(conditionId.isNotBlank()) { "Qualification condition ID must not be blank." }
    }
}

data class StoredRewardAction(
    val id: UUID,
    val rewardDeliveryId: UUID,
    val invitationId: UUID?,
    val recipientId: UUID,
    val position: Int,
    val type: String,
    val payload: String,
    val status: RewardActionStatus,
    val idempotencyKey: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastError: String? = null,
) {
    init {
        require(position >= 0) { "Reward Action position must not be negative." }
        require(type.isNotBlank()) { "Reward Action type must not be blank." }
        require(idempotencyKey.isNotBlank()) { "Reward Action idempotency key must not be blank." }
        require(!updatedAt.isBefore(createdAt)) { "Reward Action cannot be updated before it was created." }
    }
}

data class StoredRewardDelivery(
    val id: UUID,
    val invitationId: UUID?,
    val rewardId: String,
    val status: RewardDeliveryStatus,
    val idempotencyKey: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(rewardId.isNotBlank()) { "Reward ID must not be blank." }
        require(idempotencyKey.isNotBlank()) { "Reward Delivery idempotency key must not be blank." }
        require(!updatedAt.isBefore(createdAt)) { "Reward Delivery cannot be updated before it was created." }
    }
}

data class StoredRewardActionTransition(
    val sequence: Long,
    val actionId: UUID,
    val previousStatus: RewardActionStatus?,
    val currentStatus: RewardActionStatus,
    val occurredAt: Instant,
    val lastError: String? = null,
    val actor: String? = null,
    val reason: String? = null,
) {
    init {
        require(sequence > 0) { "Reward Action transition sequence must be positive." }
        require(previousStatus != currentStatus) { "Reward Action transition must change status." }
        require((actor == null) == (reason == null)) { "Reward transition actor and reason must appear together." }
        require(actor == null || actor.isNotBlank()) { "Reward transition actor must not be blank." }
        require(reason == null || reason.isNotBlank()) { "Reward transition reason must not be blank." }
    }
}

data class StoredRewardDeliveryTransition(
    val sequence: Long,
    val deliveryId: UUID,
    val previousStatus: RewardDeliveryStatus?,
    val currentStatus: RewardDeliveryStatus,
    val occurredAt: Instant,
    val actor: String? = null,
    val reason: String? = null,
) {
    init {
        require(sequence > 0) { "Reward Delivery transition sequence must be positive." }
        require(previousStatus != currentStatus) { "Reward Delivery transition must change status." }
        require((actor == null) == (reason == null)) { "Reward transition actor and reason must appear together." }
        require(actor == null || actor.isNotBlank()) { "Reward transition actor must not be blank." }
        require(reason == null || reason.isNotBlank()) { "Reward transition reason must not be blank." }
    }
}

data class InviterInvitationStats(
    val claimed: Int,
    val qualified: Int,
) {
    init {
        require(claimed >= 0) { "Claimed Invitation count must not be negative." }
        require(qualified >= 0) { "Qualified Invitation count must not be negative." }
    }
}

data class RewardBacklogCounts(
    val pending: Int,
    val processing: Int,
    val uncertain: Int,
) {
    init {
        require(pending >= 0) { "Pending Reward Action count must not be negative." }
        require(processing >= 0) { "Processing Reward Action count must not be negative." }
        require(uncertain >= 0) { "Uncertain Reward Action count must not be negative." }
    }
}

const val MAX_AUDIT_HISTORY_LIMIT: Int = 100

/**
 * 持久化領域狀態的同步介面。SQL adapter 會執行 blocking I/O，呼叫端必須將操作排程至
 * server main thread 以外的專用 executor。
 */
interface QualificationRecords {
    fun invitation(invitationId: UUID): Invitation?

    fun saveQualifiedInvitation(invitation: Invitation, facts: List<QualificationFact>)

    fun saveQualificationFacts(facts: List<QualificationFact>)
}

interface RewardRecords {
    fun qualifiedInvitationCount(inviterId: UUID): Int

    fun saveRewardAction(action: StoredRewardAction)

    fun rewardAction(actionId: UUID): StoredRewardAction?

    fun rewardActions(deliveryId: UUID): List<StoredRewardAction>

    fun pendingRewardActions(recipientId: UUID): List<StoredRewardAction>

    fun recoverInterruptedRewardActions(recoveredAt: Instant): Int

    fun resolveUncertainRewardAction(
        actionId: UUID,
        resolution: UncertainRewardActionResolution,
        context: RewardAdministrationContext,
    ): StoredRewardAction

    fun saveRewardDelivery(delivery: StoredRewardDelivery, actions: List<StoredRewardAction>)

    fun rewardDelivery(deliveryId: UUID): StoredRewardDelivery?
}

interface QualificationRewardRecords : QualificationRecords, RewardRecords {
    /** Persists the first successful Qualification and every resulting Reward plan in one transaction. */
    fun saveQualifiedInvitationWithRewards(
        invitation: Invitation,
        facts: List<QualificationFact>,
        rewardPlans: List<RewardDeliveryPlan>,
    )
}

interface PlayerLookup {
    fun player(playerId: UUID): StoredPlayer?

    /**
     * Returns at most two case-insensitive exact matches. Two results are sufficient to reject an
     * ambiguous player reference; callers must not assume a last known name is unique.
     */
    fun playersByLastKnownName(lastKnownName: String): List<StoredPlayer>
}

interface InviteCodeQueries {
    fun claimState(playerId: UUID): PlayerClaimState

    /** Returns the matching Invite Code only while it is active. */
    fun activeInviteCode(inviteCode: InviteCode): ActiveInviteCode?

    /** Returns every eligibility generation, newest Claim first with Invitation ID as the tie-breaker. */
    fun invitationsByInvitee(inviteeId: UUID): List<Invitation>

    /** Returns all Invitations currently attributed to the Inviter, newest first with ID as the tie-breaker. */
    fun invitationsByInviter(inviterId: UUID): List<Invitation>

    /** Counts current CLAIMED and QUALIFIED states separately; UNLINKED Invitations are excluded. */
    fun inviterInvitationStats(inviterId: UUID): InviterInvitationStats

    /** Returns facts in recording order, with fact ID as the tie-breaker. */
    fun qualificationFacts(invitationId: UUID): List<QualificationFact>

    /** Returns newest records first, with Audit Record ID as the tie-breaker. */
    fun auditHistoryByInvitation(invitationId: UUID, limit: Int): List<AuditRecord>

    /** Returns newest records first, with Audit Record ID as the tie-breaker. */
    fun auditHistoryByPlayer(playerId: UUID, limit: Int): List<AuditRecord>

    /** Returns newest deliveries first, with Reward Delivery ID as the tie-breaker. */
    fun rewardDeliveries(invitationId: UUID): List<StoredRewardDelivery>

    /** Counts global Reward Actions that still need delivery or operator attention. */
    fun rewardBacklogCounts(): RewardBacklogCounts
}

interface InviteCodeStorage :
    InvitationTransactions,
    InviteCodeRegistry,
    QualificationRewardRecords,
    PlayerLookup,
    InviteCodeQueries,
    AutoCloseable {
    fun savePlayer(player: StoredPlayer)

    fun recordLoginDay(playerId: UUID, loginDate: LocalDate)

    fun loginDays(playerId: UUID): Int

    fun recordNetworkHash(record: PlayerNetworkHash)

    fun purgeExpiredNetworkHashes(expiredAt: Instant): Int

    fun purgeNetworkHashesWithDifferentKey(keyId: String): Int

    fun activeInviteCode(inviterId: UUID): ActiveInviteCode?

    fun isInviteCodeReserved(code: InviteCode): Boolean

    fun saveInvitation(invitation: Invitation)

    fun saveQualificationFact(fact: QualificationFact)

    fun qualificationFact(factId: UUID): QualificationFact?

    fun rewardActionTransitions(actionId: UUID): List<StoredRewardActionTransition>

    fun rewardDeliveryTransitions(deliveryId: UUID): List<StoredRewardDeliveryTransition>

    fun saveAuditRecord(record: AuditRecord)

    fun auditRecord(recordId: UUID): AuditRecord?

    fun saveAuditedInvitationTransition(transition: AuditedInvitationTransition)

    fun saveForcedQualificationWithRewards(
        transition: AuditedInvitationTransition,
        rewardPlans: List<RewardDeliveryPlan>,
    )

    fun saveAuditedClaimStateTransition(transition: AuditedClaimStateTransition)
}
