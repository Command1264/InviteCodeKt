package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.api.InvitationStatus
import io.github.command1264.invitecode.core.AuditRecord
import io.github.command1264.invitecode.core.AuditedInvitationTransition
import io.github.command1264.invitecode.core.Invitation
import io.github.command1264.invitecode.core.InvitationAuditAction
import io.github.command1264.invitecode.core.InvitationAuditDetails
import io.github.command1264.invitecode.core.QualificationFact
import io.github.command1264.invitecode.core.QualificationFactOutcome
import java.sql.Connection
import java.time.Instant
import java.util.UUID

internal object SqliteInvitationRecords {
    fun save(connection: Connection, invitation: Invitation, updateExisting: Boolean) {
        require(invitation.status == InvitationStatus.CLAIMED) {
            "Only a newly claimed Invitation can use the initial persistence path."
        }
        if (updateExisting) {
            val current = find(connection, invitation.id)
            if (current != null) {
                if (current == invitation) return
                throw SqliteStorageException(
                    "Invitation ${invitation.id} already exists and cannot be replaced through the initial path.",
                )
            }
        }
        val inviterGeneration = SqlitePlayerRecords.eligibilityGeneration(connection, invitation.inviterId)
        val inviteeGeneration = SqlitePlayerRecords.eligibilityGeneration(connection, invitation.inviteeId)
        connection.prepareStatement(
            """
            INSERT INTO invitecode_invitations(
                invitation_id, inviter_id, inviter_eligibility_generation,
                invitee_id, invitee_eligibility_generation, status, claimed_at,
                qualified_at, qualification_policy_id, qualification_revision
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, invitation.id.toString())
            statement.setString(2, invitation.inviterId.toString())
            statement.setInt(3, inviterGeneration)
            statement.setString(4, invitation.inviteeId.toString())
            statement.setInt(5, inviteeGeneration)
            statement.setString(6, invitation.status.name)
            statement.setString(7, invitation.claimedAt.toString())
            statement.setString(8, invitation.qualifiedAt?.toString())
            statement.setString(9, invitation.qualificationPolicyId)
            statement.setString(10, invitation.qualificationRevision)
            statement.executeUpdate()
        }
    }

    fun transition(connection: Connection, transition: AuditedInvitationTransition) {
        val current = find(connection, transition.previousInvitation.id)
        if (current != transition.previousInvitation) {
            throw SqliteStorageException(
                "Invitation ${transition.previousInvitation.id} changed before the audited transition; " +
                    "reload it and retry the administrative operation.",
            )
        }
        update(connection, transition.invitation)
    }

    fun qualify(connection: Connection, invitation: Invitation) {
        require(invitation.status == InvitationStatus.QUALIFIED) {
            "Qualification persistence requires a qualified Invitation."
        }
        val current = find(connection, invitation.id)
            ?: throw SqliteStorageException("Invitation ${invitation.id} does not exist.")
        require(
            current.status == InvitationStatus.CLAIMED &&
                current.id == invitation.id &&
                current.inviterId == invitation.inviterId &&
                current.inviteeId == invitation.inviteeId &&
                current.claimedAt == invitation.claimedAt,
        ) { "Only the matching claimed Invitation can become qualified." }
        update(connection, invitation)
    }

    private fun update(connection: Connection, invitation: Invitation) {
        val inviterGeneration = SqlitePlayerRecords.eligibilityGeneration(connection, invitation.inviterId)
        connection.prepareStatement(
            """
            UPDATE invitecode_invitations SET
                inviter_id = ?,
                inviter_eligibility_generation = ?,
                status = ?,
                qualified_at = ?,
                qualification_policy_id = ?,
                qualification_revision = ?
            WHERE invitation_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, invitation.inviterId.toString())
            statement.setInt(2, inviterGeneration)
            statement.setString(3, invitation.status.name)
            statement.setString(4, invitation.qualifiedAt?.toString())
            statement.setString(5, invitation.qualificationPolicyId)
            statement.setString(6, invitation.qualificationRevision)
            statement.setString(7, invitation.id.toString())
            if (statement.executeUpdate() != 1) {
                throw SqliteStorageException("Invitation ${invitation.id} does not exist.")
            }
        }
    }

    fun find(connection: Connection, invitationId: UUID): Invitation? =
        connection.prepareStatement(
            """
            SELECT invitation_id, inviter_id, invitee_id, status, claimed_at,
                   qualified_at, qualification_policy_id, qualification_revision
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
                    qualificationPolicyId = result.getString("qualification_policy_id"),
                    qualificationRevision = result.getString("qualification_revision"),
                )
            }
        }
}

internal object SqliteQualificationRecords {
    fun save(connection: Connection, fact: QualificationFact) {
        val current = find(connection, fact.id)
        if (current != null) {
            if (current == fact) return
            throw SqliteStorageException("Qualification Fact ${fact.id} is immutable and already exists.")
        }
        connection.prepareStatement(
            """
            INSERT INTO invitecode_qualification_facts(
                fact_id, invitation_id, policy_id, qualification_revision,
                condition_id, outcome, recorded_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
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

    fun find(connection: Connection, factId: UUID): QualificationFact? =
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

internal object SqliteAuditRecords {
    fun save(connection: Connection, record: AuditRecord) {
        val details = AuditDetailsCodec.encode(record.details)
        connection.prepareStatement(
            """
            INSERT INTO invitecode_audit_records(
                audit_id, action, actor, reason, occurred_at, invitation_id, player_id,
                details_kind, previous_inviter_id, new_inviter_id,
                qualification_policy_id, qualification_revision
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            statement.setString(11, details.qualificationPolicyId)
            statement.setString(12, details.qualificationRevision)
            statement.executeUpdate()
        }
    }

    fun find(connection: Connection, recordId: UUID): AuditRecord? =
        connection.prepareStatement("SELECT * FROM invitecode_audit_records WHERE audit_id = ?").use { statement ->
            statement.setString(1, recordId.toString())
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                AuditRecord(
                    id = UUID.fromString(result.getString("audit_id")),
                    action = InvitationAuditAction.valueOf(result.getString("action")),
                    actor = result.getString("actor"),
                    reason = result.getString("reason"),
                    occurredAt = Instant.parse(result.getString("occurred_at")),
                    invitationId = result.getString("invitation_id")?.let(UUID::fromString),
                    playerId = result.getString("player_id")?.let(UUID::fromString),
                    details = AuditDetailsCodec.decode(
                        kind = result.getString("details_kind"),
                        previousInviterId = result.getString("previous_inviter_id"),
                        newInviterId = result.getString("new_inviter_id"),
                        qualificationPolicyId = result.getString("qualification_policy_id"),
                        qualificationRevision = result.getString("qualification_revision"),
                    ),
                )
            }
        }
}

private data class AuditDetailsColumns(
    val kind: String,
    val previousInviterId: UUID? = null,
    val newInviterId: UUID? = null,
    val qualificationPolicyId: String? = null,
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
            qualificationPolicyId = details.qualificationPolicyId,
            qualificationRevision = details.qualificationRevision,
        )
    }

    fun decode(
        kind: String,
        previousInviterId: String?,
        newInviterId: String?,
        qualificationPolicyId: String?,
        qualificationRevision: String?,
    ): InvitationAuditDetails = when (kind) {
        "NONE" -> InvitationAuditDetails.None
        "TRANSFER" -> InvitationAuditDetails.Transfer(
            previousInviterId = requiredUuid(previousInviterId, "previous inviter ID"),
            newInviterId = requiredUuid(newInviterId, "new inviter ID"),
        )
        "FORCE_QUALIFICATION" -> InvitationAuditDetails.ForceQualification(
            requireNotNull(qualificationPolicyId) {
                "Force Qualification Audit Record is missing its qualification policy ID."
            },
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
