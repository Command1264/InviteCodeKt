package io.github.command1264.invitecode.storage.sql

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.util.UUID
import java.util.Locale
import io.github.command1264.invitecode.core.AuditRecord
import io.github.command1264.invitecode.core.InvitationAuditAction

/** Only the maintenance entry point calls this; all callers must stop every writer and take a backup first. */
data class AnonymizationPreview(
    val invitations: Int,
    val rewardDeliveries: Int,
    val retiredCodes: Int,
)

data class AnonymizationResult(val historicalId: UUID, val preview: AnonymizationPreview)

internal object OfflinePlayerAnonymizer {
    private const val REDACTED = "[anonymized]"
    private val unsettled = setOf("PENDING", "PROCESSING", "UNCERTAIN")

    fun preview(connection: Connection, playerId: UUID): AnonymizationPreview = collect(connection, playerId).preview

    fun anonymize(
        connection: Connection,
        playerId: UUID,
        keyId: String,
        hashCode: (String) -> String,
    ): AnonymizationResult {
        require(Regex("[0-9a-f]{64}").matches(keyId)) { "Anti-abuse key ID is invalid." }
        if (connection.isMysql()) {
            val locked = connection.prepareStatement(
                "SELECT player_id FROM invitecode_players WHERE player_id = ? FOR UPDATE",
            ).use { statement ->
                statement.setString(1, playerId.toString())
                statement.executeQuery().use { it.next() }
            }
            check(locked) { "Player $playerId is absent; it may already be anonymized." }
        }
        val scope = collect(connection, playerId)
        val old = playerId.toString()
        val historical = UUID.randomUUID().toString()
        check(row(connection, "invitecode_players", "player_id", historical) == null) {
            "Generated historical ID collided with an existing player; retry after rollback."
        }
        val player = requireNotNull(row(connection, "invitecode_players", "player_id", old))
        insert(connection, "invitecode_players", player + ("player_id" to historical) + ("last_known_name" to null))

        // Reward IDs and idempotency keys can be derived from the Minecraft UUID. Copy first, then
        // repoint transition FKs and remove the originals within the same storage transaction.
        scope.deliveries.forEach { deliveryId ->
            val delivery = requireNotNull(row(connection, "invitecode_reward_deliveries", "delivery_id", deliveryId))
            val replacement = UUID.randomUUID().toString()
            insert(connection, "invitecode_reward_deliveries", delivery + mapOf(
                "delivery_id" to replacement,
                "reward_id" to REDACTED,
                "idempotency_key" to "anonymized:${UUID.randomUUID()}",
            ))
            queryIds(connection, "SELECT action_id FROM invitecode_reward_actions WHERE reward_delivery_id = ?", deliveryId)
                .forEach { actionId ->
                    val action = requireNotNull(row(connection, "invitecode_reward_actions", "action_id", actionId))
                    val replacementAction = UUID.randomUUID().toString()
                    insert(connection, "invitecode_reward_actions", action + mapOf(
                        "action_id" to replacementAction,
                        "reward_delivery_id" to replacement,
                        "recipient_id" to if (action["recipient_id"] == old) historical else action["recipient_id"],
                        "payload" to REDACTED,
                        "idempotency_key" to "anonymized:${UUID.randomUUID()}",
                        "last_error" to if (action["last_error"] == null) null else REDACTED,
                    ))
                    update(connection, """
                        UPDATE invitecode_reward_action_transitions
                        SET action_id = ?, last_error = CASE WHEN last_error IS NULL THEN NULL ELSE ? END,
                            actor = CASE WHEN actor IS NULL THEN NULL ELSE ? END,
                            reason = CASE WHEN reason IS NULL THEN NULL ELSE ? END
                        WHERE action_id = ?
                    """, replacementAction, REDACTED, REDACTED, REDACTED, actionId)
                    update(connection, "DELETE FROM invitecode_reward_actions WHERE action_id = ?", actionId)
                }
            update(connection, """
                UPDATE invitecode_reward_delivery_transitions
                SET delivery_id = ?, actor = CASE WHEN actor IS NULL THEN NULL ELSE ? END,
                    reason = CASE WHEN reason IS NULL THEN NULL ELSE ? END
                WHERE delivery_id = ?
            """, replacement, REDACTED, REDACTED, deliveryId)
            update(connection, "DELETE FROM invitecode_reward_deliveries WHERE delivery_id = ?", deliveryId)
        }

        scope.codes.forEach { code ->
            val digest = hashCode(code)
            require(Regex("[0-9a-f]{64}").matches(digest)) { "Reserved-code digest is invalid." }
            insert(connection, "invitecode_reserved_code_hashes", mapOf("code_hash" to digest, "key_id" to keyId))
        }
        update(connection, "DELETE FROM invitecode_codes WHERE inviter_id = ?", old)

        // Redact every structurally related audit row before changing its player reference.
        val auditIds = queryIds(connection, """
            SELECT audit_id FROM invitecode_audit_records WHERE player_id = ?
               OR previous_inviter_id = ? OR new_inviter_id = ?
               OR invitation_id IN (SELECT invitation_id FROM invitecode_invitations WHERE inviter_id = ? OR invitee_id = ?)
        """, old, old, old, old, old)
        auditIds.forEach { id ->
            update(connection, """
                UPDATE invitecode_audit_records SET audit_id = ?, actor = ?, reason = ?,
                    qualification_policy_id = CASE WHEN qualification_policy_id IS NULL THEN NULL ELSE ? END,
                    qualification_revision = CASE WHEN qualification_revision IS NULL THEN NULL ELSE ? END
                WHERE audit_id = ?
            """, UUID.randomUUID().toString(), REDACTED, REDACTED, REDACTED, REDACTED, id)
        }
        update(connection, "UPDATE invitecode_audit_records SET player_id = ? WHERE player_id = ?", historical, old)
        update(connection, "UPDATE invitecode_audit_records SET previous_inviter_id = ? WHERE previous_inviter_id = ?", historical, old)
        update(connection, "UPDATE invitecode_audit_records SET new_inviter_id = ? WHERE new_inviter_id = ?", historical, old)

        scope.invitations.forEach { id ->
            update(connection, """
                UPDATE invitecode_invitations
                SET qualification_policy_id = CASE WHEN qualification_policy_id IS NULL THEN NULL ELSE ? END,
                    qualification_revision = CASE WHEN qualification_revision IS NULL THEN NULL ELSE ? END
                WHERE invitation_id = ?
            """, REDACTED, REDACTED, id)
            update(connection, """
                UPDATE invitecode_qualification_facts
                SET policy_id = ?, qualification_revision = ?, condition_id = ? WHERE invitation_id = ?
            """, REDACTED, REDACTED, REDACTED, id)
        }
        update(connection, "UPDATE invitecode_invitations SET inviter_id = ? WHERE inviter_id = ?", historical, old)
        update(connection, "UPDATE invitecode_invitations SET invitee_id = ? WHERE invitee_id = ?", historical, old)
        // A reward action for this player can belong to an unrelated delivery; collect() includes it.
        update(connection, "UPDATE invitecode_reward_actions SET recipient_id = ? WHERE recipient_id = ?", historical, old)
        val oldName = player["last_known_name"] as? String
        if (!oldName.isNullOrBlank()) redactSafeFreeText(connection, oldName)
        update(connection, "DELETE FROM invitecode_player_login_days WHERE player_id = ?", old)
        update(connection, "DELETE FROM invitecode_player_network_hashes WHERE player_id = ?", old)
        check(update(connection, "DELETE FROM invitecode_players WHERE player_id = ?", old) == 1) {
            "Original player record changed during anonymization."
        }
        SqliteAuditRecords.save(connection, AuditRecord(
            UUID.randomUUID(), InvitationAuditAction.PLAYER_ANONYMIZED,
            "offline-maintenance", "privacy request", Instant.now(), playerId = UUID.fromString(historical),
        ))
        verifyNoOriginalUuid(connection, old)
        if (!oldName.isNullOrBlank()) verifyNoOriginalName(connection, oldName)
        return AnonymizationResult(UUID.fromString(historical), scope.preview)
    }

