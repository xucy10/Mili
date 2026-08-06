package fun.bm.mili.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fun.bm.mili.portal.PortalLinkManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;

import java.util.Map;

public class PortalCommand extends RootNode {
    private static final String PERM_BASE = "mili.admin.portal";

    public PortalCommand() {
        super("portal", PERM_BASE);
        children(
                new ListCommand(),
                new RemoveCommand(),
                new ClearCommand(),
                new InfoCommand(),
                new ReloadCommand()
        );
    }

    @Override
    public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
        return source.getSender().hasPermission(PERM_BASE);
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
        sendHelp(context.getSender());
        return true;
    }

    private static void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Mili Portal ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /portal list ", NamedTextColor.GRAY).append(Component.text("- List all portal pairs", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /portal remove <key> ", NamedTextColor.GRAY).append(Component.text("- Remove a portal pair", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /portal clear ", NamedTextColor.GRAY).append(Component.text("- Remove all portal pairs", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /portal info ", NamedTextColor.GRAY).append(Component.text("- Show portal info at your position", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /portal reload ", NamedTextColor.GRAY).append(Component.text("- Reload portal links from disk", NamedTextColor.WHITE)));
    }

    private static class ListCommand extends org.leavesmc.leaves.command.LiteralNode {
        ListCommand() {
            super("list");
        }

        @Override
        public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
            return source.getSender().hasPermission(PERM_BASE);
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            CommandSender sender = context.getSender();
            Map<String, PortalLinkManager.PortalPair> pairs = PortalLinkManager.getAllPairs();
            sender.sendMessage(Component.text("=== Portal Links (" + pairs.size() + ") ===", NamedTextColor.GOLD));
            for (Map.Entry<String, PortalLinkManager.PortalPair> entry : pairs.entrySet()) {
                PortalLinkManager.PortalPair p = entry.getValue();
                sender.sendMessage(Component.text("  " + entry.getKey(), NamedTextColor.WHITE));
                sender.sendMessage(Component.text("    From: ", NamedTextColor.GRAY)
                        .append(Component.text(p.getSourceWorld() + " (" + p.getSourceX() + ", " + p.getSourceY() + ", " + p.getSourceZ() + ")", NamedTextColor.AQUA)));
                sender.sendMessage(Component.text("    To:   ", NamedTextColor.GRAY)
                        .append(Component.text(p.getDestWorld() + " (" + p.getDestX() + ", " + p.getDestY() + ", " + p.getDestZ() + ")", NamedTextColor.GREEN)));
            }
            return true;
        }
    }

    private static class RemoveCommand extends org.leavesmc.leaves.command.LiteralNode {
        RemoveCommand() {
            super("remove");
        }

        @Override
        public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
            return source.getSender().hasPermission(PERM_BASE);
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            CommandSender sender = context.getSender();
            String key = context.getStringOrDefault("key", null);
            if (key == null) {
                sender.sendMessage(Component.text("Usage: /portal remove <key>", NamedTextColor.RED));
                sender.sendMessage(Component.text("Use /portal list to see keys", NamedTextColor.GRAY));
                return true;
            }
            if (PortalLinkManager.removePair(key)) {
                sender.sendMessage(Component.text("Removed portal pair: " + key, NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Portal pair not found: " + key, NamedTextColor.RED));
            }
            return true;
        }
    }

    private static class ClearCommand extends org.leavesmc.leaves.command.LiteralNode {
        ClearCommand() {
            super("clear");
        }

        @Override
        public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
            return source.getSender().hasPermission(PERM_BASE);
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            CommandSender sender = context.getSender();
            int count = PortalLinkManager.getAllPairs().size();
            for (String key : PortalLinkManager.getAllPairs().keySet()) {
                PortalLinkManager.removePair(key);
            }
            sender.sendMessage(Component.text("Cleared all " + count + " portal pairs.", NamedTextColor.GREEN));
            return true;
        }
    }

    private static class InfoCommand extends org.leavesmc.leaves.command.LiteralNode {
        InfoCommand() {
            super("info");
        }

        @Override
        public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
            return source.getSender().hasPermission(PERM_BASE);
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            CommandSender sender = context.getSender();
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
                return true;
            }
            Location loc = player.getLocation();
            String key = PortalLinkManager.locationKey(loc);
            PortalLinkManager.PortalPair pair = PortalLinkManager.findPair(loc);
            sender.sendMessage(Component.text("=== Portal Info ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("  Position: ", NamedTextColor.GRAY).append(Component.text(key, NamedTextColor.WHITE)));
            if (pair != null) {
                sender.sendMessage(Component.text("  Linked To: ", NamedTextColor.GRAY)
                        .append(Component.text(pair.getDestWorld() + " (" + pair.getDestX() + ", " + pair.getDestY() + ", " + pair.getDestZ() + ")", NamedTextColor.GREEN)));
            } else {
                sender.sendMessage(Component.text("  No portal link found at this position.", NamedTextColor.GRAY));
            }
            sender.sendMessage(Component.text("  Search Radius: ", NamedTextColor.GRAY).append(Component.text(PortalLinkManager.getSearchRadius(), NamedTextColor.WHITE)));
            return true;
        }
    }

    private static class ReloadCommand extends org.leavesmc.leaves.command.LiteralNode {
        ReloadCommand() {
            super("reload");
        }

        @Override
        public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
            return source.getSender().hasPermission(PERM_BASE);
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            PortalLinkManager.load();
            context.getSender().sendMessage(Component.text("Portal links reloaded from disk.", NamedTextColor.GREEN));
            return true;
        }
    }
}
