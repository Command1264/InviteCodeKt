package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InviteCodeTabCompletionTest {
    private val allowAll: (String) -> Boolean = { true }
    private val onlinePlayers = listOf("Alex", "Steve")

    @Test
    fun `help tabs traverse only permitted topics and real page numbers`() {
        assertEquals(listOf("1", "2", "code", "doctor", "eligibility", "inspect", "invitation", "reload", "reward", "storage"),
            complete(listOf("help", "admin", ""), player = true))
        assertEquals(listOf("force-qualify", "transfer", "unlink"), complete(listOf("help", "admin", "invitation", ""), true))
        assertEquals(emptyList<String>(), complete(listOf("help", "admin", "invitation", "transfer", ""), true))
        assertEquals(emptyList<String>(), complete(listOf("help", "admin", "2", ""), true))
        val basic: (String) -> Boolean = { it.startsWith("invitecode.command.") }
        assertEquals(listOf("cancel", "claim", "code", "confirm", "help", "stats", "status"),
            InviteCodeTabCompletion.complete(listOf("help", ""), true, basic, onlinePlayers))
        assertEquals(emptyList<String>(), InviteCodeTabCompletion.complete(listOf("help", "admin", ""), true, basic, onlinePlayers))
    }

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

        val onlyQualification: (String) -> Boolean = { it in setOf("invitecode.command.basic", "invitecode.admin.qualification") }
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

    @Test
    fun `qualification profile completion is permission gated and never fills IDs or free text`() {
        val arguments = listOf("admin", "invitation", "force-qualify", "invitation-id", "ne")
        val profiles = listOf("new-player", "veteran")
        assertEquals(listOf("new-player"), InviteCodeTabCompletion.complete(arguments, false, allowAll, onlinePlayers, profiles))
        val denied: (String) -> Boolean = { it != "invitecode.admin.qualification" }
        assertEquals(emptyList<String>(), InviteCodeTabCompletion.complete(arguments, true, denied, onlinePlayers, profiles))
        assertEquals(emptyList<String>(), complete(arguments.dropLast(1) + "" + "" + "", false))
        assertEquals(emptyList<String>(), complete(listOf("admin", "invitation", "force-qualify", ""), false))
        assertEquals(emptyList<String>(), InviteCodeTabCompletion.complete(listOf("help", "claim", ""), false, allowAll, onlinePlayers))
        val onlyQualification: (String) -> Boolean = { it in setOf("invitecode.command.basic", "invitecode.admin.qualification") }
        assertEquals(listOf("force-qualify"), InviteCodeTabCompletion.complete(
            listOf("help", "admin", "invitation", ""), false, onlyQualification, onlinePlayers))
        assertEquals(emptyList<String>(), InviteCodeTabCompletion.complete(listOf("help", "admin", ""),
            false, { it != "invitecode.command.basic" }, onlinePlayers))
    }

    private fun complete(arguments: List<String>, player: Boolean): List<String> =
        InviteCodeTabCompletion.complete(arguments, player, allowAll, onlinePlayers)
}
