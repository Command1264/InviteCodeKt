package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.RewardDeliveryStatus
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.core.StoredRewardDelivery
import java.sql.Connection
import java.time.Instant
import java.util.UUID

internal object SqliteRewardRecords {
    fun saveDelivery(connection: Connection, delivery: StoredRewardDelivery) {
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
    }

    fun findDelivery(connection: Connection, deliveryId: UUID): StoredRewardDelivery? =
        connection.prepareStatement(
            "SELECT * FROM invitecode_reward_deliveries WHERE delivery_id = ?",
        ).use { statement ->
            statement.setString(1, deliveryId.toString())
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                StoredRewardDelivery(
                    id = UUID.fromString(result.getString("delivery_id")),
                    invitationId = result.getString("invitation_id")?.let(UUID::fromString),
                    rewardId = result.getString("reward_id"),
                    status = RewardDeliveryStatus.valueOf(result.getString("status")),
                    idempotencyKey = result.getString("idempotency_key"),
                    createdAt = Instant.parse(result.getString("created_at")),
                    updatedAt = Instant.parse(result.getString("updated_at")),
                )
            }
        }

    fun saveAction(connection: Connection, action: StoredRewardAction) {
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
    }

    fun findAction(connection: Connection, actionId: UUID): StoredRewardAction? =
        connection.prepareStatement(
            "SELECT * FROM invitecode_reward_actions WHERE action_id = ?",
        ).use { statement ->
            statement.setString(1, actionId.toString())
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                StoredRewardAction(
                    id = UUID.fromString(result.getString("action_id")),
                    rewardDeliveryId = UUID.fromString(result.getString("reward_delivery_id")),
                    invitationId = result.getString("invitation_id")?.let(UUID::fromString),
                    recipientId = UUID.fromString(result.getString("recipient_id")),
                    position = result.getInt("position"),
                    type = result.getString("action_type"),
                    payload = result.getString("payload"),
                    status = RewardActionStatus.valueOf(result.getString("status")),
                    idempotencyKey = result.getString("idempotency_key"),
                    createdAt = Instant.parse(result.getString("created_at")),
                    updatedAt = Instant.parse(result.getString("updated_at")),
                    lastError = result.getString("last_error"),
                )
            }
        }
}
