package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InviteCodeCommandParserTest {
    @Test
    fun `player command routes expose permissions and operands`() {
        val claim = assertInstanceOf(
            ParsedInviteCodeCommand::class.java,
            InviteCodeCommandParser.parse(listOf("claim", "A1B2C3")),
        )

        assertEquals(InviteCodeCommandId.CLAIM, claim.id)
        assertEquals("invitecode.command.claim", claim.permission)
        assertEquals(listOf("A1B2C3"), claim.operands)
        assertTrue(claim.playerOnly)
        assertFalse(claim.allowedDuringMaintenance)
    }

    @Test
    fun `help doctor and storage migration are available in maintenance`() {
        listOf(
            emptyList(),
            listOf("help"),
            listOf("admin", "doctor"),
            listOf("admin", "storage", "migrate", "SQLITE", "initial setup"),
        ).forEach { arguments ->
            val parsed = assertInstanceOf(
                ParsedInviteCodeCommand::class.java,
                InviteCodeCommandParser.parse(arguments),
            )
            assertTrue(parsed.allowedDuringMaintenance)
        }
    }

    @Test
    fun `reload has a direct console-safe route`() {
        val parsed = assertInstanceOf(
            ParsedInviteCodeCommand::class.java,
            InviteCodeCommandParser.parse(listOf("reload")),
        )

        assertEquals(InviteCodeCommandId.ADMIN_RELOAD, parsed.id)
        assertEquals("invitecode.admin.reload", parsed.permission)
        assertFalse(parsed.playerOnly)
        assertFalse(parsed.allowedDuringMaintenance)
    }

    @Test
    fun `help and administrative commands can be used by console`() {
        val commands = listOf(
            emptyList(),
            listOf("help"),
            listOf("reload"),
            listOf("admin", "doctor"),
            listOf("admin", "reload"),
            listOf("admin", "inspect", "player", "Alex"),
        )

        commands.forEach { arguments ->
            val parsed = assertInstanceOf(
                ParsedInviteCodeCommand::class.java,
                InviteCodeCommandParser.parse(arguments),
            )
            assertFalse(parsed.playerOnly)
        }
    }

    @Test
    fun `admin mutation requires a non blank reason`() {
        val missing = InviteCodeCommandParser.parse(
            listOf("admin", "invitation", "unlink", "invitation-id"),
        )
        assertInstanceOf(InvalidInviteCodeCommand::class.java, missing)

        val parsed = assertInstanceOf(
            ParsedInviteCodeCommand::class.java,
            InviteCodeCommandParser.parse(
                listOf("admin", "invitation", "unlink", "invitation-id", "duplicate", "account"),
            ),
        )
        assertEquals(InviteCodeCommandId.ADMIN_INVITATION_UNLINK, parsed.id)
        assertEquals(listOf("invitation-id"), parsed.operands)
        assertEquals("duplicate account", parsed.reason)
    }

    @Test
    fun `unknown and surplus arguments are rejected`() {
        assertInstanceOf(
            InvalidInviteCodeCommand::class.java,
            InviteCodeCommandParser.parse(listOf("status", "extra")),
        )
        assertInstanceOf(
            InvalidInviteCodeCommand::class.java,
            InviteCodeCommandParser.parse(listOf("unknown")),
        )
    }

    @Test
    fun `all administrative routes are recognized`() {
        val routes = mapOf(
            listOf("admin", "inspect", "player", "Alex") to InviteCodeCommandId.ADMIN_INSPECT_PLAYER,
            listOf("admin", "inspect", "invitation", UUID) to InviteCodeCommandId.ADMIN_INSPECT_INVITATION,
            listOf("admin", "inspect", "reward", UUID) to InviteCodeCommandId.ADMIN_INSPECT_REWARD,
            listOf("admin", "invitation", "transfer", UUID, "Alex", "reason") to
                InviteCodeCommandId.ADMIN_INVITATION_TRANSFER,
            listOf("admin", "invitation", "force-qualify", UUID, "new-player", "reason") to
                InviteCodeCommandId.ADMIN_INVITATION_FORCE_QUALIFY,
            listOf("admin", "eligibility", "reset", "Alex", "reason") to
                InviteCodeCommandId.ADMIN_ELIGIBILITY_RESET,
            listOf("admin", "reward", "retry", UUID, "reason") to InviteCodeCommandId.ADMIN_REWARD_RETRY,
            listOf("admin", "reward", "mark-delivered", UUID, "reason") to
                InviteCodeCommandId.ADMIN_REWARD_MARK_DELIVERED,
            listOf("admin", "code", "rotate", "Alex", "reason") to InviteCodeCommandId.ADMIN_CODE_ROTATE,
            listOf("admin", "reload") to InviteCodeCommandId.ADMIN_RELOAD,
        )

        routes.forEach { (arguments, expected) ->
            val parsed = assertInstanceOf(
                ParsedInviteCodeCommand::class.java,
                InviteCodeCommandParser.parse(arguments),
            )
            assertEquals(expected, parsed.id)
        }
    }

    private companion object {
        const val UUID = "00000000-0000-0000-0000-000000000001"
    }
}
