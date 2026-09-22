package io.github.command1264.invitecode.platform.bukkit

import java.util.Locale

enum class InviteCodeCommandId {
    HELP,
    CODE,
    CLAIM,
    CONFIRM,
    CANCEL,
    STATUS,
    STATS,
    ADMIN_INSPECT_PLAYER,
    ADMIN_INSPECT_INVITATION,
    ADMIN_INSPECT_REWARD,
    ADMIN_INVITATION_UNLINK,
    ADMIN_INVITATION_TRANSFER,
    ADMIN_INVITATION_FORCE_QUALIFY,
    ADMIN_ELIGIBILITY_RESET,
    ADMIN_REWARD_RETRY,
    ADMIN_REWARD_MARK_DELIVERED,
    ADMIN_CODE_ROTATE,
    ADMIN_STORAGE_MIGRATE,
    ADMIN_RELOAD,
    ADMIN_DOCTOR,
}

sealed interface InviteCodeCommandParseResult

data class ParsedInviteCodeCommand(
    val id: InviteCodeCommandId,
    val permission: String,
    val playerOnly: Boolean,
    val allowedDuringMaintenance: Boolean,
    val operands: List<String> = emptyList(),
    val reason: String? = null,
) : InviteCodeCommandParseResult

data class InvalidInviteCodeCommand(val messageKey: String) : InviteCodeCommandParseResult

object InviteCodeCommandParser {
    fun parse(rawArguments: List<String>): InviteCodeCommandParseResult {
        if (rawArguments.any { it.isBlank() }) return invalid()
        val arguments = rawArguments.map { it.lowercase(Locale.ROOT) }
        if (arguments.isEmpty() || arguments == listOf("help")) {
            return command(
                InviteCodeCommandId.HELP,
                "invitecode.command.basic",
                playerOnly = false,
                maintenance = true,
            )
        }
        return when (arguments.first()) {
            "code" -> exact(rawArguments, 1, InviteCodeCommandId.CODE, "invitecode.command.code")
            "claim" -> claim(rawArguments)
            "confirm" -> exact(rawArguments, 1, InviteCodeCommandId.CONFIRM, "invitecode.command.claim")
            "cancel" -> exact(rawArguments, 1, InviteCodeCommandId.CANCEL, "invitecode.command.claim")
            "status" -> exact(rawArguments, 1, InviteCodeCommandId.STATUS, "invitecode.command.status")
            "stats" -> exact(rawArguments, 1, InviteCodeCommandId.STATS, "invitecode.command.stats")
            "reload" -> exact(
                rawArguments,
                1,
                InviteCodeCommandId.ADMIN_RELOAD,
                "invitecode.admin.reload",
                playerOnly = false,
            )
            "admin" -> parseAdmin(rawArguments, arguments)
            else -> invalid()
        }
    }

    private fun claim(arguments: List<String>): InviteCodeCommandParseResult {
        if (arguments.size !in 1..2) return invalid()
        return command(
            InviteCodeCommandId.CLAIM,
            "invitecode.command.claim",
            operands = arguments.drop(1),
        )
    }

    private fun parseAdmin(
        raw: List<String>,
        normalized: List<String>,
    ): InviteCodeCommandParseResult {
        if (normalized.size < 2) return invalid()
        return when (normalized[1]) {
            "inspect" -> parseInspect(raw, normalized)
            "invitation" -> parseInvitationMutation(raw, normalized)
            "eligibility" -> reasoned(
                raw, normalized, listOf("admin", "eligibility", "reset"), 1,
                InviteCodeCommandId.ADMIN_ELIGIBILITY_RESET, "invitecode.admin.eligibility",
            )
            "reward" -> parseRewardMutation(raw, normalized)
            "code" -> reasoned(
                raw, normalized, listOf("admin", "code", "rotate"), 1,
                InviteCodeCommandId.ADMIN_CODE_ROTATE, "invitecode.admin.code",
            )
            "storage" -> reasoned(
                raw, normalized, listOf("admin", "storage", "migrate"), 1,
                InviteCodeCommandId.ADMIN_STORAGE_MIGRATE, "invitecode.admin.storage", maintenance = true,
            )
            "reload" -> exact(
                raw, 2, InviteCodeCommandId.ADMIN_RELOAD, "invitecode.admin.reload",
                playerOnly = false,
            )
            "doctor" -> exact(
                raw, 2, InviteCodeCommandId.ADMIN_DOCTOR, "invitecode.admin.doctor",
                playerOnly = false, maintenance = true,
            )
            else -> invalid()
        }
    }

