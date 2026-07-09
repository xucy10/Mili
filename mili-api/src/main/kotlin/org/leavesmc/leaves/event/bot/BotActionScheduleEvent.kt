@file:JvmName("BotActionScheduleEventKt")
package org.leavesmc.leaves.event.bot

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent
import java.util.UUID

class BotActionScheduleEvent(
    val bot: Player,
    val actionName: String,
    val actionUuid: UUID?,
    val sender: CommandSender?
) : BukkitEvent(), Cancellable {

    fun callEvent(): Boolean {
        return !_cancelled
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
