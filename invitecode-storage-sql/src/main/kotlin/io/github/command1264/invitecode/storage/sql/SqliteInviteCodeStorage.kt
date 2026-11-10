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
import io.github.command1264.invitecode.core.InviterInvitationStats
import io.github.command1264.invitecode.core.PlayerClaimState
import io.github.command1264.invitecode.core.PlayerNetworkHash
import io.github.command1264.invitecode.core.QualificationFact
import io.github.command1264.invitecode.core.RetiredInviteCode
import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.RewardAdministrationContext
import io.github.command1264.invitecode.core.RewardBacklogCounts
import io.github.command1264.invitecode.core.RewardDeliveryStatus
import io.github.command1264.invitecode.core.RewardDeliveryPlan
import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.core.StoredRewardActionTransition
import io.github.command1264.invitecode.core.StoredRewardDelivery
import io.github.command1264.invitecode.core.StoredRewardDeliveryTransition
import io.github.command1264.invitecode.core.UncertainRewardActionResolution
import java.time.Instant
import java.time.LocalDate
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

/** Shared JDBC operations; backend-specific connection and transaction rules stay at this boundary. */
internal class JdbcInviteCodeStorage private constructor(
    private val database: Path?,
    private val mysqlSource: DataSource?,
    private val blockingOperationGuard: BlockingOperationGuard,
    private val reservationKey: CodeReservationKey?,
) : InviteCodeStorage {
    override fun qualifiedInvitationCount(inviterId: UUID): Int = read {
        SqliteInvitationRecords.countQualifiedForInviter(it, inviterId)
    }

    override fun savePlayer(player: StoredPlayer) {
        write { SqlitePlayerRecords.save(it, player) }
    }

    override fun recordLoginDay(playerId: UUID, loginDate: LocalDate) {
        write { SqlitePlayerRecords.recordLoginDay(it, playerId, loginDate) }
    }

    override fun loginDays(playerId: UUID): Int = read {
        SqlitePlayerRecords.loginDays(it, playerId)
    }

    override fun recordNetworkHash(record: PlayerNetworkHash) {
        write { SqliteNetworkHashRecords.save(it, record) }
    }

    override fun purgeExpiredNetworkHashes(expiredAt: Instant): Int = write {
        SqliteNetworkHashRecords.purgeExpired(it, expiredAt)
    }

    override fun purgeNetworkHashesWithDifferentKey(keyId: String): Int {
        require(Regex("[0-9a-f]{64}").matches(keyId)) { "Network hash key ID is invalid." }
        return write { SqliteNetworkHashRecords.purgeDifferentKey(it, keyId) }
    }

    override fun player(playerId: UUID): StoredPlayer? = read {
        SqlitePlayerRecords.find(it, playerId)
    }

    override fun playersByLastKnownName(lastKnownName: String): List<StoredPlayer> {
        require(lastKnownName.isNotBlank()) { "Last known player name must not be blank." }
        return read { SqlitePlayerRecords.findByLastKnownName(it, lastKnownName) }
    }

    override fun claimState(playerId: UUID): PlayerClaimState = read {
        SqlitePlayerRecords.claimState(it, playerId)
    }

    override fun activeInviteCode(inviterId: UUID): ActiveInviteCode? = read {
        SqliteInviteCodeRecords.findActive(it, inviterId)
    }

    override fun activeInviteCode(inviteCode: InviteCode): ActiveInviteCode? = read {
        SqliteInviteCodeRecords.findActive(it, inviteCode)
    }

    override fun isInviteCodeReserved(code: InviteCode): Boolean = read {
        SqliteInviteCodeRecords.isReserved(it, code, reservationKey)
    }

    override fun saveInvitation(invitation: Invitation) {
        write { SqliteInvitationRecords.save(it, invitation, updateExisting = true) }
    }

    override fun invitation(invitationId: UUID): Invitation? = read {
        SqliteInvitationRecords.find(it, invitationId)
    }

    override fun invitationsByInvitee(inviteeId: UUID): List<Invitation> = read {
        SqliteInvitationRecords.findByInvitee(it, inviteeId)
    }

    override fun invitationsByInviter(inviterId: UUID): List<Invitation> = read {
        SqliteInvitationRecords.findByInviter(it, inviterId)
    }

    override fun currentInviteeInvitationStatus(inviteeId: UUID) = read {
        SqliteInvitationRecords.currentStatusByInvitee(it, inviteeId)
    }

    override fun inviterInvitationStats(inviterId: UUID): InviterInvitationStats = read {
        SqliteInvitationRecords.statsForInviter(it, inviterId)
    }

    override fun saveQualifiedInvitation(invitation: Invitation, facts: List<QualificationFact>) {
        require(facts.isNotEmpty()) { "Qualification requires at least one persisted Qualification Fact." }
        require(
            facts.all {
                it.invitationId == invitation.id &&
                    it.policyId == invitation.qualificationPolicyId &&
                    it.qualificationRevision == invitation.qualificationRevision
            },
        ) { "Every Qualification Fact must match the Invitation policy ID and revision." }
        write { connection ->
            SqliteInvitationRecords.qualify(connection, invitation)
            facts.forEach { SqliteQualificationRecords.save(connection, it) }
        }
    }

    override fun saveQualifiedInvitationWithRewards(
        invitation: Invitation,
        facts: List<QualificationFact>,
        rewardPlans: List<RewardDeliveryPlan>,
    ) {
        require(facts.isNotEmpty()) { "Qualification requires at least one persisted Qualification Fact." }
        require(
            facts.all {
                it.invitationId == invitation.id &&
                    it.policyId == invitation.qualificationPolicyId &&
                    it.qualificationRevision == invitation.qualificationRevision
            },
        ) {
            "Every Qualification Fact must match Invitation ${invitation.id} and its policy revision."
        }
        require(rewardPlans.flatMap { it.actions }.all { it.status == RewardActionStatus.PENDING }) {
            "New Reward Actions must start in PENDING state."
        }
        require(rewardPlans.all { it.delivery.status == RewardDeliveryStatus.PENDING }) {
            "New Reward Deliveries must start in PENDING state."
        }
        write { connection ->
            SqliteInvitationRecords.qualify(connection, invitation)
            facts.forEach { SqliteQualificationRecords.save(connection, it) }
            rewardPlans.forEach { plan ->
                SqliteRewardRecords.saveDelivery(connection, plan.delivery)
                plan.actions.forEach { SqliteRewardRecords.saveAction(connection, it) }
            }
        }
    }

    override fun saveQualificationFact(fact: QualificationFact) {
        write { SqliteQualificationRecords.save(it, fact) }
    }

    override fun saveQualificationFacts(facts: List<QualificationFact>) {
        require(facts.isNotEmpty()) { "Qualification evaluation requires at least one fact." }
        require(
            facts.map { Triple(it.invitationId, it.policyId, it.qualificationRevision) }.distinct().size == 1,
        ) { "Qualification Facts in one evaluation must share Invitation, policy ID, and revision." }
        write { connection ->
            facts.forEach { SqliteQualificationRecords.save(connection, it) }
        }
    }

    override fun qualificationFact(factId: UUID): QualificationFact? = read {
        SqliteQualificationRecords.find(it, factId)
    }

    override fun qualificationFacts(invitationId: UUID): List<QualificationFact> = read {
        SqliteQualificationRecords.findByInvitation(it, invitationId)
    }

    override fun saveRewardAction(action: StoredRewardAction) {
        write { connection ->
            requireNotNull(SqliteRewardRecords.findAction(connection, action.id)) {
                "Reward Action ${action.id} must be created with its complete Reward Delivery."
            }
            SqliteRewardRecords.saveAction(connection, action)
            updateDeliveryAggregate(connection, action.rewardDeliveryId, action.updatedAt)
        }
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

    override fun recoverInterruptedRewardActions(recoveredAt: Instant): Int = write { connection ->
        val interrupted = SqliteRewardRecords.findActionsByStatus(connection, RewardActionStatus.PROCESSING)
        interrupted.forEach { action ->
            SqliteRewardRecords.saveAction(
                connection,
                action.copy(
                    status = RewardActionStatus.UNCERTAIN,
                    updatedAt = recoveredAt,
                    lastError = "Interrupted while processing\ndelivery result is unknown.",
                ),
            )
        }
        interrupted.map { it.rewardDeliveryId }.toSet().forEach { deliveryId ->
            updateDeliveryAggregate(connection, deliveryId, recoveredAt)
        }
        interrupted.size
    }

    override fun resolveUncertainRewardAction(
        actionId: UUID,
        resolution: UncertainRewardActionResolution,
        context: RewardAdministrationContext,
    ): StoredRewardAction = write { connection ->
        val current = requireNotNull(SqliteRewardRecords.findAction(connection, actionId)) {
            "Reward Action $actionId does not exist."
        }
        require(current.status == RewardActionStatus.UNCERTAIN) {
            "Only an uncertain Reward Action can be resolved\ncurrent status is ${current.status}."
        }
        val resolved = current.copy(
            status = when (resolution) {
                UncertainRewardActionResolution.RETRY -> RewardActionStatus.PENDING
                UncertainRewardActionResolution.MARK_DELIVERED -> RewardActionStatus.DELIVERED
            },
            updatedAt = context.occurredAt,
            lastError = null,
        )
        SqliteRewardRecords.saveAction(connection, resolved, context)
        updateDeliveryAggregate(connection, resolved.rewardDeliveryId, context.occurredAt, context)
        resolved
    }

    override fun saveRewardDelivery(
        delivery: StoredRewardDelivery,
        actions: List<StoredRewardAction>,
    ) {
        require(actions.isNotEmpty()) { "Reward Delivery must contain at least one Reward Action." }
        require(actions.all { it.rewardDeliveryId == delivery.id }) {
            "Every Reward Action must belong to Reward Delivery ${delivery.id}."
        }
        require(actions.all { it.invitationId == delivery.invitationId }) {
            "Every Reward Action must have the same Invitation as Reward Delivery ${delivery.id}."
        }
        require(actions.map { it.id }.distinct().size == actions.size) {
            "Reward Delivery ${delivery.id} cannot contain duplicate Reward Action IDs."
        }
        require(actions.map { it.position }.distinct().size == actions.size) {
            "Reward Delivery ${delivery.id} cannot contain duplicate Reward Action positions."
        }
        require(delivery.status == aggregateStatus(actions)) {
            "Reward Delivery ${delivery.id} status ${delivery.status} does not match its Action states."
        }
        write { connection ->
            val isNewDelivery = SqliteRewardRecords.findDelivery(connection, delivery.id) == null
            require(
                !isNewDelivery ||
                    delivery.status == RewardDeliveryStatus.PENDING &&
                    actions.all { it.status == RewardActionStatus.PENDING },
            ) { "A new Reward Delivery and all of its Actions must start in PENDING state." }
            val existingActionIds = SqliteRewardRecords.findActions(connection, delivery.id).map { it.id }.toSet()
            require(existingActionIds.isEmpty() || existingActionIds == actions.map { it.id }.toSet()) {
                "Reward Delivery ${delivery.id} must update its complete Reward Action set."
            }
            SqliteRewardRecords.saveDelivery(connection, delivery)
            actions.forEach { SqliteRewardRecords.saveAction(connection, it) }
        }
    }

    override fun rewardDelivery(deliveryId: UUID): StoredRewardDelivery? = read {
        SqliteRewardRecords.findDelivery(it, deliveryId)
    }

    override fun rewardDeliveries(invitationId: UUID): List<StoredRewardDelivery> = read {
        SqliteRewardRecords.findDeliveriesByInvitation(it, invitationId)
    }

    override fun rewardBacklogCounts(): RewardBacklogCounts = read {
        SqliteRewardRecords.backlogCounts(it)
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

    override fun auditHistoryByInvitation(invitationId: UUID, limit: Int): List<AuditRecord> = read {
        SqliteAuditRecords.findByInvitation(it, invitationId, limit)
    }

    override fun auditHistoryByPlayer(playerId: UUID, limit: Int): List<AuditRecord> = read {
        SqliteAuditRecords.findByPlayer(it, playerId, limit)
    }

    override fun saveAuditedInvitationTransition(transition: AuditedInvitationTransition) {
        write { connection ->
            SqliteInvitationRecords.transition(connection, transition)
            SqliteAuditRecords.save(connection, transition.auditRecord)
        }
    }

    override fun saveForcedQualificationWithRewards(
        transition: AuditedInvitationTransition,
        rewardPlans: List<RewardDeliveryPlan>,
    ) {
        require(transition.auditRecord.action == io.github.command1264.invitecode.core.InvitationAuditAction.FORCE_QUALIFIED) {
            "Forced Qualification persistence requires a FORCE_QUALIFIED audit transition."
        }
        write { connection ->
            SqliteInvitationRecords.transition(connection, transition)
            SqliteAuditRecords.save(connection, transition.auditRecord)
            rewardPlans.forEach { plan ->
                SqliteRewardRecords.saveDelivery(connection, plan.delivery)
                plan.actions.forEach { SqliteRewardRecords.saveAction(connection, it) }
            }
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
                SqliteInviteCodeRecords.isReserved(connection, code, reservationKey)

            override fun activate(activeInviteCode: ActiveInviteCode) {
                check(!isReserved(activeInviteCode.code)) { "Invite Code has already been reserved." }
                SqliteInviteCodeRecords.activate(connection, activeInviteCode)
            }

            override fun replace(previous: RetiredInviteCode, current: ActiveInviteCode) {
                SqliteInviteCodeRecords.retire(connection, previous)
                check(!isReserved(current.code)) { "Invite Code has already been reserved." }
                SqliteInviteCodeRecords.activate(connection, current)
            }

            override fun saveAuditRecord(record: AuditRecord) {
                SqliteAuditRecords.save(connection, record)
            }
        })
    }

    override fun <T> atomically(
        playerIds: Set<UUID>,
        operation: (InvitationTransaction) -> T,
    ): T = write { connection ->
        require(playerIds.isNotEmpty()) { "An Invitation transaction requires at least one player." }
        if (mysqlSource != null) {
            playerIds.sortedBy(UUID::toString).forEach { playerId ->
                connection.prepareStatement(
                    "SELECT player_id FROM invitecode_players WHERE player_id = ? FOR UPDATE",
                ).use { statement ->
                    statement.setString(1, playerId.toString())
                    statement.executeQuery().use { result ->
                        check(result.next()) { "Player $playerId must exist before Claim." }
                    }
                }
            }
        }
        operation(object : InvitationTransaction {
            override fun activeInviter(inviteCode: InviteCode): UUID? =
                SqliteInviteCodeRecords.findActive(connection, inviteCode)?.inviterId

            override fun claimState(playerId: UUID): PlayerClaimState =
                SqlitePlayerRecords.claimState(connection, playerId)

            override fun hasMatchingNetworkHash(
                firstPlayerId: UUID,
                secondPlayerId: UUID,
                evaluatedAt: Instant,
            ): Boolean = SqliteNetworkHashRecords.matches(
                connection,
                firstPlayerId,
                secondPlayerId,
                evaluatedAt,
            )

            override fun add(invitation: Invitation) {
                SqliteInvitationRecords.save(connection, invitation, updateExisting = false)
            }
        })
    }

    override fun close() = Unit

    fun previewAnonymization(playerId: UUID): AnonymizationPreview = read {
        OfflinePlayerAnonymizer.preview(it, playerId)
    }

    fun anonymizePlayer(playerId: UUID): AnonymizationResult {
        val key = requireNotNull(reservationKey) { "Anti-abuse secret is required for anonymization." }
        val source = mysqlSource ?: return write { OfflinePlayerAnonymizer.anonymize(it, playerId, key.keyId, key::hash) }
        // Maintenance tools share this schema-scoped lock across processes, including through commit.
        return source.connection.use { lockConnection ->
            val lockName = "ic-anon:" + lockConnection.createStatement().use { statement ->
                statement.executeQuery("SELECT LEFT(SHA2(DATABASE(), 256), 40)").use { rows ->
                    check(rows.next()) { "MySQL did not return the selected schema." }
                    rows.getString(1)
                }
            }
            val acquired = lockConnection.prepareStatement("SELECT GET_LOCK(?, 10)").use { statement ->
                statement.setString(1, lockName)
                statement.executeQuery().use { rows -> rows.next() && rows.getInt(1) == 1 }
            }
            check(acquired) { "MySQL anonymization lock timed out\ncheck other maintenance processes." }
            try {
                write { OfflinePlayerAnonymizer.anonymize(it, playerId, key.keyId, key::hash) }
            } finally {
                lockConnection.prepareStatement("SELECT RELEASE_LOCK(?)").use { statement ->
                    statement.setString(1, lockName)
                    statement.executeQuery().close()
                }
            }
        }
    }

    private fun <T> read(operation: (Connection) -> T): T {
        blockingOperationGuard.checkAllowed()
        try {
            return connection().use { connection ->
                if (mysqlSource == null) configure(connection)
                operation(connection)
            }
        } catch (error: SQLException) {
            throw storageFailure("read", error)
        }
    }

    private fun <T> write(operation: (Connection) -> T): T {
        blockingOperationGuard.checkAllowed()
        var retries = 0
        while (true) {
            try {
                return connection().use { connection ->
                    if (mysqlSource == null) {
                        configure(connection)
                        executeImmediateWrite(connection, requireNotNull(database), operation)
                    } else executeMysqlWrite(connection, operation)
                }
            } catch (error: SQLException) {
                if (mysqlSource != null && retries < 2 && (error.errorCode == 1213 || error.sqlState == "40001")) {
                    retries++
                    continue
                }
                throw storageFailure("write", error)
            }
        }
    }

    private fun connection(): Connection = mysqlSource?.connection ?: connect(requireNotNull(database))

    private fun <T> executeMysqlWrite(connection: Connection, operation: (Connection) -> T): T {
        connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        connection.autoCommit = false
        try {
            val result = operation(connection)
            connection.commit()
            return result
        } catch (error: Throwable) {
            try {
                connection.rollback()
            } catch (rollbackError: Throwable) {
                throw MysqlStorageException(
                    "MySQL write and rollback both failed\nstate is uncertain. Stop writes and inspect the database.",
                )
            }
            throw error
        }
    }

    private fun updateDeliveryAggregate(
        connection: Connection,
        deliveryId: UUID,
        updatedAt: Instant,
        administration: RewardAdministrationContext? = null,
    ) {
        val delivery = requireNotNull(SqliteRewardRecords.findDelivery(connection, deliveryId)) {
            "Reward Delivery $deliveryId does not exist."
        }
        val actions = SqliteRewardRecords.findActions(connection, deliveryId)
        val status = aggregateStatus(actions)
        if (status != delivery.status) {
            SqliteRewardRecords.saveDelivery(
                connection,
                delivery.copy(status = status, updatedAt = updatedAt),
                administration,
            )
        }
    }

    private fun storageFailure(operation: String, cause: SQLException): IllegalStateException =
        if (mysqlSource != null) MysqlStorageException(
            "MySQL $operation failed (SQLState ${cause.sqlState ?: "unknown"}, code ${cause.errorCode})\n" +
                "check database availability, schema and pool capacity. The operation was rejected.",
        ) else SqliteStorageException(
            "SQLite $operation failed for ${requireNotNull(database).toAbsolutePath()}\n" +
                "the storage operation was rejected and no fallback backend was used.",
            cause,
        )

    companion object {
        private fun aggregateStatus(actions: List<StoredRewardAction>): RewardDeliveryStatus = when {
            actions.any { it.status == RewardActionStatus.UNCERTAIN } -> RewardDeliveryStatus.UNCERTAIN
            actions.any { it.status == RewardActionStatus.FAILED } -> RewardDeliveryStatus.FAILED
            actions.all {
                it.status == RewardActionStatus.DELIVERED || it.status == RewardActionStatus.EXPIRED
            } -> RewardDeliveryStatus.DELIVERED
            actions.any { it.status != RewardActionStatus.PENDING } -> RewardDeliveryStatus.PROCESSING
            else -> RewardDeliveryStatus.PENDING
        }

        fun openSqlite(
            database: Path,
            blockingOperationGuard: BlockingOperationGuard,
            reservationKey: CodeReservationKey? = null,
        ): JdbcInviteCodeStorage {
            blockingOperationGuard.checkAllowed()
            connect(database).use { connection ->
                configure(connection)
                val version = try {
                    readSchemaVersion(connection)
                } catch (error: Exception) {
                    throw SqliteStorageException(
                        "SQLite database ${database.toAbsolutePath()} is not initialized\n" +
                            "run the explicit schema migration before opening storage.",
                        error,
                    )
                }
                if (version != SqliteSchemaMigrator.currentVersion) {
                    throw SqliteStorageException(
                        "SQLite schema version $version does not match required version " +
                            "${SqliteSchemaMigrator.currentVersion}\nrun the explicit schema migration.",
                    )
                }
                if (reservationKey != null) {
                    val otherKey = connection.prepareStatement(
                        "SELECT 1 FROM invitecode_reserved_code_hashes WHERE key_id <> ? LIMIT 1",
                    ).use { statement ->
                        statement.setString(1, reservationKey.keyId)
                        statement.executeQuery().use { it.next() }
                    }
                    check(!otherKey) { "Reserved Invite Codes use another anti-abuse key\nrestore the original secret." }
                }
            }
            return JdbcInviteCodeStorage(database, null, blockingOperationGuard, reservationKey)
        }

        fun openMysql(dataSource: DataSource, guard: BlockingOperationGuard, reservationKey: CodeReservationKey? = null): JdbcInviteCodeStorage {
            guard.checkAllowed()
            dataSource.connection.use { connection ->
                MysqlSchemaMigrator.requireCurrent(connection)
                if (reservationKey != null) {
                    val storedKey = connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT network_key_id FROM invitecode_metadata WHERE singleton = 1").use { rows ->
                            if (rows.next()) rows.getString(1) else null
                        }
                    }
                    check(storedKey == reservationKey.keyId) {
                        "MySQL anti-abuse secret does not match the database\nrestore the shared secret."
                    }
                }
            }
            return JdbcInviteCodeStorage(null, dataSource, guard, reservationKey)
        }

        private fun configure(connection: Connection) {
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute("PRAGMA busy_timeout = 5000")
            }
        }
    }
}

