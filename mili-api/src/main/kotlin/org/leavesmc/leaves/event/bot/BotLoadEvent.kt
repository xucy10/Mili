@file:JvmName("BotLoadEventKt")
package org.leavesmc.leaves.event.bot
import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent
class BotLoadEvent(val botName: String) : BukkitEvent() {
    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
    override fun getHandlers() = HANDLERS
}