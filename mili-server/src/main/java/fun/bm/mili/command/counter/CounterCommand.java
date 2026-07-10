package fun.bm.mili.command.counter;

import fun.bm.mili.command.counter.sub.DisplayCommand;
import fun.bm.mili.command.counter.sub.ResetCommand;
import fun.bm.mili.command.counter.sub.ToggleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;
import org.leavesmc.leaves.util.HopperCounter;

public class CounterCommand extends RootNode {

    private static final String PERM_BASE = "mili.commands.counter";

    public CounterCommand() {
        super("counter", PERM_BASE);
        children(ToggleCommand::new, ResetCommand::new, DisplayCommand::new);
    }

    @Override
    public boolean execute(CommandContext context) {
        context.getSender().sendMessage(
            Component.join(
                JoinConfiguration.noSeparators(),
                Component.text("Hopper Counter: ", NamedTextColor.GRAY),
                Component.text(
                    HopperCounter.isEnabled(),
                    HopperCounter.isEnabled() ? NamedTextColor.AQUA : NamedTextColor.GRAY
                )
            )
        );
        return true;
    }

    public static boolean hasPermission(CommandSender sender, String... subcommand) {
        String suffix = String.join(".", subcommand);
        if (suffix.isEmpty()) {
            return sender.hasPermission(PERM_BASE);
        }
        return sender.hasPermission(PERM_BASE) || sender.hasPermission(PERM_BASE + "." + suffix);
    }
}