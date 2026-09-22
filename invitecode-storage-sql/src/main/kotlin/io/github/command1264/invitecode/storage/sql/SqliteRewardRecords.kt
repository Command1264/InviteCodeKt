package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.RewardAdministrationContext
import io.github.command1264.invitecode.core.RewardBacklogCounts
import io.github.command1264.invitecode.core.RewardDeliveryStatus
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.core.StoredRewardActionTransition
import io.github.command1264.invitecode.core.StoredRewardDelivery
import io.github.command1264.invitecode.core.StoredRewardDeliveryTransition
import io.github.command1264.invitecode.core.canTransitionTo
import java.sql.Connection
import java.time.Instant
import java.util.UUID

internal object SqliteRewardRecords {
    fun saveDelivery(
        connection: Connection,
        delivery: StoredRewardDelivery,
        administration: RewardAdministrationContext? = null,
    ) {
        val current = findDelivery(connection, delivery.id)
        if (current != null) {
            if (current == delivery) return
            require(
                current.invitationId == delivery.invitationId &&
                    current.rewardId == delivery.rewardId &&
                    current.idempotencyKey == delivery.idempotencyKey &&
                    current.createdAt == delivery.createdAt,
            ) { "Reward Delivery ${delivery.id} identity fields cannot be changed." }
            require(current.status != delivery.status) {
                "Reward Delivery ${delivery.id} cannot change data without a status transition."
            }
            require(delivery.updatedAt.isAfter(current.updatedAt)) {
                "Reward Delivery ${delivery.id} status transitions require a newer update timestamp."
            }
            require(
                current.status == delivery.status ||
                    current.status.canTransitionTo(delivery.status, administration != null),
            ) {
                "Reward Delivery ${delivery.id} cannot transition from ${current.status} to ${delivery.status}."
            }
        }
        val previousStatus = current?.status
        connection.prepareStatement(
            """
            INSERT INTO invitecode_reward_deliveries(
                delivery_id, invitation_id, reward_id, status,
                idempotency_key, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(delivery_id) DO UPDATE SET
                status = excluded.status,
                updated_at = excluded.updated_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, delivery.id.toString())
            statement.setString(2, delivery.invitationId?.toString())
            statement.setString(3, delivery.rewardId)
            statement.setString(4, delivery.status.name)
            statement.setString(5, delivery.idempotencyKey)
            statement.setString(6, delivery.createdAt.toString())
            statement.setString(7, delivery.updatedAt.toString())
            statement.executeUpdate()
        }
        if (previousStatus != delivery.status) {
            connection.prepareStatement(
                """
                INSERT INTO invitecode_reward_delivery_transitions(
                    delivery_id, previous_status, current_status, occurred_at, actor, reason
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, delivery.id.toString())
                statement.setString(2, previousStatus?.name)
                statement.setString(3, delivery.status.name)
                statement.setString(4, delivery.updatedAt.toString())
                statement.setString(5, administration?.actor)
                statement.setString(6, administration?.reason)
                statement.executeUpdate()
            }
        }
    }

    fun findDelivery(connection: Connection, deliveryId: UUID): StoredRewardDelivery? =
        connection.prepareStatement(
            "SELECT * FROM invitecode_reward_deliveries WHERE delivery_id = ?",
        ).use { statement ->
            statement.setString(1, deliveryId.toString())
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                result.toRewardDelivery()
            }
        }

    fun findDeliveriesByInvitation(connection: Connection, invitationId: UUID): List<StoredRewardDelivery> =
        connection.prepareStatement(
            """
            SELECT * FROM invitecode_reward_deliveries
            WHERE invitation_id = ?
            ORDER BY created_at DESC, delivery_id DESC
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, invitationId.toString())
            statement.executeQuery().use { result ->
                buildList { while (result.next()) add(result.toRewardDelivery()) }
            }
        }

    fun backlogCounts(connection: Connection): RewardBacklogCounts =
        connection.prepareStatement(
            """
            SELECT
                SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS pending_count,
                SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS processing_count,
                SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS uncertain_count
            FROM invitecode_reward_actions
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, RewardActionStatus.PENDING.name)
            statement.setString(2, RewardActionStatus.PROCESSING.name)
            statement.setString(3, RewardActionStatus.UNCERTAIN.name)
            statement.executeQuery().use { result ->
                check(result.next()) { "Reward backlog count query returned no row." }
                RewardBacklogCounts(
                    pending = result.getInt("pending_count"),
                    processing = result.getInt("processing_count"),
                    uncertain = result.getInt("uncertain_count"),
                )
            }
        }

    fun saveAction(
        connection: Connection,
        action: StoredRewardAction,
        administration: RewardAdministrationContext? = null,
    ) {
        val current = findAction(connection, action.id)
        if (current != null) {
            if (current == action) return
            require(
                current.rewardDeliveryId == action.rewardDeliveryId &&
                    current.invitationId == action.invitationId &&
                    current.recipientId == action.recipientId &&
                    current.position == action.position &&
                    current.type == action.type &&
                    current.payload == action.payload &&
                    current.idempotencyKey == action.idempotencyKey &&
                    current.createdAt == action.createdAt,
            ) { "Reward Action ${action.id} identity fields cannot be changed." }
            require(current.status != action.status) {
                "Reward Action ${action.id} cannot change data without a status transition."
            }
            require(action.updatedAt.isAfter(current.updatedAt)) {
                "Reward Action ${action.id} status transitions require a newer update timestamp."
            }
            require(
                current.status == action.status ||
                    current.status.canTransitionTo(action.status, administration != null),
            ) { "Reward Action ${action.id} cannot transition from ${current.status} to ${action.status}." }
        }
        val previousStatus = current?.status
        connection.prepareStatement(
            """
            INSERT INTO invitecode_reward_actions(
                action_id, reward_delivery_id, invitation_id, recipient_id, position,
                action_type, payload, status, idempotency_key, created_at, updated_at, last_error
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(action_id) DO UPDATE SET
                status = excluded.status,
                updated_at = excluded.updated_at,
                last_error = excluded.last_error
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, action.id.toString())
            statement.setString(2, action.rewardDeliveryId.toString())
            statement.setString(3, action.invitationId?.toString())
            statement.setString(4, action.recipientId.toString())
            statement.setInt(5, action.position)
            statement.setString(6, action.type)
            statement.setString(7, action.payload)
            statement.setString(8, action.status.name)
            statement.setString(9, action.idempotencyKey)
            statement.setString(10, action.createdAt.toString())
            statement.setString(11, action.updatedAt.toString())
            statement.setString(12, action.lastError)
            statement.executeUpdate()
        }
        if (previousStatus != action.status) {
            connection.prepareStatement(
                """
                INSERT INTO invitecode_reward_action_transitions(
                    action_id, previous_status, current_status, occurred_at, last_error, actor, reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, action.id.toString())
                statement.setString(2, previousStatus?.name)
                statement.setString(3, action.status.name)
                statement.setString(4, action.updatedAt.toString())
                statement.setString(5, action.lastError)
                statement.setString(6, administration?.actor)
                statement.setString(7, administration?.reason)
                statement.executeUpdate()
            }
        }
    }

    fun findAction(connection: Connection, actionId: UUID): StoredRewardAction? =
        connection.prepareStatement(
            "SELECT * FROM invitecode_reward_actions WHERE action_id = ?",
        ).use { statement ->
            statement.setString(1, actionId.toString())
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                result.toRewardAction()
            }
        }

    fun findActions(connection: Connection, deliveryId: UUID): List<StoredRewardAction> =
        connection.prepareStatement(
            """
            SELECT * FROM invitecode_reward_actions
            WHERE reward_delivery_id = ?
            ORDER BY position, action_id
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, deliveryId.toString())
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.toRewardAction())
                }
            }
        }

    fun findPendingActions(connection: Connection, recipientId: UUID): List<StoredRewardAction> =
        connection.prepareStatement(
            """
            SELECT * FROM invitecode_reward_actions
            WHERE recipient_id = ? AND status = ?
            ORDER BY created_at, reward_delivery_id, position, action_id
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, recipientId.toString())
            statement.setString(2, RewardActionStatus.PENDING.name)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.toRewardAction())
                }
            }
        }

    fun findActionsByStatus(connection: Connection, status: RewardActionStatus): List<StoredRewardAction> =
        connection.prepareStatement(
            "SELECT * FROM invitecode_reward_actions WHERE status = ? ORDER BY action_id",
        ).use { statement ->
            statement.setString(1, status.name)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.toRewardAction())
                }
            }
        }

    fun findActionTransitions(
        connection: Connection,
        actionId: UUID,
    ): List<StoredRewardActionTransition> = connection.prepareStatement(
        """
        SELECT transition_id, action_id, previous_status, current_status, occurred_at,
               last_error, actor, reason
        FROM invitecode_reward_action_transitions
        WHERE action_id = ?
        ORDER BY transition_id
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, actionId.toString())
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) {
                    add(
                        StoredRewardActionTransition(
                            sequence = result.getLong("transition_id"),
                            actionId = UUID.fromString(result.getString("action_id")),
                            previousStatus = result.getString("previous_status")?.let(RewardActionStatus::valueOf),
                            currentStatus = RewardActionStatus.valueOf(result.getString("current_status")),
                            occurredAt = Instant.parse(result.getString("occurred_at")),
                            lastError = result.getString("last_error"),
                            actor = result.getString("actor"),
                            reason = result.getString("reason"),
                        ),
                    )
                }
            }
        }
    }

    fun findDeliveryTransitions(
        connection: Connection,
        deliveryId: UUID,
    ): List<StoredRewardDeliveryTransition> = connection.prepareStatement(
        """
        SELECT transition_id, delivery_id, previous_status, current_status, occurred_at, actor, reason
        FROM invitecode_reward_delivery_transitions
        WHERE delivery_id = ?
        ORDER BY transition_id
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, deliveryId.toString())
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) {
                    add(
                        StoredRewardDeliveryTransition(
                            sequence = result.getLong("transition_id"),
                            deliveryId = UUID.fromString(result.getString("delivery_id")),
                            previousStatus = result.getString("previous_status")?.let(RewardDeliveryStatus::valueOf),
                            currentStatus = RewardDeliveryStatus.valueOf(result.getString("current_status")),
                            occurredAt = Instant.parse(result.getString("occurred_at")),
                            actor = result.getString("actor"),
                            reason = result.getString("reason"),
                        ),
                    )
                }
            }
        }
    }

    private fun java.sql.ResultSet.toRewardAction() = StoredRewardAction(
        id = UUID.fromString(getString("action_id")),
        rewardDeliveryId = UUID.fromString(getString("reward_delivery_id")),
        invitationId = getString("invitation_id")?.let(UUID::fromString),
        recipientId = UUID.fromString(getString("recipient_id")),
        position = getInt("position"),
        type = getString("action_type"),
        payload = getString("payload"),
        status = RewardActionStatus.valueOf(getString("status")),
        idempotencyKey = getString("idempotency_key"),
        createdAt = Instant.parse(getString("created_at")),
        updatedAt = Instant.parse(getString("updated_at")),
        lastError = getString("last_error"),
    )

    private fun java.sql.ResultSet.toRewardDelivery() = StoredRewardDelivery(
        id = UUID.fromString(getString("delivery_id")),
        invitationId = getString("invitation_id")?.let(UUID::fromString),
        rewardId = getString("reward_id"),
        status = RewardDeliveryStatus.valueOf(getString("status")),
        idempotencyKey = getString("idempotency_key"),
        createdAt = Instant.parse(getString("created_at")),
        updatedAt = Instant.parse(getString("updated_at")),
    )
}
