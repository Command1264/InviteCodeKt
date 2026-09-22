package io.github.command1264.invitecode.platform.modern

import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.platform.bukkit.InviteCodeDisplayFormatter
import io.github.command1264.invitecode.platform.bukkit.LegacyInviteCodeDisplayFormatter
import io.github.command1264.invitecode.platform.bukkit.NativeDialogInput
import io.github.command1264.invitecode.platform.bukkit.PlatformCapabilities
import org.bukkit.ChatColor
import org.bukkit.plugin.Plugin

data class ModernPlatformIntegration(
    val capabilities: PlatformCapabilities,
    val dialogInput: NativeDialogInput,
    val displayFormatter: InviteCodeDisplayFormatter,
)

object ModernPlatformCapabilities {
    fun initialize(plugin: Plugin): ModernPlatformIntegration {
        val dialog = ReflectiveNativeDialogInput.create(plugin, plugin.logger)
        val formatter = RgbInviteCodeDisplayFormatter.create()
        return ModernPlatformIntegration(
            capabilities = PlatformCapabilities(
                nativeDialogInput = dialog.available,
                rgbText = formatter !== LegacyInviteCodeDisplayFormatter,
                dialogProvider = (dialog as? ReflectiveNativeDialogInput)?.provider ?: "NONE",
            ),
            dialogInput = dialog,
            displayFormatter = formatter,
        )
    }
}

internal class RgbInviteCodeDisplayFormatter(
    private val colorPrefix: (String) -> String,
) : InviteCodeDisplayFormatter {
    override fun display(code: InviteCode): String {
        if (code.color == null) return code.displayValue()
        return colorPrefix("#${code.normalized}") + code.displayValue() + ChatColor.RESET
    }

    companion object {
        fun create(): InviteCodeDisplayFormatter = try {
            val type = Class.forName("net.md_5.bungee.api.ChatColor")
            val method = type.getMethod("of", String::class.java)
            RgbInviteCodeDisplayFormatter { value -> method.invoke(null, value).toString() }
        } catch (_: ReflectiveOperationException) {
            LegacyInviteCodeDisplayFormatter
        } catch (_: LinkageError) {
            LegacyInviteCodeDisplayFormatter
        }
    }
}