    private fun redactSafeFreeText(connection: Connection, originalName: String) {
        val fields = mapOf(
            "invitecode_audit_records" to listOf("actor", "reason"),
            "invitecode_reward_action_transitions" to listOf("actor", "reason", "last_error"),
            "invitecode_reward_delivery_transitions" to listOf("actor", "reason"),
        )
        val needle = originalName.lowercase(Locale.ROOT)
        fields.forEach { (table, columns) ->
            columns.forEach { column ->
                connection.prepareStatement(
                    "UPDATE $table SET $column = ? WHERE INSTR(LOWER($column), ?) > 0",
                ).use { statement ->
                    statement.setString(1, REDACTED)
                    statement.setString(2, needle)
                    statement.executeUpdate()
                }
            }
        }
    }

    private fun verifyNoOriginalName(connection: Connection, originalName: String) {
        val fields = mapOf(
            "invitecode_audit_records" to listOf("actor", "reason", "qualification_policy_id", "qualification_revision"),
            "invitecode_reward_actions" to listOf("payload", "last_error", "idempotency_key"),
            "invitecode_reward_deliveries" to listOf("reward_id", "idempotency_key"),
            "invitecode_reward_action_transitions" to listOf("actor", "reason", "last_error"),
            "invitecode_reward_delivery_transitions" to listOf("actor", "reason"),
            "invitecode_invitations" to listOf("qualification_policy_id", "qualification_revision"),
            "invitecode_qualification_facts" to listOf("policy_id", "qualification_revision", "condition_id"),
        )
        fields.forEach { (table, columns) ->
            val conditions = columns.joinToString(" OR ") { "INSTR(LOWER($it), ?) > 0" }
            connection.prepareStatement("SELECT 1 FROM $table WHERE $conditions LIMIT 1").use { statement ->
                columns.indices.forEach { index ->
                    statement.setString(index + 1, originalName.lowercase(Locale.ROOT))
                }
                statement.executeQuery().use { rows ->
                    check(!rows.next()) {
                        "Player name remains in $table; anonymization was rolled back. " +
                            "Inspect unrelated free text and unresolved rewards before retrying."
                    }
                }
            }
        }
    }

