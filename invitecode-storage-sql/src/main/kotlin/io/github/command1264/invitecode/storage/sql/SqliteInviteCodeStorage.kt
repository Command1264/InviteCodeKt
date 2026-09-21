package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.core.ActiveInviteCode
import io.github.command1264.invitecode.core.AuditRecord
import io.github.command1264.invitecode.core.AuditedClaimStateTransition
import io.github.command1264.invitecode.core.AuditedInvitationTransition
import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.InviteCodeStorage
import io.github.command1264.invitecode.core.InviteCodeTransaction
import io.github.command1264.invitecode.core.Invitation
import io.github.command1264.invitecode.core.InvitationTransaction
import io.github.command1264.invitecode.core.PlayerClaimState
import io.github.command1264.invitecode.core.QualificationFact
import io.github.command1264.invitecode.core.RetiredInviteCode
import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.core.StoredRewardActionTransition
import io.github.command1264.invitecode.core.StoredRewardDelivery
import io.github.command1264.invitecode.core.StoredRewardDeliveryTransition
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

class SqliteInviteCodeStorage private constructor(
    private val database: Path,
    private val blockingOperationGuard: BlockingOperationGuard,
) : InviteCodeStorage {
    override fun savePlayer(player: StoredPlayer) {
        write { SqlitePlayerRecords.save(it, player) }
    }

    override fun player(playerId: UUID): StoredPlayer? = read {
        SqlitePlayerRecords.find(it, playerId)
    }

    override fun activeInviteCode(inviterId: UUID): ActiveInviteCode? = read {
        SqliteInviteCodeRecords.findActive(it, inviterId)
    }

    override fun isInviteCodeReserved(code: InviteCode): Boolean = read {
        SqliteInviteCodeRecords.isReserved(it, code)
    }

    override fun saveInvitation(invitation: Invitation) {
        write { SqliteInvitationRecords.save(it, invitation, updateExisting = true) }
    }

    override fun invitation(invitationId: UUID): Invitation? = read {
        SqliteInvitationRecords.find(it, invitationId)
    }

    override fun saveQualificationFact(fact: QualificationFact) {
        write { SqliteQualificationRecords.save(it, fact) }
    }

    override fun qualificationFact(factId: UUID): QualificationFact? = read {
        SqliteQualificationRecords.find(it, factId)
    }

    override fun saveRewardAction(action: StoredRewardAction) {
        write { SqliteRewardRecords.saveAction(it, action) }
    }

    override fun rewardAction(actionId: UUID): StoredRewardAction? = read {
        SqliteRewardRecords.findAction(it, actionId)
    }

    override fun rewardActions(deliveryId: UUID): List<StoredRewardAction> = read {
        SqliteRewardRecords.findActions(it, deliveryId)
    }

    override fun pendingRewardActions(recipientId: UUID): List<StoredRewardAction> = read {
        SqliteRewardRecords.findPendingActions(it, recipientId)
    }

    override fun rewardActionTransitions(actionId: UUID): List<StoredRewardActionTransition> = read {
        SqliteRewardRecords.findActionTransitions(it, actionId)
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
            SqliteRewardRecords.saveDelivery(connection, delivery)
            actions.forEach { SqliteRewardRecords.saveAction(connection, it) }
        }
    }

    override fun rewardDelivery(deliveryId: UUID): StoredRewardDelivery? = read {
        SqliteRewardRecords.findDelivery(it, deliveryId)
    }

    override fun rewardDeliveryTransitions(deliveryId: UUID): List<StoredRewardDeliveryTransition> = read {
        SqliteRewardRecords.findDeliveryTransitions(it, deliveryId)
    }

    override fun saveAuditRecord(record: AuditRecord) {
        write { SqliteAuditRecords.save(it, record) }
    }

    override fun auditRecord(recordId: UUID): AuditRecord? = read {
        SqliteAuditRecords.find(it, recordId)
    }

    override fun saveAuditedInvitationTransition(transition: AuditedInvitationTransition) {
        write { connection ->
            SqliteInvitationRecords.transition(connection, transition)
            SqliteAuditRecords.save(connection, transition.auditRecord)
        }
    }

    override fun saveAuditedClaimStateTransition(transition: AuditedClaimStateTransition) {
        write { connection ->
            SqlitePlayerRecords.resetEligibility(connection, transition)
            SqliteAuditRecords.save(connection, transition.auditRecord)
        }
    }

    override fun <T> atomically(operation: (InviteCodeTransaction) -> T): T = write { connection ->
        operation(object : InviteCodeTransaction {
            override fun activeCode(inviterId: UUID): ActiveInviteCode? =
                SqliteInviteCodeRecords.findActive(connection, inviterId)

            override fun isReserved(code: InviteCode): Boolean =
                SqliteInviteCodeRecords.isReserved(connection, code)

            override fun activate(activeInviteCode: ActiveInviteCode) {
                SqliteInviteCodeRecords.activate(connection, activeInviteCode)
            }

            override fun replace(previous: RetiredInviteCode, current: ActiveInviteCode) {
                SqliteInviteCodeRecords.retire(connection, previous)
                SqliteInviteCodeRecords.activate(connection, current)
            }
        })
    }

    override fun <T> atomically(
        playerIds: Set<UUID>,
        operation: (InvitationTransaction) -> T,
    ): T = write { connection ->
        require(playerIds.isNotEmpty()) { "An Invitation transaction requires at least one player." }
        operation(object : InvitationTransaction {
            override fun claimState(playerId: UUID): PlayerClaimState =
                SqlitePlayerRecords.claimState(connection, playerId)

            override fun add(invitation: Invitation) {
                SqliteInvitationRecords.save(connection, invitation, updateExisting = false)
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
