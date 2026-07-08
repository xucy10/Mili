@file:JvmName("BotConfigModifyEventKt")
package org.leavesmc.leaves.event.bot
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent
class BotConfigModifyEvent(val botName: String, val key: String, val oldValue: Any?, val newValue: Any?) : BukkitEvent(), Cancellable {
    override var cancelled: Boolean = false
    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
    override fun getHandlers() = HANDLERS
}