class SqliteInviteCodeStorage private constructor(
    private val delegate: JdbcInviteCodeStorage,
) : InviteCodeStorage by delegate {
    override fun close() = delegate.close()
    fun previewAnonymization(playerId: UUID): AnonymizationPreview = delegate.previewAnonymization(playerId)
    fun anonymizePlayer(playerId: UUID): AnonymizationResult = delegate.anonymizePlayer(playerId)

    companion object {
        fun open(database: Path, guard: BlockingOperationGuard, reservationKey: CodeReservationKey? = null): SqliteInviteCodeStorage =
            SqliteInviteCodeStorage(JdbcInviteCodeStorage.openSqlite(database, guard, reservationKey))
    }
}

internal fun <T> executeImmediateWrite(
    connection: Connection,
    database: Path,
    operation: (Connection) -> T,
): T {
    connection.createStatement().use { it.execute("BEGIN IMMEDIATE") }
    try {
        val result = operation(connection)
        connection.createStatement().use { it.execute("COMMIT") }
        return result
    } catch (error: Throwable) {
        try {
            connection.createStatement().use { it.execute("ROLLBACK") }
        } catch (rollbackError: Throwable) {
            val uncertain = SqliteStorageException(
                "SQLite write failed for ${database.toAbsolutePath()} and rollback also failed\n" +
                    "the database state is uncertain. Preserve it and inspect both causes before retrying.",
                error,
            )
            uncertain.addSuppressed(rollbackError)
            throw uncertain
        }
        throw error
    }
}