    private fun parseInspect(raw: List<String>, normalized: List<String>): InviteCodeCommandParseResult {
        if (raw.size != 4) return invalid()
        val id = when (normalized[2]) {
            "player" -> InviteCodeCommandId.ADMIN_INSPECT_PLAYER
            "invitation" -> InviteCodeCommandId.ADMIN_INSPECT_INVITATION
            "reward" -> InviteCodeCommandId.ADMIN_INSPECT_REWARD
            else -> return invalid()
        }
        return command(id, "invitecode.admin.inspect", playerOnly = false, operands = listOf(raw[3]))
    }

    private fun parseInvitationMutation(
        raw: List<String>,
        normalized: List<String>,
    ): InviteCodeCommandParseResult = when (normalized.getOrNull(2)) {
        "unlink" -> reasoned(
            raw, normalized, listOf("admin", "invitation", "unlink"), 1,
            InviteCodeCommandId.ADMIN_INVITATION_UNLINK, "invitecode.admin.invitation",
        )
        "transfer" -> reasoned(
            raw, normalized, listOf("admin", "invitation", "transfer"), 2,
            InviteCodeCommandId.ADMIN_INVITATION_TRANSFER, "invitecode.admin.invitation",
        )
        "force-qualify" -> reasoned(
            raw, normalized, listOf("admin", "invitation", "force-qualify"), 2,
            InviteCodeCommandId.ADMIN_INVITATION_FORCE_QUALIFY, "invitecode.admin.qualification",
        )
        else -> invalid()
    }

    private fun parseRewardMutation(
        raw: List<String>,
        normalized: List<String>,
    ): InviteCodeCommandParseResult = when (normalized.getOrNull(2)) {
        "retry" -> reasoned(
            raw, normalized, listOf("admin", "reward", "retry"), 1,
            InviteCodeCommandId.ADMIN_REWARD_RETRY, "invitecode.admin.reward",
        )
        "mark-delivered" -> reasoned(
            raw, normalized, listOf("admin", "reward", "mark-delivered"), 1,
            InviteCodeCommandId.ADMIN_REWARD_MARK_DELIVERED, "invitecode.admin.reward",
        )
        else -> invalid()
    }

    private fun reasoned(
        raw: List<String>,
        normalized: List<String>,
        prefix: List<String>,
        operandCount: Int,
        id: InviteCodeCommandId,
        permission: String,
        maintenance: Boolean = false,
    ): InviteCodeCommandParseResult {
        if (normalized.take(prefix.size) != prefix) return invalid()
        val reasonStart = prefix.size + operandCount
        if (raw.size <= reasonStart) return InvalidInviteCodeCommand("command.reason-required")
        val reason = raw.drop(reasonStart).joinToString(" ").trim()
        if (reason.isBlank()) return InvalidInviteCodeCommand("command.reason-required")
        return command(
            id,
            permission,
            playerOnly = false,
            maintenance = maintenance,
            operands = raw.subList(prefix.size, reasonStart),
            reason = reason,
        )
    }

    private fun exact(
        arguments: List<String>,
        size: Int,
        id: InviteCodeCommandId,
        permission: String,
        playerOnly: Boolean = true,
        maintenance: Boolean = false,
    ): InviteCodeCommandParseResult = if (arguments.size == size) {
        command(id, permission, playerOnly, maintenance)
    } else {
        invalid()
    }

    private fun command(
        id: InviteCodeCommandId,
        permission: String,
        playerOnly: Boolean = true,
        maintenance: Boolean = false,
        operands: List<String> = emptyList(),
        reason: String? = null,
    ) = ParsedInviteCodeCommand(id, permission, playerOnly, maintenance, operands, reason)

    private fun invalid() = InvalidInviteCodeCommand("command.usage")
}
