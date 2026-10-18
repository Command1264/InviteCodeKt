package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommandHelpNavigationTest {
    private val all: (String) -> Boolean = { true }
    private fun page(vararg args: String, player: Boolean = true, permissions: (String) -> Boolean = all): HelpPage =
        CommandHelpNavigation.resolve(requireNotNull(CommandHelpNavigation.query(args.toList())), "ic", player, permissions)

    @Test
    fun `return locates category on its permission filtered parent page`() {
        assertEquals("/ic", page("help", "admin").back)
        listOf("storage", "reload", "doctor").forEach { topic ->
            val child = page("help", "admin", topic)
            assertEquals("/ic help admin 2", child.back, topic)
        }
        val onlyDoctor: (String) -> Boolean = { it in setOf("invitecode.command.basic", "invitecode.admin.doctor") }
        assertEquals("/ic help admin", page("help", "admin", "doctor", permissions = onlyDoctor).back)
        assertEquals("/ic help admin invitation", page("help", "admin", "invitation", "transfer").back)
        // Every visible child returns to a page which actually contains it, including future categories.
        for (number in 1..page("help", "admin").pages) {
            val parent = page("help", "admin", number.toString())
            parent.rows.forEach { row ->
                val child = CommandHelpNavigation.resolve(requireNotNull(CommandHelpNavigation.query(
                    row.target.removePrefix("/ic ").split(' '))), "ic", true, all)
                val back = CommandHelpNavigation.resolve(requireNotNull(CommandHelpNavigation.query(
                    child.back!!.removePrefix("/ic ").split(' '))), "ic", true, all)
                assertTrue(back.rows.any { it.target == row.target })
            }
        }
    }

    @Test
    fun `homepage contains five common commands and a single admin entrance`() {
        val home = page()
        assertEquals(listOf("/ic code", "/ic claim", "/ic status", "/ic stats", "/ic help", "/ic help admin"),
            home.rows.map { it.usage })
        assertEquals(4, home.rows.count { !it.navigation })
        assertTrue(home.rows.single { it.usage == "/ic help" }.navigation)
        assertEquals("/ic help", home.rows.single { it.usage == "/ic help" }.target)
        assertTrue(home.rows.none { it.usage.contains("confirm") || it.usage.contains("cancel") || it.usage.contains('<') })
    }

    @Test
    fun `management index pages are bounded and point only to help routes`() {
        val first = page("help", "admin")
        val second = page("help", "admin", "2")
        assertEquals(listOf("/ic help admin inspect", "/ic help admin invitation", "/ic help admin eligibility",
            "/ic help admin reward", "/ic help admin code"), first.rows.map { it.usage })
        assertEquals(listOf("/ic help admin storage", "/ic help admin reload", "/ic help admin doctor"), second.rows.map { it.usage })
        assertEquals("/ic help admin 2", first.next)
        assertNull(first.previous)
        assertEquals("/ic help admin 1", second.previous)
        assertNull(second.next)
        assertTrue(page("help", "admin", "3").error)
        assertTrue(first.rows.all { it.navigation && it.target.startsWith("/ic help ") })
    }

    @Test
    fun `permission filtering applies to groups details pages and console audiences`() {
        val qualificationOnly: (String) -> Boolean = { it in setOf("invitecode.command.basic", "invitecode.admin.qualification") }
        assertEquals(listOf("/ic admin invitation force-qualify"),
            page("help", "admin", "invitation", permissions = qualificationOnly).rows.map { it.usage })
        assertTrue(page("help", "admin", "invitation", "unlink", permissions = qualificationOnly).error)
        assertTrue(page("help", "admin", "2", permissions = qualificationOnly).error)
        assertTrue(page("help", "claim", player = false).error)
        assertEquals(listOf("/ic help"), page(player = false).rows.map { it.usage })
        assertTrue(page("help", "admin", permissions = { it == "invitecode.command.basic" }).error)
    }

    @Test
    fun `every operational route has a reachable permission filtered detail without mutating operands`() {
        CommandHelp.entries.forEach { entry ->
            val result = page(*(listOf("help") + CommandHelpNavigation.path(entry)).toTypedArray())
            assertEquals(entry.id, result.detail?.id)
            assertTrue(result.rows.isEmpty())
            assertTrue(result.back!!.startsWith("/ic help"))
        }
        assertEquals(InviteCodeCommandId.CONFIRM, page("help", "confirm").detail?.id)
        assertEquals(InviteCodeCommandId.CANCEL, page("help", "cancel").detail?.id)
        assertEquals(listOf("/ic code", "/ic claim", "/ic status", "/ic stats", "/ic help"), page("help").rows.map { it.usage })
    }

    @Test
    fun `unknown pages excess arguments and action parameters are not help topics`() {
        listOf(listOf("help", "0"), listOf("help", "-1"), listOf("help", "9999999999999"),
            listOf("help", "invitation"), listOf("help", "claim", "A1B2C3"),
            listOf("help", "admin", "invitation", "transfer", "UUID"), listOf("help", "claim", "1"))
            .forEach { assertNull(CommandHelpNavigation.query(it), it.toString()) }
    }
}
