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

interface NativeDialogInput : AutoCloseable {
    val available: Boolean

    fun show(
        player: Player,
        request: NativeDialogRequest,
        onSubmit: (String) -> Unit,
        onCancel: () -> Unit,
    ): Boolean

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
