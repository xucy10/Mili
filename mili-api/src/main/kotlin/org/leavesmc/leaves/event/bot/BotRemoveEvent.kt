@file:JvmName("BotRemoveEventKt")
package org.leavesmc.leaves.event.bot

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent

class BotRemoveEvent(
    val bot: Player,
    val save: Boolean
) : BukkitEvent(), Cancellable {

    enum class RemoveReason { INTERNAL, DEATH, PLUGIN }

    var reason: RemoveReason = RemoveReason.PLUGIN
        private set
    var remover: CommandSender? = null
        private set
    var resume = false
        private set
    var removeMessage: Component? = null
    var async = false
        private set

    constructor(bot: Player, reason: RemoveReason, remover: CommandSender?, save: Boolean, resume: Boolean)
        : this(bot, save) {
        this.reason = reason
        this.remover = remover
        this.resume = resume
    }

    constructor(bot: Player, reason: RemoveReason, remover: CommandSender?, save: Boolean, resume: Boolean, async: Boolean)
        : this(bot, reason, remover, save, resume) {
        this.async = async
    }

    constructor(bot: Player, remover: CommandSender?, reason: RemoveReason, save: Boolean, resume: Boolean)
        : this(bot, reason, remover, save, resume)

    constructor(bot: Player, reason: RemoveReason, remover: CommandSender?, removeMessage: Component?, save: Boolean)
        : this(bot, save) {
        this.reason = reason
        this.remover = remover
        this.removeMessage = removeMessage
    }

    fun getReason() = reason
    fun shouldSave() = save
    fun shouldResume() = resume

    private var _cancelled = false
    override fun isCancelled() = _cancelled
    override fun setCancelled(cancel: Boolean) { _cancelled = cancel }

    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
    override fun getHandlers() = HANDLERS
}
