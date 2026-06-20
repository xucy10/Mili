package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.WorldData;
import org.slf4j.Logger;

public class ReloadCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void reloadPacks(Collection<String> selectedIds, CommandSourceStack source) {
        source.getServer().reloadResources(selectedIds, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.COMMAND).exceptionally(throwable -> { // Paper - Add ServerResourcesReloadedEvent
            LOGGER.warn("Failed to execute reload", throwable);
            source.sendFailure(Component.translatable("commands.reload.failure"));
            return null;
        });
    }

    private static Collection<String> discoverNewPacks(PackRepository packRepository, WorldData worldData, Collection<String> selectedIds) {
        packRepository.reload(true); // Paper - will perform a full reload
        Collection<String> list = Lists.newArrayList(selectedIds);
        Collection<String> disabled = worldData.getDataConfiguration().dataPacks().getDisabled();

        for (String string : packRepository.getAvailableIds()) {
            if (!disabled.contains(string) && !list.contains(string)) {
                list.add(string);
            }
        }

        return list;
    }

    // CraftBukkit start
    public static void reload(MinecraftServer server) {
        PackRepository packRepository = server.getPackRepository();
        WorldData worldData = server.getWorldData();
        Collection<String> selectedIds = packRepository.getSelectedIds();
        Collection<String> collection = discoverNewPacks(packRepository, worldData, selectedIds);
        server.reloadResources(collection, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.PLUGIN); // Paper - Add ServerResourcesReloadedEvent
    }
    // CraftBukkit end

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(commandContext -> {
            CommandSourceStack commandSourceStack = commandContext.getSource();
            MinecraftServer server = commandSourceStack.getServer();
            PackRepository packRepository = server.getPackRepository();
            WorldData worldData = server.getWorldData();
            Collection<String> selectedIds = packRepository.getSelectedIds();
            Collection<String> collection = discoverNewPacks(packRepository, worldData, selectedIds);
            commandSourceStack.sendSuccess(() -> Component.translatable("commands.reload.success"), true);
            reloadPacks(collection, commandSourceStack);
            return 0;
        }));
    }
}
