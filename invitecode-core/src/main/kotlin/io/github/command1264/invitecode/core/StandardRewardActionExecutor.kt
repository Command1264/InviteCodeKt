package io.github.command1264.invitecode.core

import java.math.BigDecimal
import java.util.Locale
import java.util.UUID

interface RewardExecutionEnvironment {
    fun isOnline(playerId: UUID): Boolean

    fun dispatchConsole(command: String): Boolean

    fun dispatchPlayer(playerId: UUID, command: String): OnlineRewardActionResult

    fun sendMessage(playerId: UUID, localeKey: String): OnlineRewardActionResult

    fun economyAvailable(): Boolean

    fun deposit(playerId: UUID, amount: BigDecimal): Boolean
}

enum class OnlineRewardActionResult {
    ACCEPTED,
    REJECTED,
    RECIPIENT_OFFLINE,
}

class StandardRewardActionExecutor(
    private val environment: RewardExecutionEnvironment,
) : RewardActionExecutor {
    override fun readiness(
        action: StoredRewardAction,
        context: RewardActionExecutionContext,
    ): RewardActionReadiness {
        require(action.recipientId == context.recipientId) {
            "Reward Action recipient does not match execution context."
        }
        return when (action.type.toActionTypeOrNull()) {
            RewardActionType.MESSAGE,
            RewardActionType.PLAYER_COMMAND -> if (environment.isOnline(action.recipientId)) {
                RewardActionReadiness.READY
            } else {
                RewardActionReadiness.RECIPIENT_OFFLINE
            }
            RewardActionType.VAULT_ECONOMY -> if (environment.economyAvailable()) {
                RewardActionReadiness.READY
            } else {
                RewardActionReadiness.INTEGRATION_UNAVAILABLE
            }
            RewardActionType.CONSOLE_COMMAND,
            null -> RewardActionReadiness.READY
        }
    }

    override fun execute(
        action: StoredRewardAction,
        context: RewardActionExecutionContext,
    ): RewardActionExecutionResult {
        require(action.status == RewardActionStatus.PROCESSING) {
            "Only a PROCESSING Reward Action can execute external effects."
        }
        require(action.recipientId == context.recipientId) {
            "Reward Action recipient does not match execution context."
        }
        return when (action.type.toActionTypeOrNull()) {
            RewardActionType.CONSOLE_COMMAND -> executeCommand(action.payload, context, environment::dispatchConsole)
            RewardActionType.PLAYER_COMMAND -> executeOnlineCommand(action.payload, context) { command ->
                environment.dispatchPlayer(action.recipientId, command)
            }
            RewardActionType.MESSAGE -> {
                if (!MESSAGE_KEY.matches(action.payload)) {
                    return RewardActionExecutionResult.Failed("Stored Reward message key is invalid.")
                }
                environment.sendMessage(action.recipientId, action.payload).toExecutionResult()
            }
            RewardActionType.VAULT_ECONOMY -> {
                val amount = action.payload.toBigDecimalOrNull()
                    ?: return RewardActionExecutionResult.Failed("Stored Vault economy amount is invalid.")
                if (amount <= BigDecimal.ZERO) {
                    return RewardActionExecutionResult.Failed("Stored Vault economy amount is invalid.")
                }
                if (environment.deposit(action.recipientId, amount)) {
                    RewardActionExecutionResult.Delivered
                } else {
                    RewardActionExecutionResult.Failed("Vault economy deposit was rejected.")
                }
            }
            null -> RewardActionExecutionResult.Failed("Stored Reward Action type is unsupported.")
        }
    }

    private fun executeCommand(
        template: String,
        context: RewardActionExecutionContext,
        dispatch: (String) -> Boolean,
    ): RewardActionExecutionResult {
        val command = try {
            renderCommand(template, context)
        } catch (_: IllegalArgumentException) {
            return RewardActionExecutionResult.Failed("Reward command context is invalid.")
        }
        return commandResult(command, dispatch)
    }

    private fun executeOnlineCommand(
        template: String,
        context: RewardActionExecutionContext,
        dispatch: (String) -> OnlineRewardActionResult,
    ): RewardActionExecutionResult {
        val command = try {
            renderCommand(template, context)
        } catch (_: IllegalArgumentException) {
            return RewardActionExecutionResult.Failed("Reward command context is invalid.")
        }
        return dispatch(command).toExecutionResult()
    }

    private fun OnlineRewardActionResult.toExecutionResult(): RewardActionExecutionResult = when (this) {
        OnlineRewardActionResult.ACCEPTED -> RewardActionExecutionResult.Delivered
        OnlineRewardActionResult.REJECTED -> RewardActionExecutionResult.Failed("Reward action was rejected.")
        OnlineRewardActionResult.RECIPIENT_OFFLINE -> RewardActionExecutionResult.Deferred(
            RewardActionReadiness.RECIPIENT_OFFLINE,
        )
    }

    private fun commandResult(
        command: String,
        dispatch: (String) -> Boolean,
    ): RewardActionExecutionResult = if (dispatch(command)) {
        RewardActionExecutionResult.Delivered
    } else {
        RewardActionExecutionResult.Failed("Command dispatch was rejected.")
    }

    private fun renderCommand(
        template: String,
        context: RewardActionExecutionContext,
    ): String {
        require(template.isNotBlank() && !template.startsWith("/")) { "Stored Reward command is invalid." }
        require(template.none { it == '\r' || it == '\n' || it.code < 0x20 }) {
            "Stored Reward command contains control characters."
        }
        val values = mapOf(
            "recipient_uuid" to context.recipientId.toString(),
            "recipient_name" to context.recipientName,
            "inviter_uuid" to context.inviterId?.toString(),
            "inviter_name" to context.inviterName,
            "invitee_uuid" to context.inviteeId?.toString(),
            "invitee_name" to context.inviteeName,
        )
        val rendered = PLACEHOLDER.replace(template) { match ->
            val name = match.groupValues[1]
            require(name in values) { "Stored Reward command contains unsupported placeholder '{$name}'." }
            requireNotNull(values[name]) { "Reward command placeholder '{$name}' is unavailable." }
        }
        require(rendered.none { it == '{' || it == '}' }) { "Stored Reward command contains malformed braces." }
        return rendered
    }

    private fun String.toActionTypeOrNull(): RewardActionType? = try {
        RewardActionType.valueOf(uppercase(Locale.ROOT))
    } catch (_: IllegalArgumentException) {
        null
    }

    private val PLACEHOLDER = Regex("\\{([a-z_]+)}")
    private val MESSAGE_KEY = Regex("^[a-z0-9][a-z0-9._-]*$")
}
