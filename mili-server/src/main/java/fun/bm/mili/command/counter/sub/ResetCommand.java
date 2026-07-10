package fun.bm.mili.command.counter.sub;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.MinecraftServer;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.LiteralNode;
import org.leavesmc.leaves.util.HopperCounter;

public class ResetCommand extends LiteralNode {

    public ResetCommand() {
        super("reset");
    }

    @Override
    public boolean requires(CommandSourceStack source) {
        return source.getSender().hasPermission("mili.commands.counter.reset");
    }

    @Override
    public boolean execute(CommandContext context) {
        HopperCounter.resetAll(MinecraftServer.getServer(), false);
        context.getSender().sendMessage(Component.text("Counters reset.", NamedTextColor.GREEN));
        return true;
    }
}