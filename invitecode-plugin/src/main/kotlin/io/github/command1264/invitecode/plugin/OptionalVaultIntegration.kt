package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.EconomyRewardGateway
import org.bukkit.Server
import java.util.logging.Level
import java.util.logging.Logger

internal object OptionalVaultIntegration {
    private const val ADAPTER_CLASS =
        "io.github.command1264.invitecode.plugin.VaultEconomyRewardGateway"

    fun find(server: Server, logger: Logger): EconomyRewardGateway? {
        if (!server.pluginManager.isPluginEnabled("Vault")) return null

        return try {
            val adapter = Class.forName(ADAPTER_CLASS, true, OptionalVaultIntegration::class.java.classLoader)
            adapter.getMethod("find", Server::class.java).invoke(null, server) as? EconomyRewardGateway
        } catch (error: ReflectiveOperationException) {
            logger.log(Level.WARNING, "Vault 已啟用，但 InviteCode 無法載入 Vault 經濟整合。", error)
            null
        } catch (error: LinkageError) {
            logger.log(Level.WARNING, "Vault 已啟用，但 Vault API 或經濟服務不相容。", error)
            null
        }
    }
}
