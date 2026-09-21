package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.core.ActiveInviteCode
import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.InviteCodeTransaction
import io.github.command1264.invitecode.core.RetiredInviteCode
import io.github.command1264.invitecode.core.Invitation
import io.github.command1264.invitecode.core.InvitationTransaction
import io.github.command1264.invitecode.core.PlayerClaimState
import io.github.command1264.invitecode.api.InvitationStatus
import io.github.command1264.invitecode.core.AuditRecord
import io.github.command1264.invitecode.core.InvitationAuditAction
import io.github.command1264.invitecode.core.InvitationAuditDetails
import io.github.command1264.invitecode.core.InviteCodeStorage
import io.github.command1264.invitecode.core.QualificationFact
import io.github.command1264.invitecode.core.QualificationFactOutcome
import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.RewardDeliveryStatus
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.core.StoredRewardDelivery
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class SqliteInviteCodeStorage private constructor(
    private val database: Path,
    private val blockingOperationGuard: BlockingOperationGuard,
) : InviteCodeStorage {
    override fun savePlayer(player: StoredPlayer) {
        write { connection ->
            connection.prepareStatement(
                """
                INSERT INTO invitecode_players(player_id, first_seen_at, last_known_name)
                VALUES (?, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET
                    last_known_name = excluded.last_known_name
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, player.id.toString())
                statement.setString(2, player.firstSeenAt.toString())
                statement.setString(3, player.lastKnownName)
                statement.executeUpdate()
            }
        }
    }

    override fun player(playerId: UUID): StoredPlayer? {
        return read { connection ->
            connection.prepareStatement(
                "SELECT player_id, first_seen_at, last_known_name FROM invitecode_players WHERE player_id = ?",
            ).use { statement ->
                statement.setString(1, playerId.toString())
                statement.executeQuery().use { result ->
                    if (!result.next()) return@use null
                    StoredPlayer(
                        id = UUID.fromString(result.getString("player_id")),
                        firstSeenAt = Instant.parse(result.getString("first_seen_at")),
                        lastKnownName = result.getString("last_known_name"),
                    )
                }
            }
        }
    }

    override fun activeInviteCode(inviterId: UUID): ActiveInviteCode? = read { connection ->
        findActiveInviteCode(connection, inviterId)
    }

    override fun isInviteCodeReserved(code: InviteCode): Boolean = read { connection ->
        isReserved(connection, code)
    }

    override fun invitation(invitationId: UUID): Invitation? = read { connection ->
        findInvitation(connection, invitationId)
    }

    override fun saveInvitation(invitation: Invitation) {
        write { connection ->
            insertInvitation(connection, invitation, updateExisting = true)
        }
    }

    override fun saveQualificationFact(fact: QualificationFact) {
        write { connection ->
            connection.prepareStatement(
                """
                INSERT INTO invitecode_qualification_facts(
                    fact_id, invitation_id, policy_id, qualification_revision,
                    condition_id, outcome, recorded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(fact_id) DO UPDATE SET
                    outcome = excluded.outcome,
                    recorded_at = excluded.recorded_at
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, fact.id.toString())
                statement.setString(2, fact.invitationId.toString())
                statement.setString(3, fact.policyId)
                statement.setString(4, fact.qualificationRevision)
                statement.setString(5, fact.conditionId)
                statement.setString(6, fact.outcome.name)
                statement.setString(7, fact.recordedAt.toString())
                statement.executeUpdate()
            }
        }
    }

    override fun qualificationFact(factId: UUID): QualificationFact? = read { connection ->
        connection.prepareStatement(
            """
            SELECT fact_id, invitation_id, policy_id, qualification_revision,
                   condition_id, outcome, recorded_at
            FROM invitecode_qualification_facts WHERE fact_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, factId.toString())
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                QualificationFact(
                    id = UUID.fromString(result.getString("fact_id")),
                    invitationId = UUID.fromString(result.getString("invitation_id")),
                    policyId = result.getString("policy_id"),
                    qualificationRevision = result.getString("qualification_revision"),
                    conditionId = result.getString("condition_id"),
                    outcome = QualificationFactOutcome.valueOf(result.getString("outcome")),
                    recordedAt = Instant.parse(result.getString("recorded_at")),
                )
            }
        }
    }

    override fun saveRewardAction(action: StoredRewardAction) {
        write { connection ->
            upsertRewardAction(connection, action)
        }
    }

    override fun rewardAction(actionId: UUID): StoredRewardAction? = read { connection ->
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

    override fun saveRewardDelivery(
        delivery: StoredRewardDelivery,
        actions: List<StoredRewardAction>,
    ) {
        require(actions.isNotEmpty()) { "Reward Delivery must contain at least one Reward Action." }
        require(actions.all { it.rewardDeliveryId == delivery.id }) {
            "Every Reward Action must belong to Reward Delivery ${delivery.id}."
        }
        write { connection ->
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
            actions.forEach { upsertRewardAction(connection, it) }
        }
    }

    override fun rewardDelivery(deliveryId: UUID): StoredRewardDelivery? = read { connection ->
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
    }

    override fun saveAuditRecord(record: AuditRecord) {
        write { connection ->
            val details = AuditDetailsCodec.encode(record.details)
            connection.prepareStatement(
                """
                INSERT INTO invitecode_audit_records(
                    audit_id, action, actor, reason, occurred_at, invitation_id, player_id,
                    details_kind, previous_inviter_id, new_inviter_id, qualification_revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, record.id.toString())
                statement.setString(2, record.action.name)
                statement.setString(3, record.actor)
                statement.setString(4, record.reason)
                statement.setString(5, record.occurredAt.toString())
                statement.setString(6, record.invitationId?.toString())
                statement.setString(7, record.playerId?.toString())
                statement.setString(8, details.kind)
                statement.setString(9, details.previousInviterId?.toString())
                statement.setString(10, details.newInviterId?.toString())
                statement.setString(11, details.qualificationRevision)
                statement.executeUpdate()
            }
        }
    }

    override fun auditRecord(recordId: UUID): AuditRecord? = read { connection ->
        connection.prepareStatement(
            "SELECT * FROM invitecode_audit_records WHERE audit_id = ?",
        ).use { statement ->
            statement.setString(1, recordId.toString())
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                val details = AuditDetailsCodec.decode(
                    kind = result.getString("details_kind"),
                    previousInviterId = result.getString("previous_inviter_id"),
                    newInviterId = result.getString("new_inviter_id"),
                    qualificationRevision = result.getString("qualification_revision"),
                )
                AuditRecord(
                    id = UUID.fromString(result.getString("audit_id")),
                    action = InvitationAuditAction.valueOf(result.getString("action")),
                    actor = result.getString("actor"),
                    reason = result.getString("reason"),
                    occurredAt = Instant.parse(result.getString("occurred_at")),
                    invitationId = result.getString("invitation_id")?.let(UUID::fromString),
                    playerId = result.getString("player_id")?.let(UUID::fromString),
                    details = details,
                )
            }
        }
    }

    override fun <T> atomically(operation: (InviteCodeTransaction) -> T): T = write { connection ->
        operation(object : InviteCodeTransaction {
            override fun activeCode(inviterId: UUID): ActiveInviteCode? =
                findActiveInviteCode(connection, inviterId)

            override fun isReserved(code: InviteCode): Boolean =
                isReserved(connection, code)

            override fun activate(activeInviteCode: ActiveInviteCode) {
                connection.prepareStatement(
                    """
                    INSERT INTO invitecode_codes(code, inviter_id, activated_at, retired_at)
                    VALUES (?, ?, ?, NULL)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, activeInviteCode.code.normalized)
                    statement.setString(2, activeInviteCode.inviterId.toString())
                    statement.setString(3, activeInviteCode.activatedAt.toString())
                    statement.executeUpdate()
                }
            }

            override fun replace(previous: RetiredInviteCode, current: ActiveInviteCode) {
                val changed = connection.prepareStatement(
                    """
                    UPDATE invitecode_codes
                    SET retired_at = ?
                    WHERE code = ? AND inviter_id = ? AND retired_at IS NULL
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, previous.retiredAt.toString())
                    statement.setString(2, previous.code.normalized)
                    statement.setString(3, previous.inviterId.toString())
                    statement.executeUpdate()
                }
                check(changed == 1) { "Active Invite Code changed while it was being rotated." }
                activate(current)
            }
        })
    }

    override fun <T> atomically(
        playerIds: Set<UUID>,
        operation: (InvitationTransaction) -> T,
    ): T = write { connection ->
        require(playerIds.isNotEmpty()) { "An Invitation transaction requires at least one player." }
        operation(object : InvitationTransaction {
            override fun claimState(playerId: UUID): PlayerClaimState {
                val firstSeenAt = connection.prepareStatement(
                    "SELECT first_seen_at FROM invitecode_players WHERE player_id = ?",
                ).use { statement ->
                    statement.setString(1, playerId.toString())
                    statement.executeQuery().use { result ->
                        check(result.next()) { "Player $playerId must be stored before evaluating Claim Eligibility." }
                        Instant.parse(result.getString("first_seen_at"))
                    }
                }
                val roles = connection.prepareStatement(
                    """
                    SELECT
                        EXISTS(SELECT 1 FROM invitecode_invitations WHERE inviter_id = ?) AS is_inviter,
                        EXISTS(SELECT 1 FROM invitecode_invitations WHERE invitee_id = ?) AS has_invitation
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, playerId.toString())
                    statement.setString(2, playerId.toString())
                    statement.executeQuery().use { result ->
                        result.next()
                        result.getBoolean("is_inviter") to result.getBoolean("has_invitation")
                    }
                }
                return PlayerClaimState(playerId, firstSeenAt, roles.first, roles.second)
            }

            override fun add(invitation: Invitation) {
                insertInvitation(connection, invitation, updateExisting = false)
            }
        })
    }

    override fun close() = Unit

    private fun <T> read(operation: (Connection) -> T): T {
        blockingOperationGuard.checkAllowed()
        try {
            return connect(database).use { connection ->
                configure(connection)
                operation(connection)
            }
        } catch (error: SQLException) {
            throw storageFailure("read", error)
        }
    }

    private fun <T> write(operation: (Connection) -> T): T {
        blockingOperationGuard.checkAllowed()
        try {
            return connect(database).use { connection ->
                configure(connection)
                connection.createStatement().use { it.execute("BEGIN IMMEDIATE") }
                try {
                    val result = operation(connection)
                    connection.createStatement().use { it.execute("COMMIT") }
                    result
                } catch (error: Throwable) {
                    try {
                        connection.createStatement().use { it.execute("ROLLBACK") }
                    } catch (rollbackError: Throwable) {
                        error.addSuppressed(rollbackError)
                    }
                    throw error
                }
            }
        } catch (error: SQLException) {
            throw storageFailure("write", error)
        }
    }

    private fun storageFailure(operation: String, cause: SQLException) = SqliteStorageException(
        "SQLite $operation failed for ${database.toAbsolutePath()}; " +
            "the storage operation was rejected and no fallback backend was used.",
        cause,
    )

    private fun findActiveInviteCode(connection: Connection, inviterId: UUID): ActiveInviteCode? =
        connection.prepareStatement(
            """
            SELECT code, inviter_id, activated_at
            FROM invitecode_codes
            WHERE inviter_id = ? AND retired_at IS NULL
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, inviterId.toString())
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                ActiveInviteCode(
                    inviterId = UUID.fromString(result.getString("inviter_id")),
                    code = InviteCode.fromNormalized(result.getString("code")),
                    activatedAt = Instant.parse(result.getString("activated_at")),
                )
            }
        }

    private fun isReserved(connection: Connection, code: InviteCode): Boolean =
        connection.prepareStatement(
            "SELECT 1 FROM invitecode_codes WHERE code = ?",
        ).use { statement ->
            statement.setString(1, code.normalized)
            statement.executeQuery().use { it.next() }
        }

    private fun findInvitation(connection: Connection, invitationId: UUID): Invitation? =
        connection.prepareStatement(
            """
            SELECT invitation_id, inviter_id, invitee_id, status, claimed_at,
                   qualified_at, qualification_revision
            FROM invitecode_invitations
            WHERE invitation_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, invitationId.toString())
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                Invitation(
                    id = UUID.fromString(result.getString("invitation_id")),
                    inviterId = UUID.fromString(result.getString("inviter_id")),
                    inviteeId = UUID.fromString(result.getString("invitee_id")),
                    status = InvitationStatus.valueOf(result.getString("status")),
                    claimedAt = Instant.parse(result.getString("claimed_at")),
                    qualifiedAt = result.getString("qualified_at")?.let(Instant::parse),
                    qualificationRevision = result.getString("qualification_revision"),
                )
            }
        }

    private fun insertInvitation(
        connection: Connection,
        invitation: Invitation,
        updateExisting: Boolean,
    ) {
        val conflictClause = if (updateExisting) {
            """
            ON CONFLICT(invitation_id) DO UPDATE SET
                inviter_id = excluded.inviter_id,
                status = excluded.status,
                qualified_at = excluded.qualified_at,
                qualification_revision = excluded.qualification_revision
            """.trimIndent()
        } else {
            ""
        }
        connection.prepareStatement(
            """
            INSERT INTO invitecode_invitations(
                invitation_id, inviter_id, invitee_id, status, claimed_at,
                qualified_at, qualification_revision
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            $conflictClause
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, invitation.id.toString())
            statement.setString(2, invitation.inviterId.toString())
            statement.setString(3, invitation.inviteeId.toString())
            statement.setString(4, invitation.status.name)
            statement.setString(5, invitation.claimedAt.toString())
            statement.setString(6, invitation.qualifiedAt?.toString())
            statement.setString(7, invitation.qualificationRevision)
            statement.executeUpdate()
        }
    }

    private fun upsertRewardAction(connection: Connection, action: StoredRewardAction) {
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

    companion object {
        fun open(
            database: Path,
            blockingOperationGuard: BlockingOperationGuard,
        ): SqliteInviteCodeStorage {
            blockingOperationGuard.checkAllowed()
            connect(database).use { connection ->
                configure(connection)
                val version = try {
                    readSchemaVersion(connection)
                } catch (error: Exception) {
                    throw SqliteStorageException(
                        "SQLite database ${database.toAbsolutePath()} is not initialized; " +
                            "run the explicit schema migration before opening storage.",
                        error,
                    )
                }
                if (version != SqliteSchemaMigrator.currentVersion) {
                    throw SqliteStorageException(
                        "SQLite schema version $version does not match required version " +
                            "${SqliteSchemaMigrator.currentVersion}; run the explicit schema migration.",
                    )
                }
            }
            return SqliteInviteCodeStorage(database, blockingOperationGuard)
        }

        private fun configure(connection: Connection) {
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute("PRAGMA busy_timeout = 5000")
            }
        }
    }
}

