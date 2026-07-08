@file:JvmName("ToggleCommandKt")
package fun.bm.mili.command.counter.sub
import org.leavesmc.leaves.command.CommandContext
import org.leavesmc.leaves.command.RootNode
import org.leavesmc.leaves.command.SubNode
import org.leavesmc.leaves.util.HopperCounter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class ToggleCommand(parent: RootNode) : SubNode("toggle", "mili.commands.counter.toggle", parent) {
    override fun execute(context: CommandContext): Boolean {
        HopperCounter.toggle()
        val status = if (HopperCounter.isEnabled()) "enabled" else "disabled"
        context.sender.sendMessage(Component.text("Hopper Counter $status", NamedTextColor.GREEN))
        return true
    }
}