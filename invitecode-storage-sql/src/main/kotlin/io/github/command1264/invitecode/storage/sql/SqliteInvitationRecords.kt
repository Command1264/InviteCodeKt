package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.api.InvitationStatus
import io.github.command1264.invitecode.core.AuditRecord
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

    fun find(connection: Connection, invitationId: UUID): Invitation? =
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
}

internal object SqliteQualificationRecords {
    fun save(connection: Connection, fact: QualificationFact) {
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
