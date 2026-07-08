@file:JvmName("CounterCommandKt")

package fun.bm.mili.command.counter

import com.mojang.brigadier.exceptions.CommandSyntaxException
import fun.bm.mili.command.counter.sub.DisplayCommand
import fun.bm.mili.command.counter.sub.ResetCommand
import fun.bm.mili.command.counter.sub.ToggleCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import org.leavesmc.leaves.command.CommandContext
import org.leavesmc.leaves.command.RootNode
import org.leavesmc.leaves.util.HopperCounter

class CounterCommand : RootNode("counter", "mili.commands.counter") {

    private val permBase = "mili.commands.counter"

    init {
        children(
            ToggleCommand(this),
            ResetCommand(this),
            DisplayCommand(this),
        )
    }

    override fun execute(context: CommandContext): Boolean {
        context.sender.sendMessage(
            Component.join(
                JoinConfiguration.noSeparators(),
                Component.text("Hopper Counter: ", NamedTextColor.GRAY),
                Component.text(
                    HopperCounter.isEnabled(),
                    if (HopperCounter.isEnabled()) NamedTextColor.AQUA else NamedTextColor.GRAY,
                ),
            ),
        )
        return true
    }

    fun hasPermission(sender: org.bukkit.command.CommandSender, vararg subcommand: String): Boolean =
        hasPermission(permBase, sender, *subcommand)
}