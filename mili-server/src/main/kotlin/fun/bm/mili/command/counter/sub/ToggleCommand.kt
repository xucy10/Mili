package `fun`.bm.mili.command.counter.sub

import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.leavesmc.leaves.command.CommandContext
import org.leavesmc.leaves.command.LiteralNode
import org.leavesmc.leaves.util.HopperCounter

class ToggleCommand : LiteralNode("toggle") {

    override fun requires(source: CommandSourceStack): Boolean =
        source.sender.hasPermission("mili.commands.counter.toggle")

    override fun execute(context: CommandContext): Boolean {
        HopperCounter.setEnabled(!HopperCounter.isEnabled())
        val status = if (HopperCounter.isEnabled()) "enabled" else "disabled"
        context.sender.sendMessage(Component.text("Hopper Counter $status", NamedTextColor.GREEN))
        return true
    }
}
