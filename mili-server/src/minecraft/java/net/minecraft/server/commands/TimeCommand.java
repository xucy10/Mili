package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class TimeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("time")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("set")
                        .then(Commands.literal("day").executes(context -> setTime(context.getSource(), 1000)))
                        .then(Commands.literal("noon").executes(context -> setTime(context.getSource(), 6000)))
                        .then(Commands.literal("night").executes(context -> setTime(context.getSource(), 13000)))
                        .then(Commands.literal("midnight").executes(context -> setTime(context.getSource(), 18000)))
                        .then(
                            Commands.argument("time", TimeArgument.time())
                                .executes(context -> setTime(context.getSource(), IntegerArgumentType.getInteger(context, "time")))
                        )
                )
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("time", TimeArgument.time())
                                .executes(context -> addTime(context.getSource(), IntegerArgumentType.getInteger(context, "time")))
                        )
                )
                .then(
                    Commands.literal("query")
                        .then(Commands.literal("daytime").executes(context -> queryTime(context.getSource(), getDayTime(context.getSource().getLevel()))))
                        .then(
                            Commands.literal("gametime")
                                .executes(context -> queryTime(context.getSource(), (int)(context.getSource().getLevel().getGameTime() % 2147483647L)))
                        )
                        .then(
                            Commands.literal("day")
                                .executes(context -> queryTime(context.getSource(), (int)(context.getSource().getLevel().getDayCount() % 2147483647L)))
                        )
                )
        );
    }

    private static int getDayTime(ServerLevel level) {
        return (int)(level.getDayTime() % 24000L);
    }

    private static int queryTime(CommandSourceStack source, int time) {
        source.sendSuccess(() -> Component.translatable("commands.time.query", time), false);
        return time;
    }

    public static int setTime(CommandSourceStack source, int time) {
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
        for (ServerLevel serverLevel : io.papermc.paper.configuration.GlobalConfiguration.get().commands.timeCommandAffectsAllWorlds ? source.getServer().getAllLevels() : java.util.List.of(source.getLevel())) { // CraftBukkit - SPIGOT-6496: Only set the time for the world the command originates in // Paper - add config option for spigot's change
            // serverLevel.setDayTime(time);
            // CraftBukkit start
            org.bukkit.event.world.TimeSkipEvent event = new org.bukkit.event.world.TimeSkipEvent(serverLevel.getWorld(), org.bukkit.event.world.TimeSkipEvent.SkipReason.COMMAND, time - serverLevel.getDayTime());
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                serverLevel.setDayTime(serverLevel.getDayTime() + event.getSkipAmount());
            }
            // CraftBukkit end
        }

        source.getServer().forceTimeSynchronization();
        source.sendSuccess(() -> Component.translatable("commands.time.set", time), true);
        }); // Folia - region threading
        return 0; // Folia - region threading
    }

    public static int addTime(CommandSourceStack source, int amount) {
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
        for (ServerLevel serverLevel : io.papermc.paper.configuration.GlobalConfiguration.get().commands.timeCommandAffectsAllWorlds ? source.getServer().getAllLevels() : java.util.List.of(source.getLevel())) { // CraftBukkit - SPIGOT-6496: Only set the time for the world the command originates in // Paper - add config option for spigot's change
            // CraftBukkit start
            org.bukkit.event.world.TimeSkipEvent event = new org.bukkit.event.world.TimeSkipEvent(serverLevel.getWorld(), org.bukkit.event.world.TimeSkipEvent.SkipReason.COMMAND, amount);
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                serverLevel.setDayTime(serverLevel.getDayTime() + event.getSkipAmount());
            }
            // CraftBukkit end
        }

        source.getServer().forceTimeSynchronization();
        int dayTime = getDayTime(source.getLevel());
        source.sendSuccess(() -> Component.translatable("commands.time.set", dayTime), true);
        }); // Folia - region threading
        return 0; // Folia - region threading
    }
}
