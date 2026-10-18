package io.github.command1264.invitecode.platform.bukkit

import java.util.Locale

data class HelpQuery(val home: Boolean, val path: List<String>, val page: Int = 1)
data class HelpRow(val usage: String, val descriptionKey: String, val target: String, val navigation: Boolean)
data class HelpPage(
    val query: HelpQuery,
    val rows: List<HelpRow> = emptyList(),
    val detail: CommandHelpEntry? = null,
    val pages: Int = 1,
    val previous: String? = null,
    val next: String? = null,
    val back: String? = null,
    val error: Boolean = false,
)

/** Permission-filtered help tree shared by parsing, rendering and completion. */
object CommandHelpNavigation {
    const val PAGE_SIZE = 5
    private val homeIds = listOf(InviteCodeCommandId.CODE, InviteCodeCommandId.CLAIM,
        InviteCodeCommandId.STATUS, InviteCodeCommandId.STATS, InviteCodeCommandId.HELP)

    fun path(entry: CommandHelpEntry): List<String> =
        if (entry.id == InviteCodeCommandId.ADMIN_RELOAD) listOf("admin", "reload")
        else entry.usage.split(' ').takeWhile { !it.startsWith('<') && !it.startsWith('[') }

    fun query(arguments: List<String>): HelpQuery? {
        if (arguments.isEmpty()) return HelpQuery(true, emptyList())
        if (!arguments.first().equals("help", true) || arguments.any { it.isBlank() }) return null
        val tail = arguments.drop(1).map { it.lowercase(Locale.ROOT) }
        val hasPage = tail.lastOrNull()?.toIntOrNull() != null
        val page = if (hasPage) tail.last().toIntOrNull() ?: return null else 1
        if (page < 1) return null
        val topic = if (hasPage) tail.dropLast(1) else tail
        if (!known(topic) || (hasPage && leaf(topic) != null)) return null
        return HelpQuery(false, topic, page)
    }

    private fun known(topic: List<String>): Boolean = topic.isEmpty() || topic == listOf("admin") ||
        CommandHelp.entries.any { path(it).take(topic.size) == topic }

    private fun leaf(topic: List<String>) = CommandHelp.entries.firstOrNull { path(it) == topic }

    private fun visible(player: Boolean, permission: (String) -> Boolean) = CommandHelp.entries.filter {
        permission("invitecode.command.basic") && (!it.playerOnly || player) && permission(it.permission)
    }

    fun resolve(query: HelpQuery, label: String, player: Boolean, permission: (String) -> Boolean): HelpPage {
        val allowed = visible(player, permission)
        fun usage(entry: CommandHelpEntry) = "/$label ${path(entry).let {
            if (entry.id == InviteCodeCommandId.ADMIN_RELOAD) listOf("reload") else it
        }.joinToString(" ")}"
        fun detailRow(entry: CommandHelpEntry, navigate: Boolean) = HelpRow(usage(entry), entry.descriptionKey,
            if (navigate) command(label, path(entry)) else usage(entry), navigate)
        val admins = allowed.filter { !it.playerOnly && it.id != InviteCodeCommandId.HELP }
        if (query.home) {
            val rows = homeIds.mapNotNull { id -> allowed.firstOrNull { it.id == id } }.map { entry -> detailRow(entry, false).copy(navigation = entry.id == InviteCodeCommandId.HELP) }
            val adminLink = if (admins.isEmpty()) emptyList() else listOf(
                HelpRow("/$label help admin", "runtime.help-topic.admin", command(label, listOf("admin")), true))
            return HelpPage(query, rows + adminLink)
        }
        val detail = leaf(query.path)
        if (detail != null) return if (detail in allowed) {
            HelpPage(query, detail = detail, back = parentLink(query.path, label, player, permission))
        } else HelpPage(query, error = true)
        val candidates = when {
            query.path.isEmpty() && player -> homeIds.mapNotNull { id -> allowed.firstOrNull { it.id == id } }
                .map { detailRow(it, true) }
            query.path.isEmpty() || query.path == listOf("admin") -> admins.map { path(it)[1] }.distinct().map { topic ->
                HelpRow("/$label help admin $topic", "runtime.help-topic-description.$topic",
                    command(label, listOf("admin", topic)), true)
            }
            else -> allowed.filter { path(it).size > query.path.size && path(it).take(query.path.size) == query.path }
                .map { detailRow(it, true) }
        }
        val pages = maxOf(1, (candidates.size + PAGE_SIZE - 1) / PAGE_SIZE)
        if (candidates.isEmpty() || query.page > pages) return HelpPage(query, error = true)
        val rows = candidates.drop((query.page - 1) * PAGE_SIZE).take(PAGE_SIZE)
        return HelpPage(query, rows, pages = pages,
            previous = if (query.page > 1) command(label, query.path, query.page - 1) else null,
            next = if (query.page < pages) command(label, query.path, query.page + 1) else null,
            back = if (query.path.isEmpty()) "/$label" else parentLink(query.path, label, player, permission))
    }

    private fun parentLink(topic: List<String>, label: String, player: Boolean, permission: (String) -> Boolean): String {
        if (topic == listOf("admin")) return "/$label"
        val parent = topic.dropLast(1)
        val first = resolve(HelpQuery(false, parent), label, player, permission)
        val target = command(label, topic)
        val number = (1..first.pages).firstOrNull { page ->
            val content = if (page == 1) first else resolve(HelpQuery(false, parent, page), label, player, permission)
            content.rows.any { it.target == target }
        } ?: 1
        return command(label, parent, number.takeIf { it > 1 })
    }

    fun complete(arguments: List<String>, player: Boolean, permission: (String) -> Boolean): List<String> {
        if (arguments.isEmpty() || !permission("invitecode.command.basic")) return emptyList()
        val topic = arguments.drop(1).map { it.lowercase(Locale.ROOT) }
        if (topic.any { it.toIntOrNull() != null } || leaf(topic) != null) return emptyList()
        val allowed = visible(player, permission)
        val candidates = allowed.map(::path).filter { it.size > topic.size && it.take(topic.size) == topic }
            .map { it[topic.size] }.distinct().toMutableList()
        if (topic.isEmpty() && allowed.any { path(it).first() == "admin" } && "admin" !in candidates) candidates.add("admin")
        val page = resolve(HelpQuery(false, topic), "ic", player, permission)
        if (!page.error && page.pages > 1) candidates.addAll((1..page.pages).map(Int::toString))
        return candidates
    }

    fun command(label: String, topic: List<String>, page: Int? = null): String =
        (listOf("/$label", "help") + topic + listOfNotNull(page?.toString())).joinToString(" ")

    fun detailKey(entry: CommandHelpEntry) = entry.descriptionKey.replace("help-description", "help-detail")

    fun example(entry: CommandHelpEntry, label: String): String = "/$label " + entry.usage
        .replace("[code]", "A1B2C3").replace("<player-reference>", "Alex")
        .replace("<invitation-uuid>", "EXAMPLE_INVITATION_UUID").replace("<reward-action-uuid>", "EXAMPLE_ACTION_UUID")
        .replace("<qualification-profile-id>", "new-player").replace("<SQLITE|MYSQL>", "SQLITE")
        .replace("<reason>", "manual-review")
}