private data class AuditDetailsColumns(
    val kind: String,
    val previousInviterId: UUID? = null,
    val newInviterId: UUID? = null,
    val qualificationRevision: String? = null,
)

private object AuditDetailsCodec {
    fun encode(details: InvitationAuditDetails): AuditDetailsColumns = when (details) {
        InvitationAuditDetails.None -> AuditDetailsColumns("NONE")
        is InvitationAuditDetails.Transfer -> AuditDetailsColumns(
            kind = "TRANSFER",
            previousInviterId = details.previousInviterId,
            newInviterId = details.newInviterId,
        )
        is InvitationAuditDetails.ForceQualification -> AuditDetailsColumns(
            kind = "FORCE_QUALIFICATION",
            qualificationRevision = details.qualificationRevision,
        )
    }

    fun decode(
        kind: String,
        previousInviterId: String?,
        newInviterId: String?,
        qualificationRevision: String?,
    ): InvitationAuditDetails = when (kind) {
        "NONE" -> InvitationAuditDetails.None
        "TRANSFER" -> InvitationAuditDetails.Transfer(
            previousInviterId = requiredUuid(previousInviterId, "previous inviter ID"),
            newInviterId = requiredUuid(newInviterId, "new inviter ID"),
        )
        "FORCE_QUALIFICATION" -> InvitationAuditDetails.ForceQualification(
            requireNotNull(qualificationRevision) {
                "Force Qualification Audit Record is missing its qualification revision."
            },
        )
        else -> throw SqliteStorageException("Unknown Audit Record details kind: $kind")
    }

    private fun requiredUuid(value: String?, field: String): UUID = UUID.fromString(
        requireNotNull(value) { "Transfer Audit Record is missing its $field." },
    )
}
