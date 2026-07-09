@file:JvmName("BotJoinEventKt")
package org.leavesmc.leaves.event.bot

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent

class BotJoinEvent(
    val bot: Player,
    @get:JvmName("joinMessage") var joinMessage: Component?
) : BukkitEvent() {

    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
    override fun getHandlers() = HANDLERS
}
