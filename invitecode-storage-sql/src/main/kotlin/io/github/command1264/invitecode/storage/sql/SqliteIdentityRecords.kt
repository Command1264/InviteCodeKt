package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.core.ActiveInviteCode
import io.github.command1264.invitecode.core.AuditedClaimStateTransition
import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.PlayerClaimState
import io.github.command1264.invitecode.core.PlayerNetworkHash
import io.github.command1264.invitecode.core.RetiredInviteCode
import io.github.command1264.invitecode.core.StoredPlayer
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal object SqlitePlayerRecords {
    fun save(connection: Connection, player: StoredPlayer) {
        connection.prepareStatement(
            if (connection.isMysql()) """
            INSERT INTO invitecode_players(player_id, first_seen_at, last_known_name)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                last_known_name = COALESCE(VALUES(last_known_name), last_known_name)
            """.trimIndent() else """
            INSERT INTO invitecode_players(player_id, first_seen_at, last_known_name)
            VALUES (?, ?, ?)
            ON CONFLICT(player_id) DO UPDATE SET
                last_known_name = COALESCE(excluded.last_known_name, invitecode_players.last_known_name)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, player.id.toString())
            statement.setString(2, player.firstSeenAt.toSqlText(connection))
            statement.setString(3, player.lastKnownName)
            statement.executeUpdate()
        }
    }

    fun findByLastKnownName(connection: Connection, lastKnownName: String): List<StoredPlayer> =
        connection.prepareStatement(
            """
            SELECT player_id, first_seen_at, last_known_name
            FROM invitecode_players
            WHERE last_known_name = ? ${if (connection.isMysql()) "" else "COLLATE NOCASE"}
            ORDER BY player_id
            LIMIT 2
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, lastKnownName)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            StoredPlayer(
                                id = UUID.fromString(result.getString("player_id")),
                                firstSeenAt = Instant.parse(result.getString("first_seen_at")),
                                lastKnownName = result.getString("last_known_name"),
                            ),
                        )
                    }
                }
            }
        }

    fun recordLoginDay(connection: Connection, playerId: UUID, loginDate: LocalDate) {
        connection.prepareStatement(
            if (connection.isMysql())
                "INSERT INTO invitecode_player_login_days(player_id, login_date) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE login_date = VALUES(login_date)"
            else "INSERT OR IGNORE INTO invitecode_player_login_days(player_id, login_date) VALUES (?, ?)",
        ).use { statement ->
            statement.setString(1, playerId.toString())
            statement.setString(2, loginDate.toString())
            statement.executeUpdate()
        }
    }

    fun loginDays(connection: Connection, playerId: UUID): Int = connection.prepareStatement(
        "SELECT COUNT(*) FROM invitecode_player_login_days WHERE player_id = ?",
    ).use { statement ->
        statement.setString(1, playerId.toString())
        statement.executeQuery().use { result ->
            result.next()
            result.getInt(1)
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
        val playerState = connection.prepareStatement(
            "SELECT first_seen_at, eligibility_generation FROM invitecode_players WHERE player_id = ?",
        ).use { statement ->
            statement.setString(1, playerId.toString())
            statement.executeQuery().use { result ->
                check(result.next()) { "Player $playerId must be stored before evaluating Claim Eligibility." }
                Instant.parse(result.getString("first_seen_at")) to result.getInt("eligibility_generation")
            }
        }
        val roles = connection.prepareStatement(
            """
            SELECT
                EXISTS(
                    SELECT 1 FROM invitecode_codes
                    WHERE inviter_id = ? AND retired_at IS NULL
                ) OR EXISTS(
                    SELECT 1 FROM invitecode_invitations
                    WHERE inviter_id = ? AND inviter_eligibility_generation = ?
                ) AS is_inviter,
                EXISTS(
                    SELECT 1 FROM invitecode_invitations
                    WHERE invitee_id = ? AND invitee_eligibility_generation = ?
                ) AS has_invitation
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, playerId.toString())
            statement.setString(2, playerId.toString())
            statement.setInt(3, playerState.second)
            statement.setString(4, playerId.toString())
            statement.setInt(5, playerState.second)
            statement.executeQuery().use { result ->
                result.next()
                result.getBoolean("is_inviter") to result.getBoolean("has_invitation")
            }
        }
        return PlayerClaimState(playerId, playerState.first, roles.first, roles.second)
    }

    fun eligibilityGeneration(connection: Connection, playerId: UUID): Int =
        connection.prepareStatement(
            "SELECT eligibility_generation FROM invitecode_players WHERE player_id = ?",
        ).use { statement ->
            statement.setString(1, playerId.toString())
            statement.executeQuery().use { result ->
                check(result.next()) { "Player $playerId must be stored before creating an Invitation." }
                result.getInt("eligibility_generation")
            }
        }

    fun resetEligibility(connection: Connection, transition: AuditedClaimStateTransition) {
        val changed = connection.prepareStatement(
            """
            UPDATE invitecode_players
            SET first_seen_at = ?, eligibility_generation = eligibility_generation + 1
            WHERE player_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, transition.state.firstSeenAt.toSqlText(connection))
            statement.setString(2, transition.state.playerId.toString())
            statement.executeUpdate()
        }
        check(changed == 1) {
            "Player ${transition.state.playerId} must be stored before resetting Claim Eligibility."
        }
    }
}

