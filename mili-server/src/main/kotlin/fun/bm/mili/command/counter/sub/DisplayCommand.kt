package `fun`.bm.mili.command.counter.sub

import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.DyeColor
import org.leavesmc.leaves.command.CommandContext
import org.leavesmc.leaves.command.LiteralNode
import org.leavesmc.leaves.util.HopperCounter

class DisplayCommand : LiteralNode("display") {

    override fun requires(source: CommandSourceStack): Boolean =
        source.sender.hasPermission("mili.commands.counter.display")

    override fun execute(context: CommandContext): Boolean {
        val total = DyeColor.values().sumOf { HopperCounter.getCounter(it).getTotalItems() }
        context.sender.sendMessage(Component.text("Hoppers: $total", NamedTextColor.GRAY))
        return true
    }
}
