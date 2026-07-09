@file:JvmName("BotActionStopEventKt")
package org.leavesmc.leaves.event.bot

import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent

class BotActionStopEvent(val botName: String, val actionName: String) : BukkitEvent(), Cancellable {

    enum class Reason { DONE, PLUGIN }

    private var _cancelled = false
    override fun isCancelled() = _cancelled
    override fun setCancelled(cancel: Boolean) { _cancelled = cancel }

    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
    override fun getHandlers() = HANDLERS
}
