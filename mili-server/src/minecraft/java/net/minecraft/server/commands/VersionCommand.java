package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;

public class VersionCommand {
    private static final Component HEADER = Component.translatable("commands.version.header");
    private static final Component STABLE = Component.translatable("commands.version.stable.yes");
    private static final Component UNSTABLE = Component.translatable("commands.version.stable.no");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean isDedicatedServer) {
        dispatcher.register(
            Commands.literal("version")
                .requires(Commands.hasPermission(isDedicatedServer ? Commands.LEVEL_GAMEMASTERS : Commands.LEVEL_ALL))
                .executes(commandContext -> {
                    CommandSourceStack commandSourceStack = commandContext.getSource();
                    commandSourceStack.sendSystemMessage(HEADER);
                    dumpVersion(commandSourceStack::sendSystemMessage);
                    return 1;
                })
        );
    }

    public static void dumpVersion(Consumer<Component> output) {
        WorldVersion currentVersion = SharedConstants.getCurrentVersion();
        output.accept(Component.translatable("commands.version.id", currentVersion.id()));
        output.accept(Component.translatable("commands.version.name", currentVersion.name()));
        output.accept(Component.translatable("commands.version.data", currentVersion.dataVersion().version()));
        output.accept(Component.translatable("commands.version.series", currentVersion.dataVersion().series()));
        output.accept(
            Component.translatable("commands.version.protocol", currentVersion.protocolVersion(), "0x" + Integer.toHexString(currentVersion.protocolVersion()))
        );
        output.accept(Component.translatable("commands.version.build_time", Component.translationArg(currentVersion.buildTime())));
        output.accept(Component.translatable("commands.version.pack.resource", currentVersion.packVersion(PackType.CLIENT_RESOURCES).toString()));
        output.accept(Component.translatable("commands.version.pack.data", currentVersion.packVersion(PackType.SERVER_DATA).toString()));
        output.accept(currentVersion.stable() ? STABLE : UNSTABLE);
    }
}