    private fun verifyNoOriginalUuid(connection: Connection, original: String) {
        val tables = listOf(
            "invitecode_players", "invitecode_codes", "invitecode_invitations", "invitecode_qualification_facts",
            "invitecode_reward_deliveries", "invitecode_reward_actions", "invitecode_audit_records",
            "invitecode_reward_delivery_transitions", "invitecode_reward_action_transitions",
            "invitecode_player_login_days", "invitecode_player_network_hashes", "invitecode_reserved_code_hashes",
        )
        tables.forEach { table ->
            val textColumns = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM $table WHERE 1 = 0").use { rows ->
                    val metadata = rows.metaData
                    (1..metadata.columnCount).filter { index ->
                        metadata.getColumnType(index) in setOf(
                            Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
                            Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR,
                        )
                    }.map { index ->
                        metadata.getColumnName(index).also { name ->
                            require(Regex("[a-z_]+").matches(name)) { "Storage column name is invalid." }
                        }
                    }
                }
            }
            if (textColumns.isNotEmpty()) {
                val conditions = textColumns.joinToString(" OR ") { "LOWER($it) LIKE ?" }
                listOf(original, original.replace("-", "")).forEach { needle ->
                    connection.prepareStatement("SELECT 1 FROM $table WHERE $conditions LIMIT 1").use { statement ->
                        textColumns.indices.forEach { index -> statement.setString(index + 1, "%$needle%") }
                        statement.executeQuery().use { rows ->
                            check(!rows.next()) {
                                "Original Minecraft UUID remains in $table; anonymization was rolled back. " +
                                    "Inspect related text without exposing private data."
                            }
                        }
                    }
                }
            }
        }
    }

    private data class Scope(val invitations: List<String>, val deliveries: List<String>, val codes: List<String>) {
        val preview = AnonymizationPreview(invitations.size, deliveries.size, codes.size)
    }

    private fun collect(connection: Connection, playerId: UUID): Scope {
        val id = playerId.toString()
        check(row(connection, "invitecode_players", "player_id", id) != null) {
            "Player $id is absent; it may already be anonymized."
        }
        val invitations = queryIds(connection,
            "SELECT invitation_id FROM invitecode_invitations WHERE inviter_id = ? OR invitee_id = ?", id, id)
        val deliveries = queryIds(connection, """
            SELECT DISTINCT d.delivery_id FROM invitecode_reward_deliveries d
            LEFT JOIN invitecode_reward_actions a ON a.reward_delivery_id = d.delivery_id
            WHERE d.invitation_id IN (
                SELECT invitation_id FROM invitecode_invitations WHERE inviter_id = ? OR invitee_id = ?
            ) OR a.recipient_id = ? OR a.invitation_id IN (
                SELECT invitation_id FROM invitecode_invitations WHERE inviter_id = ? OR invitee_id = ?
            )
        """, id, id, id, id, id)
        deliveries.forEach { deliveryId ->
            val delivery = requireNotNull(row(connection, "invitecode_reward_deliveries", "delivery_id", deliveryId))
            check(delivery["status"] !in unsettled) {
                "Reward Delivery $deliveryId is unresolved; resolve it before anonymization."
            }
            queryIds(connection, "SELECT status FROM invitecode_reward_actions WHERE reward_delivery_id = ?", deliveryId)
                .forEach { status -> check(status !in unsettled) {
                    "Reward Delivery $deliveryId contains an unresolved action; resolve it before anonymization."
                } }
        }
        val codes = queryIds(connection, "SELECT code FROM invitecode_codes WHERE inviter_id = ?", id)
        return Scope(invitations, deliveries, codes)
    }

    private fun queryIds(connection: Connection, sql: String, vararg values: String): List<String> =
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { result -> buildList { while (result.next()) add(result.getString(1)) } }
        }

    private fun row(connection: Connection, table: String, key: String, id: String): Map<String, Any?>? =
        connection.prepareStatement("SELECT * FROM $table WHERE $key = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { result ->
                if (!result.next()) null else result.toMap().filterKeys { it !in setOf("idempotency_hash", "active_inviter_id") }
            }
        }

    private fun ResultSet.toMap(): Map<String, Any?> = buildMap {
        val metadata = metaData
        for (index in 1..metadata.columnCount) put(metadata.getColumnName(index), getObject(index))
    }

    private fun insert(connection: Connection, table: String, values: Map<String, Any?>) {
        val names = values.keys.toList()
        val placeholders = names.joinToString(",") { "?" }
        connection.prepareStatement("INSERT INTO $table (${names.joinToString(",")}) VALUES ($placeholders)").use { statement ->
            names.forEachIndexed { index, name -> statement.setObject(index + 1, values[name]) }
            check(statement.executeUpdate() == 1) { "Insert into $table did not affect one row." }
        }
    }

    private fun update(connection: Connection, sql: String, vararg values: String): Int =
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeUpdate()
        }
}
