package fun.bm.mili.command.counter.sub;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.LiteralNode;
import org.leavesmc.leaves.util.HopperCounter;

public class ToggleCommand extends LiteralNode {

    public ToggleCommand() {
        super("toggle");
    }

    @Override
    public boolean requires(CommandSourceStack source) {
        return source.getSender().hasPermission("mili.commands.counter.toggle");
    }

    @Override
    public boolean execute(CommandContext context) {
        HopperCounter.setEnabled(!HopperCounter.isEnabled());
        String status = HopperCounter.isEnabled() ? "enabled" : "disabled";
        context.sender.sendMessage(Component.text("Hopper Counter " + status, NamedTextColor.GREEN));
        return true;
    }
}
