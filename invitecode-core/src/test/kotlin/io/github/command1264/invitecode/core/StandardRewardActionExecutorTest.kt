package io.github.command1264.invitecode.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class StandardRewardActionExecutorTest {
    @Test
    fun `console command expands only validated execution context placeholders`() {
        val environment = RecordingRewardEnvironment()
        val executor = StandardRewardActionExecutor(environment)
        val action = action("CONSOLE_COMMAND", "give {recipient_name} diamond 1")

        val result = executor.execute(action, context())

        assertEquals(RewardActionExecutionResult.Delivered, result)
        assertEquals(listOf("give Invitee diamond 1"), environment.consoleCommands)
    }

    @Test
    fun `player command requires online recipient and never requests operator state`() {
        val environment = RecordingRewardEnvironment(online = false)
        val executor = StandardRewardActionExecutor(environment)
        val action = action("PLAYER_COMMAND", "spawn")

        assertEquals(RewardActionReadiness.RECIPIENT_OFFLINE, executor.readiness(action, context()))
        assertEquals(emptyList<String>(), environment.playerCommands)
    }

    @Test
    fun `player disconnect before dispatch defers delivery`() {
        val environment = RecordingRewardEnvironment(onlineActionResult = OnlineRewardActionResult.RECIPIENT_OFFLINE)
        val executor = StandardRewardActionExecutor(environment)
        val action = action("PLAYER_COMMAND", "spawn")

        val result = executor.execute(action, context())

        assertEquals(
            RewardActionExecutionResult.Deferred(RewardActionReadiness.RECIPIENT_OFFLINE),
            result,
        )
        assertEquals(listOf("spawn"), environment.playerCommands)
    }

    @Test
    fun `message sends locale key only when recipient is online`() {
        val environment = RecordingRewardEnvironment()
        val executor = StandardRewardActionExecutor(environment)
        val action = action("MESSAGE", "invite.qualified.invitee")

        val result = executor.execute(action, context())

        assertEquals(RewardActionExecutionResult.Delivered, result)
        assertEquals(listOf("invite.qualified.invitee"), environment.messages)
    }

    @Test
    fun `Vault action stays pending when economy integration is unavailable`() {
        val environment = RecordingRewardEnvironment(economyAvailable = false)
        val executor = StandardRewardActionExecutor(environment)
        val action = action("VAULT_ECONOMY", "100.5")

        assertEquals(RewardActionReadiness.INTEGRATION_UNAVAILABLE, executor.readiness(action, context()))
        assertEquals(emptyList<BigDecimal>(), environment.deposits)
    }

    @Test
    fun `Vault rejection is a terminal failure`() {
        val environment = RecordingRewardEnvironment(economyAccepted = false)
        val executor = StandardRewardActionExecutor(environment)
        val action = action("VAULT_ECONOMY", "100.5")

        val result = executor.execute(action, context())

        assertEquals(
            RewardActionExecutionResult.Failed("Vault economy deposit was rejected."),
            result,
        )
        assertEquals(listOf(BigDecimal("100.5")), environment.deposits)
    }

    @Test
    fun `missing command placeholder context fails before dispatch`() {
        val environment = RecordingRewardEnvironment()
        val executor = StandardRewardActionExecutor(environment)
        val action = action("CONSOLE_COMMAND", "give {inviter_name} diamond 1")
        val contextWithoutInviterName = context().copy(inviterName = null)

        val result = executor.execute(action, contextWithoutInviterName)

        assertEquals(
            RewardActionExecutionResult.Failed("Reward command context is invalid."),
            result,
        )
        assertEquals(emptyList<String>(), environment.consoleCommands)
    }

    private fun action(type: String, payload: String) = StoredRewardAction(
        id = UUID.randomUUID(),
        rewardDeliveryId = UUID.randomUUID(),
        invitationId = UUID.randomUUID(),
        recipientId = recipientId,
        position = 0,
        type = type,
        payload = payload,
        status = RewardActionStatus.PROCESSING,
        idempotencyKey = "test-action",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH.plusNanos(1),
    )

    private fun context() = RewardActionExecutionContext(
        recipientId,
        "Invitee",
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        "Inviter",
        recipientId,
        "Invitee",
    )

    companion object {
        private val recipientId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }
}

private class RecordingRewardEnvironment(
    private val online: Boolean = true,
    private val economyAvailable: Boolean = true,
    private val economyAccepted: Boolean = true,
    private val onlineActionResult: OnlineRewardActionResult = OnlineRewardActionResult.ACCEPTED,
) : RewardExecutionEnvironment {
    val consoleCommands = mutableListOf<String>()
    val playerCommands = mutableListOf<String>()
    val messages = mutableListOf<String>()
    val deposits = mutableListOf<BigDecimal>()

    override fun isOnline(playerId: UUID) = online

    override fun dispatchConsole(command: String): Boolean {
        consoleCommands += command
        return true
    }

    override fun dispatchPlayer(playerId: UUID, command: String): OnlineRewardActionResult {
        playerCommands += command
        return onlineActionResult
    }

    override fun sendMessage(playerId: UUID, localeKey: String): OnlineRewardActionResult {
        messages += localeKey
        return onlineActionResult
    }

    override fun economyAvailable() = economyAvailable

    override fun deposit(playerId: UUID, amount: BigDecimal): Boolean {
        deposits += amount
        return economyAccepted
    }
}
