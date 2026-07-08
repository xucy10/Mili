@file:JvmName("DisplayCommandKt")
package fun.bm.mili.command.counter.sub
import com.mojang.brigadier.exceptions.CommandSyntaxException
import org.leavesmc.leaves.command.CommandContext
import org.leavesmc.leaves.command.SubNode
import org.leavesmc.leaves.util.HopperCounter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class DisplayCommand(parent: org.leavesmc.leaves.command.RootNode) : SubNode("display", "mili.commands.counter.display", parent) {
    override fun execute(context: CommandContext): Boolean {
        context.sender.sendMessage(
            Component.text("Hoppers: ${HopperCounter.getCount()}", NamedTextColor.GRAY)
        )
        return true
    }
}