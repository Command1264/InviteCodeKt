package io.github.command1264.invitecode.core

import java.time.Instant

enum class RewardDeliveryStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED,
    UNCERTAIN,
}

enum class RewardActionStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED,
    UNCERTAIN,
    EXPIRED,
}

enum class UncertainRewardActionResolution {
    RETRY,
    MARK_DELIVERED,
}

data class RewardAdministrationContext(
    val actor: String,
    val reason: String,
    val occurredAt: Instant,
) {
    init {
        require(actor.isNotBlank()) { "Reward administration actor must not be blank." }
        require(reason.isNotBlank()) { "Reward administration reason must not be blank." }
    }
}

fun RewardActionStatus.canTransitionTo(next: RewardActionStatus, administrative: Boolean = false): Boolean =
    when (this) {
        RewardActionStatus.PENDING -> next in setOf(
            RewardActionStatus.PROCESSING,
            RewardActionStatus.FAILED,
            RewardActionStatus.EXPIRED,
        )
        RewardActionStatus.PROCESSING -> next in setOf(
            RewardActionStatus.DELIVERED,
            RewardActionStatus.FAILED,
            RewardActionStatus.UNCERTAIN,
        )
        RewardActionStatus.UNCERTAIN -> administrative && next in setOf(
            RewardActionStatus.PENDING,
            RewardActionStatus.DELIVERED,
        )
        RewardActionStatus.DELIVERED,
        RewardActionStatus.FAILED,
        RewardActionStatus.EXPIRED -> false
    }

fun RewardDeliveryStatus.canTransitionTo(
    next: RewardDeliveryStatus,
    administrative: Boolean = false,
): Boolean = when (this) {
    RewardDeliveryStatus.PENDING -> next in setOf(
        RewardDeliveryStatus.PROCESSING,
        RewardDeliveryStatus.DELIVERED,
        RewardDeliveryStatus.FAILED,
        RewardDeliveryStatus.UNCERTAIN,
    )
    RewardDeliveryStatus.PROCESSING -> next in setOf(
        RewardDeliveryStatus.DELIVERED,
        RewardDeliveryStatus.FAILED,
        RewardDeliveryStatus.UNCERTAIN,
    )
    RewardDeliveryStatus.UNCERTAIN -> administrative && next in setOf(
        RewardDeliveryStatus.PENDING,
        RewardDeliveryStatus.PROCESSING,
        RewardDeliveryStatus.DELIVERED,
        RewardDeliveryStatus.FAILED,
    )
    RewardDeliveryStatus.DELIVERED,
    RewardDeliveryStatus.FAILED -> false
}
