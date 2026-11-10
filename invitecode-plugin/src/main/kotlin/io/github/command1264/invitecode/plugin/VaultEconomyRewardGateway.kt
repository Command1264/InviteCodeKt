package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.EconomyRewardGateway
import net.milkbowl.vault.economy.Economy
import org.bukkit.Server
import java.math.BigDecimal
import java.util.UUID

class VaultEconomyRewardGateway private constructor(
    private val server: Server,
    private val economy: Economy,
) : EconomyRewardGateway {
    override fun deposit(playerId: UUID, amount: BigDecimal): Boolean {
        val value = amount.toDouble()
        require(value.isFinite() && value > 0.0) { "Vault economy amount is outside the supported range." }
        return economy.depositPlayer(server.getOfflinePlayer(playerId), value).transactionSuccess()
    }

    companion object {
        @JvmStatic
        fun find(server: Server): VaultEconomyRewardGateway? {
            if (!server.pluginManager.isPluginEnabled("Vault")) return null
            val provider = server.servicesManager.getRegistration(Economy::class.java)?.provider ?: return null
            return VaultEconomyRewardGateway(server, provider)
        }
    }
}
