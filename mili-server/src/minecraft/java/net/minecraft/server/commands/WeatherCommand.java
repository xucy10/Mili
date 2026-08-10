package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;

public class WeatherCommand {
    private static final int DEFAULT_TIME = -1;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("weather")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("clear")
                        .executes(commandContext -> setClear(commandContext.getSource(), -1))
                        .then(
                            Commands.argument("duration", TimeArgument.time(1))
                                .executes(context -> setClear(context.getSource(), IntegerArgumentType.getInteger(context, "duration")))
                        )
                )
                .then(
                    Commands.literal("rain")
                        .executes(context -> setRain(context.getSource(), -1))
                        .then(
                            Commands.argument("duration", TimeArgument.time(1))
                                .executes(context -> setRain(context.getSource(), IntegerArgumentType.getInteger(context, "duration")))
                        )
                )
                .then(
                    Commands.literal("thunder")
                        .executes(context -> setThunder(context.getSource(), -1))
                        .then(
                            Commands.argument("duration", TimeArgument.time(1))
                                .executes(context -> setThunder(context.getSource(), IntegerArgumentType.getInteger(context, "duration")))
                        )
                )
        );
    }

    private static int getDuration(CommandSourceStack source, int time, IntProvider timeProvider) {
        return time == -1 ? timeProvider.sample(source.getLevel().getRandom()) : time; // CraftBukkit - SPIGOT-7680: per-world
    }

    private static int setClear(CommandSourceStack source, int time) {
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
        source.getLevel().setWeatherParameters(getDuration(source, time, ServerLevel.RAIN_DELAY), 0, false, false); // CraftBukkit - SPIGOT-7680: per-world
        source.sendSuccess(() -> Component.translatable("commands.weather.set.clear"), true);
        }); // Folia - region threading
        return time;
    }

    private static int setRain(CommandSourceStack source, int time) {
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
        source.getLevel().setWeatherParameters(0, getDuration(source, time, ServerLevel.RAIN_DURATION), true, false); // CraftBukkit - SPIGOT-7680: per-world
        source.sendSuccess(() -> Component.translatable("commands.weather.set.rain"), true);
        }); // Folia - region threading
        return time;
    }

    private static int setThunder(CommandSourceStack source, int time) {
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
        source.getLevel().setWeatherParameters(0, getDuration(source, time, ServerLevel.THUNDER_DURATION), true, true); // CraftBukkit - SPIGOT-7680: per-world
        source.sendSuccess(() -> Component.translatable("commands.weather.set.thunder"), true);
        }); // Folia - region threading
        return time;
    }
}
