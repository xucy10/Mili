@file:JvmName("BotActionExecuteEventKt")
package org.leavesmc.leaves.event.bot

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent
import java.util.UUID

class BotActionExecuteEvent(
    val bot: Player,
    val actionName: String,
    val actionUuid: UUID?
) : BukkitEvent(), Cancellable {

    enum class Result { ALLOW, SOFT_CANCEL, HARD_CANCEL }

    var result: Result = Result.ALLOW
        private set

    private var _cancelled = false
    override fun isCancelled() = _cancelled
    override fun setCancelled(cancel: Boolean) { _cancelled = cancel }

    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
    override fun getHandlers() = HANDLERS
}
