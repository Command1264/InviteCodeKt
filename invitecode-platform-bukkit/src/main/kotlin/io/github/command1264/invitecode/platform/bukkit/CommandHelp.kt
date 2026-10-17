package io.github.command1264.invitecode.platform.bukkit

/** Help entries mirror parser routes while keeping localized descriptions outside routing logic. */
data class CommandHelpEntry(
    val id: InviteCodeCommandId,
    val usage: String,
    val descriptionKey: String,
    val permission: String,
    val playerOnly: Boolean,
)

object CommandHelp {
    val entries: List<CommandHelpEntry> = listOf(
        entry(InviteCodeCommandId.HELP, "help", "help", "invitecode.command.basic", false),
        entry(InviteCodeCommandId.CODE, "code", "code", "invitecode.command.code", true),
        entry(InviteCodeCommandId.CLAIM, "claim [code]", "claim", "invitecode.command.claim", true),
        entry(InviteCodeCommandId.CONFIRM, "confirm", "confirm", "invitecode.command.claim", true),
        entry(InviteCodeCommandId.CANCEL, "cancel", "cancel", "invitecode.command.claim", true),
        entry(InviteCodeCommandId.STATUS, "status", "status", "invitecode.command.status", true),
        entry(InviteCodeCommandId.STATS, "stats", "stats", "invitecode.command.stats", true),
        entry(
            InviteCodeCommandId.ADMIN_INSPECT_PLAYER,
            "admin inspect player <player-reference>", "inspect-player", "invitecode.admin.inspect", false,
        ),
        entry(
            InviteCodeCommandId.ADMIN_INSPECT_INVITATION,
            "admin inspect invitation <invitation-uuid>", "inspect-invitation", "invitecode.admin.inspect", false,
        ),
        entry(
            InviteCodeCommandId.ADMIN_INSPECT_REWARD,
            "admin inspect reward <reward-action-uuid>", "inspect-reward", "invitecode.admin.inspect", false,
        ),
        entry(
            InviteCodeCommandId.ADMIN_INVITATION_UNLINK,
            "admin invitation unlink <invitation-uuid> <reason>", "unlink", "invitecode.admin.invitation", false,
        ),
        entry(
            InviteCodeCommandId.ADMIN_INVITATION_TRANSFER,
            "admin invitation transfer <invitation-uuid> <player-reference> <reason>",
            "transfer", "invitecode.admin.invitation", false,
        ),
        entry(
            InviteCodeCommandId.ADMIN_INVITATION_FORCE_QUALIFY,
            "admin invitation force-qualify <invitation-uuid> <qualification-profile-id> <reason>",
            "force-qualify", "invitecode.admin.qualification", false,
        ),
        entry(
            InviteCodeCommandId.ADMIN_ELIGIBILITY_RESET,
            "admin eligibility reset <player-reference> <reason>", "reset-eligibility",
            "invitecode.admin.eligibility", false,
        ),
        entry(
            InviteCodeCommandId.ADMIN_REWARD_RETRY,
            "admin reward retry <reward-action-uuid> <reason>", "retry-reward", "invitecode.admin.reward", false,
        ),
        entry(
            InviteCodeCommandId.ADMIN_REWARD_MARK_DELIVERED,
            "admin reward mark-delivered <reward-action-uuid> <reason>", "mark-delivered",
            "invitecode.admin.reward", false,
        ),
        entry(
            InviteCodeCommandId.ADMIN_CODE_ROTATE,
            "admin code rotate <player-reference> <reason>", "rotate-code", "invitecode.admin.code", false,
        ),
        entry(
            InviteCodeCommandId.ADMIN_STORAGE_MIGRATE,
            "admin storage migrate <SQLITE|MYSQL> <reason>", "migrate-storage", "invitecode.admin.storage", false,
        ),
        entry(InviteCodeCommandId.ADMIN_RELOAD, "reload", "reload", "invitecode.admin.reload", false),
        entry(InviteCodeCommandId.ADMIN_DOCTOR, "admin doctor", "doctor", "invitecode.admin.doctor", false),
    )

    private fun entry(
        id: InviteCodeCommandId,
        usage: String,
        description: String,
        permission: String,
        playerOnly: Boolean,
    ) = CommandHelpEntry(id, usage, "runtime.help-description.$description", permission, playerOnly)
}
