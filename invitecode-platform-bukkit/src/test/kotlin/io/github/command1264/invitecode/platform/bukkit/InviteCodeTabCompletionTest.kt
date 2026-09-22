package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InviteCodeTabCompletionTest {
    private val allowAll: (String) -> Boolean = { true }
    private val onlinePlayers = listOf("Alex", "Steve")

    @Test
    fun `player and console receive only applicable root commands`() {
        assertEquals(
            listOf("admin", "cancel", "claim", "code", "confirm", "help", "reload", "stats", "status"),
            complete(listOf(""), player = true),
        )
        assertEquals(listOf("admin", "help", "reload"), complete(listOf(""), player = false))
        assertEquals(listOf("stats", "status"), complete(listOf("st"), player = true))
    }

    @Test
    fun `administrative trees and player operands complete at exact positions`() {
        assertEquals(
            listOf("code", "doctor", "eligibility", "inspect", "invitation", "reload", "reward", "storage"),
            complete(listOf("admin", ""), player = false),
        )
        assertEquals(
            listOf("invitation", "player", "reward"),
            complete(listOf("admin", "inspect", ""), player = false),
        )
        assertEquals(
            onlinePlayers,
            complete(listOf("admin", "inspect", "player", ""), player = false),
        )
        assertEquals(
            listOf("Steve"),
            complete(listOf("admin", "invitation", "transfer", "invitation-id", "St"), player = false),
        )
        assertEquals(
            listOf("SQLITE"),
            complete(listOf("admin", "storage", "migrate", "s"), player = false),
        )
    }

    @Test
    fun `finished unknown and excess arguments never fall back to player names`() {
        listOf(
            listOf("status", ""),
            listOf("stats", "anything"),
            listOf("help", ""),
            listOf("reload", ""),
            listOf("unknown", ""),
            listOf("admin", "doctor", ""),
            listOf("admin", "inspect", "player", "Alex", ""),
            listOf("admin", "invitation", "transfer", "invitation-id", "Alex", "reason", "extra"),
            listOf("one", "two", "three", "four", "five", "six", "seven"),
        ).forEach { arguments ->
            assertEquals(emptyList<String>(), complete(arguments, player = true), arguments.toString())
        }
    }

    @Test
    fun `permissions hide unavailable branches`() {
        val onlyHelp: (String) -> Boolean = { it == "invitecode.command.basic" }

        assertEquals(
            listOf("help"),
            InviteCodeTabCompletion.complete(listOf(""), true, onlyHelp, onlinePlayers),
        )
        assertEquals(
            emptyList<String>(),
            InviteCodeTabCompletion.complete(listOf("admin", ""), false, onlyHelp, onlinePlayers),
        )

        val onlyQualification: (String) -> Boolean = { it == "invitecode.admin.qualification" }
        assertEquals(
            listOf("force-qualify"),
            InviteCodeTabCompletion.complete(
                listOf("admin", "invitation", ""),
                false,
                onlyQualification,
                onlinePlayers,
            ),
        )
        assertEquals(
            emptyList<String>(),
            InviteCodeTabCompletion.complete(
                listOf("admin", "inspect", "player", ""),
                false,
                onlyQualification,
                onlinePlayers,
            ),
        )
    }

    private fun complete(arguments: List<String>, player: Boolean): List<String> =
        InviteCodeTabCompletion.complete(arguments, player, allowAll, onlinePlayers)
}
