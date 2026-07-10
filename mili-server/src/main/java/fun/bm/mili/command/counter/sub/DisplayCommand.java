package fun.bm.mili.command.counter.sub;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.world.item.DyeColor;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.LiteralNode;
import org.leavesmc.leaves.util.HopperCounter;

public class DisplayCommand extends LiteralNode {

    public DisplayCommand() {
        super("display");
    }

    @Override
    public boolean requires(CommandSourceStack source) {
        return source.getSender().hasPermission("mili.commands.counter.display");
    }

    @Override
    public boolean execute(CommandContext context) {
        long total = 0;
        for (DyeColor color : DyeColor.values()) {
            total += HopperCounter.getCounter(color).getTotalItems();
        }
        context.getSender().sendMessage(Component.text("Hoppers: " + total, NamedTextColor.GRAY));
        return true;
    }
}