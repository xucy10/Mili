@file:JvmName("BotDeathEventKt")
package org.leavesmc.leaves.event.bot

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent

class BotDeathEvent(
    val bot: Player,
    var deathMessage: Component?,
    val sendDeathMessage: Boolean
) : BukkitEvent() {

    fun isSendDeathMessage() = sendDeathMessage

    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
    override fun getHandlers() = HANDLERS
}
