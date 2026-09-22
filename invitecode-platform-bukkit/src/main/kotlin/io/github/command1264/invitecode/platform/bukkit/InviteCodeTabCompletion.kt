package io.github.command1264.invitecode.platform.bukkit

import java.util.Locale

object InviteCodeTabCompletion {
    fun complete(
        arguments: List<String>,
        player: Boolean,
        hasPermission: (String) -> Boolean,
        onlinePlayerNames: Collection<String>,
    ): List<String> {
        if (arguments.isEmpty()) return emptyList()
        val path = arguments.dropLast(1).map { it.lowercase(Locale.ROOT) }
        val prefix = arguments.last()
        val candidates = if (
            path.size == 4 && path.take(3) == listOf("admin", "invitation", "transfer")
        ) {
            permitted(hasPermission, "invitecode.admin.invitation", onlinePlayerNames)
        } else when (path) {
            emptyList<String>() -> rootCandidates(player, hasPermission)
            listOf("admin") -> adminCandidates(hasPermission)
            listOf("admin", "inspect") -> permitted(
                hasPermission,
                "invitecode.admin.inspect",
                "player",
                "invitation",
                "reward",
            )
            listOf("admin", "invitation") -> invitationCandidates(hasPermission)
            listOf("admin", "eligibility") -> permitted(
                hasPermission,
                "invitecode.admin.eligibility",
                "reset",
            )
            listOf("admin", "reward") -> permitted(
                hasPermission,
                "invitecode.admin.reward",
                "retry",
                "mark-delivered",
            )
            listOf("admin", "code") -> permitted(
                hasPermission,
                "invitecode.admin.code",
                "rotate",
            )
            listOf("admin", "storage") -> permitted(
                hasPermission,
                "invitecode.admin.storage",
                "migrate",
            )
            listOf("admin", "inspect", "player") ->
                permitted(hasPermission, "invitecode.admin.inspect", onlinePlayerNames)
            listOf("admin", "eligibility", "reset") ->
                permitted(hasPermission, "invitecode.admin.eligibility", onlinePlayerNames)
            listOf("admin", "code", "rotate") ->
                permitted(hasPermission, "invitecode.admin.code", onlinePlayerNames)
            listOf("admin", "storage", "migrate") ->
                permitted(hasPermission, "invitecode.admin.storage", listOf("SQLITE"))
            else -> emptyList()
        }
        return candidates
            .asSequence()
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()
    }

    private fun rootCandidates(
        player: Boolean,
        hasPermission: (String) -> Boolean,
    ): List<String> = buildList {
        if (hasPermission("invitecode.command.basic")) add("help")
        if (player && hasPermission("invitecode.command.code")) add("code")
        if (player && hasPermission("invitecode.command.claim")) addAll(listOf("claim", "confirm", "cancel"))
        if (player && hasPermission("invitecode.command.status")) add("status")
        if (player && hasPermission("invitecode.command.stats")) add("stats")
        if (hasPermission("invitecode.admin.reload")) add("reload")
        if (ADMIN_PERMISSIONS.any(hasPermission)) add("admin")
    }

    private fun adminCandidates(hasPermission: (String) -> Boolean): List<String> = buildList {
        if (hasPermission("invitecode.admin.inspect")) add("inspect")
        if (hasPermission("invitecode.admin.invitation") || hasPermission("invitecode.admin.qualification")) {
            add("invitation")
        }
        if (hasPermission("invitecode.admin.eligibility")) add("eligibility")
        if (hasPermission("invitecode.admin.reward")) add("reward")
        if (hasPermission("invitecode.admin.code")) add("code")
        if (hasPermission("invitecode.admin.storage")) add("storage")
        if (hasPermission("invitecode.admin.reload")) add("reload")
        if (hasPermission("invitecode.admin.doctor")) add("doctor")
    }

    private fun invitationCandidates(hasPermission: (String) -> Boolean): List<String> = buildList {
        if (hasPermission("invitecode.admin.invitation")) addAll(listOf("unlink", "transfer"))
        if (hasPermission("invitecode.admin.qualification")) add("force-qualify")
    }

    private fun permitted(
        hasPermission: (String) -> Boolean,
        permission: String,
        vararg candidates: String,
    ): List<String> = if (hasPermission(permission)) candidates.toList() else emptyList()

    private fun permitted(
        hasPermission: (String) -> Boolean,
        permission: String,
        candidates: Collection<String>,
    ): Collection<String> = if (hasPermission(permission)) candidates else emptyList()

    private val ADMIN_PERMISSIONS = listOf(
        "invitecode.admin.inspect",
        "invitecode.admin.invitation",
        "invitecode.admin.eligibility",
        "invitecode.admin.qualification",
        "invitecode.admin.reward",
        "invitecode.admin.code",
        "invitecode.admin.storage",
        "invitecode.admin.reload",
        "invitecode.admin.doctor",
    )
}
