@file:JvmName("BotCreateEventKt")

package org.leavesmc.leaves.event.bot

import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent

class BotCreateEvent(
    val bot: String,
    val skin: String?,
    var createLocation: Location,
    val reason: CreateReason,
    val creator: CommandSender?,
) : BukkitEvent(), Cancellable {

    override var cancelled: Boolean = false

    enum class CreateReason { COMMAND, PLUGIN, INTERNAL, UNKNOWN }

    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }

    override fun getHandlers() = HANDLERS
}