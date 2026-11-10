package io.github.command1264.invitecode.platform.bukkit

enum class InputMode {
    AUTO,
    DIALOG,
    CHAT,
    COMMAND_ONLY,
}

enum class ClaimInputChannel {
    DIALOG,
    CHAT,
    COMMAND_ONLY,
}

data class PlatformCapabilities(
    val nativeDialogInput: Boolean,
    val rgbText: Boolean,
    val dialogProvider: String = "NONE",
)

fun InputMode.resolve(capabilities: PlatformCapabilities): ClaimInputChannel = when (this) {
    InputMode.COMMAND_ONLY -> ClaimInputChannel.COMMAND_ONLY
    InputMode.CHAT -> ClaimInputChannel.CHAT
    InputMode.AUTO,
    InputMode.DIALOG -> if (capabilities.nativeDialogInput) {
        ClaimInputChannel.DIALOG
    } else {
        ClaimInputChannel.CHAT
    }
}
