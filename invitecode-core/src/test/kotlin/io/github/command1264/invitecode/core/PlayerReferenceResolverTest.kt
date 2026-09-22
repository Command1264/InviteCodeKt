package io.github.command1264.invitecode.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PlayerReferenceResolverTest {
    private val first = StoredPlayer(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        Instant.EPOCH,
        "SameName",
    )
    private val second = StoredPlayer(
        UUID.fromString("00000000-0000-0000-0000-000000000002"),
        Instant.EPOCH,
        "samename",
    )
    private val lookup = object : PlayerLookup {
        override fun player(playerId: UUID): StoredPlayer? =
            listOf(first, second).singleOrNull { it.id == playerId }

        override fun playersByLastKnownName(lastKnownName: String): List<StoredPlayer> =
            listOf(first, second).filter { it.lastKnownName.equals(lastKnownName, ignoreCase = true) }
    }
    private val resolver = PlayerReferenceResolver(lookup)

    @Test
    fun `UUID is resolved before treating the reference as a player name`() {
        assertEquals(PlayerReferenceResolution.Found(first), resolver.resolve(first.id.toString()))
        assertEquals(PlayerReferenceResolution.Unknown, resolver.resolve(UUID.randomUUID().toString()))
    }

    @Test
    fun `unique player name resolves case insensitively`() {
        val unique = first.copy(lastKnownName = "OnlyOne")
        val resolver = PlayerReferenceResolver(object : PlayerLookup {
            override fun player(playerId: UUID): StoredPlayer? = null
            override fun playersByLastKnownName(lastKnownName: String): List<StoredPlayer> = listOf(unique)
        })

        assertEquals(PlayerReferenceResolution.Found(unique), resolver.resolve("onlyone"))
    }

    @Test
    fun `unknown and ambiguous player names remain distinct`() {
        assertEquals(PlayerReferenceResolution.Unknown, resolver.resolve("Nobody"))
        assertEquals(
            PlayerReferenceResolution.Ambiguous(listOf(first, second)),
            resolver.resolve("SAMENAME"),
        )
    }
}
