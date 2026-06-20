package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.util.TimeUtil;

public class TickCommand {
    private static final float MAX_TICKRATE = 10000.0F;
    private static final String DEFAULT_TICKRATE = String.valueOf(20);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("tick")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("query").executes(commandContext -> tickQuery(commandContext.getSource())))
                .then(
                    Commands.literal("rate")
                        .then(
                            Commands.argument("rate", FloatArgumentType.floatArg(1.0F, 10000.0F))
                                .suggests(
                                    (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(new String[]{DEFAULT_TICKRATE}, suggestionsBuilder)
                                )
                                .executes(commandContext -> setTickingRate(commandContext.getSource(), FloatArgumentType.getFloat(commandContext, "rate")))
                        )
                )
                .then(
                    Commands.literal("step")
                        .executes(commandContext -> step(commandContext.getSource(), 1))
                        .then(Commands.literal("stop").executes(commandContext -> stopStepping(commandContext.getSource())))
                        .then(
                            Commands.argument("time", TimeArgument.time(1))
                                .suggests(
                                    (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(new String[]{"1t", "1s"}, suggestionsBuilder)
                                )
                                .executes(commandContext -> step(commandContext.getSource(), IntegerArgumentType.getInteger(commandContext, "time")))
                        )
                )
                .then(
                    Commands.literal("sprint")
                        .then(Commands.literal("stop").executes(commandContext -> stopSprinting(commandContext.getSource())))
                        .then(
                            Commands.argument("time", TimeArgument.time(1))
                                .suggests(
                                    (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
                                        new String[]{"60s", "1d", "3d"}, suggestionsBuilder
                                    )
                                )
                                .executes(commandContext -> sprint(commandContext.getSource(), IntegerArgumentType.getInteger(commandContext, "time")))
                        )
                )
                .then(Commands.literal("unfreeze").executes(commandContext -> setFreeze(commandContext.getSource(), false)))
                .then(Commands.literal("freeze").executes(commandContext -> setFreeze(commandContext.getSource(), true)))
        );
    }

    private static String nanosToMilisString(long nanos) {
        return String.format(Locale.ROOT, "%.1f", (float)nanos / (float)TimeUtil.NANOSECONDS_PER_MILLISECOND);
    }

    private static int setTickingRate(CommandSourceStack source, float tickRate) {
        ServerTickRateManager serverTickRateManager = source.getServer().tickRateManager();
        serverTickRateManager.setTickRate(tickRate);
        String string = String.format(Locale.ROOT, "%.1f", tickRate);
        source.sendSuccess(() -> Component.translatable("commands.tick.rate.success", string), true);
        return (int)tickRate;
    }

    private static int tickQuery(CommandSourceStack source) {
        ServerTickRateManager serverTickRateManager = source.getServer().tickRateManager();
        String string = nanosToMilisString(source.getServer().getAverageTickTimeNanos());
        float tickrate = serverTickRateManager.tickrate();
        String string1 = String.format(Locale.ROOT, "%.1f", tickrate);
        if (serverTickRateManager.isSprinting()) {
            source.sendSuccess(() -> Component.translatable("commands.tick.status.sprinting"), false);
            source.sendSuccess(() -> Component.translatable("commands.tick.query.rate.sprinting", string1, string), false);
        } else {
            if (serverTickRateManager.isFrozen()) {
                source.sendSuccess(() -> Component.translatable("commands.tick.status.frozen"), false);
            } else if (serverTickRateManager.nanosecondsPerTick() < source.getServer().getAverageTickTimeNanos()) {
                source.sendSuccess(() -> Component.translatable("commands.tick.status.lagging"), false);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.tick.status.running"), false);
            }

            String string2 = nanosToMilisString(serverTickRateManager.nanosecondsPerTick());
            source.sendSuccess(() -> Component.translatable("commands.tick.query.rate.running", string1, string, string2), false);
        }

        long[] longs = Arrays.copyOf(source.getServer().getTickTimesNanos(), source.getServer().getTickTimesNanos().length);
        Arrays.sort(longs);
        String string3 = nanosToMilisString(longs[longs.length / 2]);
        String string4 = nanosToMilisString(longs[(int)(longs.length * 0.95)]);
        String string5 = nanosToMilisString(longs[(int)(longs.length * 0.99)]);
        source.sendSuccess(() -> Component.translatable("commands.tick.query.percentiles", string3, string4, string5, longs.length), false);
        return (int)tickrate;
    }

    private static int sprint(CommandSourceStack source, int sprintTime) {
        boolean flag = source.getServer().tickRateManager().requestGameToSprint(sprintTime);
        if (flag) {
            source.sendSuccess(() -> Component.translatable("commands.tick.sprint.stop.success"), true);
        }

        source.sendSuccess(() -> Component.translatable("commands.tick.status.sprinting"), true);
        return 1;
    }

    private static int setFreeze(CommandSourceStack source, boolean frozen) {
        ServerTickRateManager serverTickRateManager = source.getServer().tickRateManager();
        if (frozen) {
            if (serverTickRateManager.isSprinting()) {
                serverTickRateManager.stopSprinting();
            }

            if (serverTickRateManager.isSteppingForward()) {
                serverTickRateManager.stopStepping();
            }
        }

        serverTickRateManager.setFrozen(frozen);
        if (frozen) {
            source.sendSuccess(() -> Component.translatable("commands.tick.status.frozen"), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.tick.status.running"), true);
        }

        return frozen ? 1 : 0;
    }

    private static int step(CommandSourceStack source, int ticks) {
        ServerTickRateManager serverTickRateManager = source.getServer().tickRateManager();
        boolean flag = serverTickRateManager.stepGameIfPaused(ticks);
        if (flag) {
            source.sendSuccess(() -> Component.translatable("commands.tick.step.success", ticks), true);
        } else {
            source.sendFailure(Component.translatable("commands.tick.step.fail"));
        }

        return 1;
    }

    private static int stopStepping(CommandSourceStack source) {
        ServerTickRateManager serverTickRateManager = source.getServer().tickRateManager();
        boolean flag = serverTickRateManager.stopStepping();
        if (flag) {
            source.sendSuccess(() -> Component.translatable("commands.tick.step.stop.success"), true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("commands.tick.step.stop.fail"));
            return 0;
        }
    }

    private static int stopSprinting(CommandSourceStack source) {
        ServerTickRateManager serverTickRateManager = source.getServer().tickRateManager();
        boolean flag = serverTickRateManager.stopSprinting();
        if (flag) {
            source.sendSuccess(() -> Component.translatable("commands.tick.sprint.stop.success"), true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("commands.tick.sprint.stop.fail"));
            return 0;
        }
    }
}
