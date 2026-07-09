package fun.bm.mili.command.counter.sub
import com.mojang.brigadier.exceptions.CommandSyntaxException
import org.leavesmc.leaves.command.CommandContext
import org.leavesmc.leaves.command.RootNode
import org.leavesmc.leaves.command.SubNode
import org.leavesmc.leaves.util.HopperCounter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class ResetCommand(parent: RootNode) : SubNode("reset", "mili.commands.counter.reset", parent) {
    override fun execute(context: CommandContext): Boolean {
        HopperCounter.reset()
        context.sender.sendMessage(Component.text("Counters reset.", NamedTextColor.GREEN))
        return true
    }
}