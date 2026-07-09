package `fun`.bm.mili.command.counter.sub

import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.server.MinecraftServer
import org.leavesmc.leaves.command.CommandContext
import org.leavesmc.leaves.command.LiteralNode
import org.leavesmc.leaves.util.HopperCounter

class ResetCommand : LiteralNode("reset") {

    override fun requires(source: CommandSourceStack): Boolean =
        source.sender.hasPermission("mili.commands.counter.reset")

    override fun execute(context: CommandContext): Boolean {
        HopperCounter.resetAll(MinecraftServer.getServer(), false)
        context.sender.sendMessage(Component.text("Counters reset.", NamedTextColor.GREEN))
        return true
    }
}
