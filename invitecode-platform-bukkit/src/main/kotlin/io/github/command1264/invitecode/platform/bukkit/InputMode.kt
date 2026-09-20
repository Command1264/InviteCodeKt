package io.github.command1264.invitecode.platform.bukkit

enum class InputMode {
    AUTO,
    DIALOG,
    CHAT,
    COMMAND_ONLY,
}

interface PlatformCapabilities {
    val nativeDialogInput: Boolean
}

object LegacyPlatformCapabilities : PlatformCapabilities {
    override val nativeDialogInput: Boolean = false
}
