package io.github.command1264.invitecode.core

import java.util.UUID

sealed class PlayerReferenceResolution {
    data class Found(val player: StoredPlayer) : PlayerReferenceResolution()

    object Unknown : PlayerReferenceResolution()

    data class Ambiguous(val players: List<StoredPlayer>) : PlayerReferenceResolution() {
        init {
            require(players.size >= 2) { "An ambiguous player reference requires at least two matches." }
        }
    }
}

class PlayerReferenceResolver(
    private val players: PlayerLookup,
) {
    fun resolve(reference: String): PlayerReferenceResolution {
        require(reference.isNotBlank()) { "Player reference must not be blank." }
        val playerId = try {
            UUID.fromString(reference)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (playerId != null) {
            return players.player(playerId)?.let(PlayerReferenceResolution::Found)
                ?: PlayerReferenceResolution.Unknown
        }

        val matches = players.playersByLastKnownName(reference)
        return when (matches.size) {
            0 -> PlayerReferenceResolution.Unknown
            1 -> PlayerReferenceResolution.Found(matches.single())
            else -> PlayerReferenceResolution.Ambiguous(matches)
        }
    }
}
