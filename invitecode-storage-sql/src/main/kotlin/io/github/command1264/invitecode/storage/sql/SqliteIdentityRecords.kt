package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.core.ActiveInviteCode
import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.PlayerClaimState
import io.github.command1264.invitecode.core.RetiredInviteCode
import io.github.command1264.invitecode.core.StoredPlayer
import java.sql.Connection
import java.time.Instant
import java.util.UUID

internal object SqlitePlayerRecords {
    fun save(connection: Connection, player: StoredPlayer) {
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

    fun find(connection: Connection, playerId: UUID): StoredPlayer? =
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

    fun claimState(connection: Connection, playerId: UUID): PlayerClaimState {
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
}

internal object SqliteInviteCodeRecords {
    fun findActive(connection: Connection, inviterId: UUID): ActiveInviteCode? =
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

    fun isReserved(connection: Connection, code: InviteCode): Boolean =
        connection.prepareStatement("SELECT 1 FROM invitecode_codes WHERE code = ?").use { statement ->
            statement.setString(1, code.normalized)
            statement.executeQuery().use { it.next() }
        }

    fun activate(connection: Connection, activeInviteCode: ActiveInviteCode) {
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

    fun retire(connection: Connection, retiredInviteCode: RetiredInviteCode) {
        val changed = connection.prepareStatement(
            """
            UPDATE invitecode_codes
            SET retired_at = ?
            WHERE code = ? AND inviter_id = ? AND retired_at IS NULL
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, retiredInviteCode.retiredAt.toString())
            statement.setString(2, retiredInviteCode.code.normalized)
            statement.setString(3, retiredInviteCode.inviterId.toString())
            statement.executeUpdate()
        }
        check(changed == 1) { "Active Invite Code changed while it was being rotated." }
    }
}
