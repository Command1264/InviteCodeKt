package io.github.command1264.invitecode.platform.bukkit

import org.bukkit.entity.Player
import java.util.UUID

data class NativeDialogRequest(
    val title: String,
    val label: String,
    val confirm: String,
    val cancel: String,
    val maximumLength: Int,
)

data class NativeDialogConfirmationRequest(
    val title: String,
    val inviterLine: String,
    val codeLabel: String,
    val code: String,
    val codeRgb: Int?,
    val question: String,
    val timeoutPrompt: String,
    val agree: String,
    val disagree: String,
    val agreeHint: String,
    val disagreeHint: String,
)

interface NativeDialogInput : AutoCloseable {
    val available: Boolean

    fun show(
        player: Player,
        request: NativeDialogRequest,
        onSubmit: (String) -> Unit,
        onCancel: () -> Unit,
    ): Boolean

    fun showConfirmation(
        player: Player,
        request: NativeDialogConfirmationRequest,
        onConfirm: () -> Unit,
        onDecline: () -> Unit,
    ): Boolean = false

    fun cancel(player: Player)

    fun disconnect(playerId: UUID)

    override fun close()
}

object UnavailableNativeDialogInput : NativeDialogInput {
    override val available: Boolean = false

    override fun show(
        player: Player,
        request: NativeDialogRequest,
        onSubmit: (String) -> Unit,
        onCancel: () -> Unit,
    ): Boolean = false

    override fun cancel(player: Player) = Unit

    override fun disconnect(playerId: UUID) = Unit

    override fun close() = Unit
}
