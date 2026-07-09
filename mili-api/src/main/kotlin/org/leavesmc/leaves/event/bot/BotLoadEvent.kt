@file:JvmName("BotLoadEventKt")
package org.leavesmc.leaves.event.bot

import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent
import java.util.UUID

class BotLoadEvent(val botName: String) : BukkitEvent(), Cancellable {
    var uuid: UUID? = null
        private set

    constructor(botName: String, uuid: UUID) : this(botName) {
        this.uuid = uuid
    }

    private var _cancelled = false
    override fun isCancelled() = _cancelled
    override fun setCancelled(cancel: Boolean) { _cancelled = cancel }

    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
    override fun getHandlers() = HANDLERS
}