internal object SqliteNetworkHashRecords {
    fun save(connection: Connection, record: PlayerNetworkHash) {
        connection.prepareStatement(
            if (connection.isMysql()) """
            INSERT INTO invitecode_player_network_hashes(player_id, key_id, network_hash, observed_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                observed_at = VALUES(observed_at),
                expires_at = VALUES(expires_at)
            """.trimIndent() else """
            INSERT INTO invitecode_player_network_hashes(player_id, key_id, network_hash, observed_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(player_id, key_id, network_hash) DO UPDATE SET
                observed_at = excluded.observed_at,
                expires_at = excluded.expires_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, record.playerId.toString())
            statement.setString(2, record.keyId)
            statement.setString(3, record.hash)
            statement.setString(4, record.observedAt.toSqlText(connection))
            statement.setString(5, record.expiresAt.toSqlText(connection))
            statement.executeUpdate()
        }
    }

    fun purgeExpired(connection: Connection, expiredAt: Instant): Int = connection.prepareStatement(
        "DELETE FROM invitecode_player_network_hashes WHERE expires_at <= ?",
    ).use { statement ->
        statement.setString(1, expiredAt.toSqlText(connection))
        statement.executeUpdate()
    }

    fun purgeDifferentKey(connection: Connection, keyId: String): Int = connection.prepareStatement(
        "DELETE FROM invitecode_player_network_hashes WHERE key_id <> ?",
    ).use { statement ->
        statement.setString(1, keyId)
        statement.executeUpdate()
    }

    fun matches(
        connection: Connection,
        firstPlayerId: UUID,
        secondPlayerId: UUID,
        evaluatedAt: Instant,
    ): Boolean = connection.prepareStatement(
        """
        SELECT 1
        FROM invitecode_player_network_hashes first_hash
        INNER JOIN invitecode_player_network_hashes second_hash
            ON second_hash.key_id = first_hash.key_id
           AND second_hash.network_hash = first_hash.network_hash
        WHERE first_hash.player_id = ?
          AND second_hash.player_id = ?
          AND first_hash.expires_at > ?
          AND second_hash.expires_at > ?
        LIMIT 1
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, firstPlayerId.toString())
        statement.setString(2, secondPlayerId.toString())
        statement.setString(3, evaluatedAt.toSqlText(connection))
        statement.setString(4, evaluatedAt.toSqlText(connection))
        statement.executeQuery().use { it.next() }
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

    fun findActive(connection: Connection, inviteCode: InviteCode): ActiveInviteCode? =
        connection.prepareStatement(
            """
            SELECT code, inviter_id, activated_at
            FROM invitecode_codes
            WHERE code = ? AND retired_at IS NULL
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, inviteCode.normalized)
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
            statement.setString(3, activeInviteCode.activatedAt.toSqlText(connection))
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
            statement.setString(1, retiredInviteCode.retiredAt.toSqlText(connection))
            statement.setString(2, retiredInviteCode.code.normalized)
            statement.setString(3, retiredInviteCode.inviterId.toString())
            statement.executeUpdate()
        }
        check(changed == 1) { "Active Invite Code changed while it was being rotated." }
    }
}
