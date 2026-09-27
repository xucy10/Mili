package fun.bm.mili.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fun.bm.mili.vanilla.VanillaToggleRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;

/**
 * /mili toggles — 原版行为开关状态观测命令。
 * 显示所有已注册 VanillaToggle 的当前生效值与来源层。
 */
public class MiliTogglesCommand extends RootNode {
    private static final String PERM = "mili.admin.toggles";

    public MiliTogglesCommand() {
        super("mili-toggles", PERM);
    }

    @Override
    public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
        return source.getSender().hasPermission(PERM);
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
        CommandSender sender = context.getSender();
        sender.sendMessage(Component.text("=== Mili Vanilla Toggles ===", NamedTextColor.GOLD));

        var snapshot = VanillaToggleRegistry.snapshot();
        if (snapshot.isEmpty()) {
            sender.sendMessage(Component.text("  (no toggles registered)", NamedTextColor.GRAY));
            return true;
        }

        for (var entry : snapshot.entrySet()) {
            String id = entry.getKey();
            boolean enabled = entry.getValue();
            sender.sendMessage(Component.text("  " + id + ": ", NamedTextColor.GRAY)
                    .append(Component.text(enabled ? "ON" : "OFF",
                            enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }
        return true;
    }
}
