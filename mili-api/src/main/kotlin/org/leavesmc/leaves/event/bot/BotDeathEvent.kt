@file:JvmName("BotDeathEventKt")
package org.leavesmc.leaves.event.bot

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent

class BotDeathEvent(
    val bot: Player,
    @get:JvmName("deathMessage") var deathMessage: Component?,
    val sendDeathMessage: Boolean
) : BukkitEvent(), Cancellable {

    fun isSendDeathMessage() = sendDeathMessage

    private var _cancelled = false
    override fun isCancelled() = _cancelled
    override fun setCancelled(cancel: Boolean) { _cancelled = cancel }

    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
    override fun getHandlers() = HANDLERS
}
