@file:JvmName("BotRemoveEventKt")
package org.leavesmc.leaves.event.bot

import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent

class BotRemoveEvent(val botName: String, val save: Boolean) : BukkitEvent() {
    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
    override fun getHandlers() = HANDLERS
}