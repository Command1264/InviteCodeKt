package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandHelpTest {
    @Test
    fun `help contains exactly one entry for every parser route with matching metadata`() {
        assertEquals(20, CommandHelp.entries.size)
        assertEquals(InviteCodeCommandId.entries.toSet(), CommandHelp.entries.map { it.id }.toSet())
        assertEquals(CommandHelp.entries.size, CommandHelp.entries.map { it.id }.toSet().size)

        CommandHelp.entries.forEach { entry ->
            val arguments = argumentsById.getValue(entry.id)
            val parsed = assertInstanceOf(
                ParsedInviteCodeCommand::class.java,
                InviteCodeCommandParser.parse(arguments),
                "${entry.id}: $arguments",
            )
            assertEquals(entry.id, parsed.id, entry.usage)
            assertEquals(entry.permission, parsed.permission, entry.usage)
            assertEquals(entry.playerOnly, parsed.playerOnly, entry.usage)
            assertTrue(entry.descriptionKey.startsWith("runtime.help-description."))
            assertTrue(entry.usage.isNotBlank())
        }
    }

    @Test
    fun `help and reload compatibility routes resolve to their canonical entries`() {
        val help = CommandHelp.entries.single { it.id == InviteCodeCommandId.HELP }
        val root = assertInstanceOf(ParsedInviteCodeCommand::class.java, InviteCodeCommandParser.parse(emptyList()))
        val explicit = assertInstanceOf(ParsedInviteCodeCommand::class.java, InviteCodeCommandParser.parse(listOf("help")))
        assertEquals(help.id, root.id)
        assertEquals(help.id, explicit.id)
        assertFalse(root.playerOnly)
        assertFalse(explicit.playerOnly)

        val reload = CommandHelp.entries.single { it.id == InviteCodeCommandId.ADMIN_RELOAD }
        listOf(listOf("reload"), listOf("admin", "reload")).forEach { arguments ->
            val parsed = assertInstanceOf(
                ParsedInviteCodeCommand::class.java,
                InviteCodeCommandParser.parse(arguments),
            )
            assertEquals(reload.id, parsed.id)
            assertEquals(reload.permission, parsed.permission)
            assertFalse(parsed.playerOnly)
        }
    }

    private companion object {
        const val UUID = "00000000-0000-0000-0000-000000000001"
        val argumentsById = mapOf(
            InviteCodeCommandId.HELP to listOf("help"),
            InviteCodeCommandId.CODE to listOf("code"),
            InviteCodeCommandId.CLAIM to listOf("claim", "CODE"),
            InviteCodeCommandId.CONFIRM to listOf("confirm"),
            InviteCodeCommandId.CANCEL to listOf("cancel"),
            InviteCodeCommandId.STATUS to listOf("status"),
            InviteCodeCommandId.STATS to listOf("stats"),
            InviteCodeCommandId.ADMIN_INSPECT_PLAYER to listOf("admin", "inspect", "player", "Alex"),
            InviteCodeCommandId.ADMIN_INSPECT_INVITATION to listOf("admin", "inspect", "invitation", UUID),
            InviteCodeCommandId.ADMIN_INSPECT_REWARD to listOf("admin", "inspect", "reward", UUID),
            InviteCodeCommandId.ADMIN_INVITATION_UNLINK to listOf("admin", "invitation", "unlink", UUID, "reason"),
            InviteCodeCommandId.ADMIN_INVITATION_TRANSFER to
                listOf("admin", "invitation", "transfer", UUID, "Alex", "reason"),
            InviteCodeCommandId.ADMIN_INVITATION_FORCE_QUALIFY to
                listOf("admin", "invitation", "force-qualify", UUID, "profile", "reason"),
            InviteCodeCommandId.ADMIN_ELIGIBILITY_RESET to listOf("admin", "eligibility", "reset", "Alex", "reason"),
            InviteCodeCommandId.ADMIN_REWARD_RETRY to listOf("admin", "reward", "retry", UUID, "reason"),
            InviteCodeCommandId.ADMIN_REWARD_MARK_DELIVERED to
                listOf("admin", "reward", "mark-delivered", UUID, "reason"),
            InviteCodeCommandId.ADMIN_CODE_ROTATE to listOf("admin", "code", "rotate", "Alex", "reason"),
            InviteCodeCommandId.ADMIN_STORAGE_MIGRATE to
                listOf("admin", "storage", "migrate", "SQLITE", "reason"),
            InviteCodeCommandId.ADMIN_RELOAD to listOf("reload"),
            InviteCodeCommandId.ADMIN_DOCTOR to listOf("admin", "doctor"),
        )
    }
}
