package io.github.command1264.invitecode.platform.modern

import io.github.command1264.invitecode.platform.bukkit.PlatformCapabilities

/**
 * Marker for the future native Dialog adapter. New Bukkit/Paper API references must remain in this module.
 */
object ModernPlatformCapabilities : PlatformCapabilities {
    override val nativeDialogInput: Boolean = true
}
