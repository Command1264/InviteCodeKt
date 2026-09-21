package io.github.command1264.invitecode.core

import java.time.Instant
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

/**
 * 持久化領域狀態的同步介面。SQL adapter 會執行 blocking I/O，呼叫端必須將操作排程至
 * server main thread 以外的專用 executor。
 */
interface InviteCodeStorage : InvitationTransactions, InviteCodeRegistry, AutoCloseable {
    fun savePlayer(player: StoredPlayer)

    fun player(playerId: UUID): StoredPlayer?

    fun activeInviteCode(inviterId: UUID): ActiveInviteCode?

    fun isInviteCodeReserved(code: InviteCode): Boolean

    fun saveInvitation(invitation: Invitation)

    fun invitation(invitationId: UUID): Invitation?

    fun saveQualificationFact(fact: QualificationFact)

    fun qualificationFact(factId: UUID): QualificationFact?

    fun saveRewardAction(action: StoredRewardAction)

    fun rewardAction(actionId: UUID): StoredRewardAction?

    fun saveRewardDelivery(delivery: StoredRewardDelivery, actions: List<StoredRewardAction>)

    fun rewardDelivery(deliveryId: UUID): StoredRewardDelivery?

    fun saveAuditRecord(record: AuditRecord)

    fun auditRecord(recordId: UUID): AuditRecord?

    fun saveAuditedInvitationTransition(transition: AuditedInvitationTransition)

    fun saveAuditedClaimStateTransition(transition: AuditedClaimStateTransition)
}
