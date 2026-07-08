@file:JvmName("BotInventoryOpenEventKt")
package org.leavesmc.leaves.event.bot
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.inventory.Inventory
import org.leavesmc.leaves.event.BukkitEvent
class BotInventoryOpenEvent(val botName: String, val inventory: Inventory) : BukkitEvent(), Cancellable {
    override var cancelled = false
    companion object { @JvmStatic val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
    override fun getHandlers() = HANDLERS
}