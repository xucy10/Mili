package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec2;

public class WorldBorderCommand {
    private static final SimpleCommandExceptionType ERROR_SAME_CENTER = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.center.failed")
    );
    private static final SimpleCommandExceptionType ERROR_SAME_SIZE = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.set.failed.nochange")
    );
    private static final SimpleCommandExceptionType ERROR_TOO_SMALL = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.set.failed.small")
    );
    private static final SimpleCommandExceptionType ERROR_TOO_BIG = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.set.failed.big", 5.999997E7F)
    );
    private static final SimpleCommandExceptionType ERROR_TOO_FAR_OUT = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.set.failed.far", 2.9999984E7)
    );
    private static final SimpleCommandExceptionType ERROR_SAME_WARNING_TIME = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.warning.time.failed")
    );
    private static final SimpleCommandExceptionType ERROR_SAME_WARNING_DISTANCE = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.warning.distance.failed")
    );
    private static final SimpleCommandExceptionType ERROR_SAME_DAMAGE_BUFFER = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.damage.buffer.failed")
    );
    private static final SimpleCommandExceptionType ERROR_SAME_DAMAGE_AMOUNT = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.damage.amount.failed")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("worldborder")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("distance", DoubleArgumentType.doubleArg(-5.999997E7F, 5.999997E7F))
                                .executes(
                                    context -> setSize(
                                        context.getSource(),
                                        context.getSource().getLevel().getWorldBorder().getSize() + DoubleArgumentType.getDouble(context, "distance"),
                                        0L
                                    )
                                )
                                .then(
                                    Commands.argument("time", TimeArgument.time(0))
                                        .executes(
                                            context -> setSize(
                                                context.getSource(),
                                                context.getSource().getLevel().getWorldBorder().getSize() + DoubleArgumentType.getDouble(context, "distance"),
                                                context.getSource().getLevel().getWorldBorder().getLerpTime() + IntegerArgumentType.getInteger(context, "time")
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("distance", DoubleArgumentType.doubleArg(-5.999997E7F, 5.999997E7F))
                                .executes(context -> setSize(context.getSource(), DoubleArgumentType.getDouble(context, "distance"), 0L))
                                .then(
                                    Commands.argument("time", TimeArgument.time(0))
                                        .executes(
                                            context -> setSize(
                                                context.getSource(),
                                                DoubleArgumentType.getDouble(context, "distance"),
                                                IntegerArgumentType.getInteger(context, "time")
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("center")
                        .then(
                            Commands.argument("pos", Vec2Argument.vec2())
                                .executes(context -> setCenter(context.getSource(), Vec2Argument.getVec2(context, "pos")))
                        )
                )
                .then(
                    Commands.literal("damage")
                        .then(
                            Commands.literal("amount")
                                .then(
                                    Commands.argument("damagePerBlock", FloatArgumentType.floatArg(0.0F))
                                        .executes(context -> setDamageAmount(context.getSource(), FloatArgumentType.getFloat(context, "damagePerBlock")))
                                )
                        )
                        .then(
                            Commands.literal("buffer")
                                .then(
                                    Commands.argument("distance", FloatArgumentType.floatArg(0.0F))
                                        .executes(context -> setDamageBuffer(context.getSource(), FloatArgumentType.getFloat(context, "distance")))
                                )
                        )
                )
                .then(Commands.literal("get").executes(context -> getSize(context.getSource())))
                .then(
                    Commands.literal("warning")
                        .then(
                            Commands.literal("distance")
                                .then(
                                    Commands.argument("distance", IntegerArgumentType.integer(0))
                                        .executes(context -> setWarningDistance(context.getSource(), IntegerArgumentType.getInteger(context, "distance")))
                                )
                        )
                        .then(
                            Commands.literal("time")
                                .then(
                                    Commands.argument("time", TimeArgument.time(0))
                                        .executes(context -> setWarningTime(context.getSource(), IntegerArgumentType.getInteger(context, "time")))
                                )
                        )
                )
        );
    }

    // Folia start - region threading
    private static void sendMessage(CommandSourceStack src, CommandSyntaxException ex) {
        src.sendFailure((Component)ex.getRawMessage());
    }
    // Folia end - region threading

    private static int setDamageBuffer(CommandSourceStack source, float distance) throws CommandSyntaxException {
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            try {
                // Folia end - region threading
        WorldBorder worldBorder = source.getLevel().getWorldBorder();
        if (worldBorder.getSafeZone() == distance) {
            throw ERROR_SAME_DAMAGE_BUFFER.create();
        } else {
            worldBorder.setSafeZone(distance);
            source.sendSuccess(() -> Component.translatable("commands.worldborder.damage.buffer.success", String.format(Locale.ROOT, "%.2f", distance)), true);
            return; // Folia - region threading
        }
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        });
        return 1;
        // Folia end - region threading
    }

    private static int setDamageAmount(CommandSourceStack source, float damagePerBlock) throws CommandSyntaxException {
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            try {
                // Folia end - region threading
        WorldBorder worldBorder = source.getLevel().getWorldBorder();
        if (worldBorder.getDamagePerBlock() == damagePerBlock) {
            throw ERROR_SAME_DAMAGE_AMOUNT.create();
        } else {
            worldBorder.setDamagePerBlock(damagePerBlock);
            source.sendSuccess(
                () -> Component.translatable("commands.worldborder.damage.amount.success", String.format(Locale.ROOT, "%.2f", damagePerBlock)), true
            );
            return; // Folia - region threading
        }
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        });
        return 1;
        // Folia end - region threading
    }

    private static int setWarningTime(CommandSourceStack source, int time) throws CommandSyntaxException {
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            try {
                // Folia end - region threading
        WorldBorder worldBorder = source.getLevel().getWorldBorder();
        if (worldBorder.getWarningTime() == time) {
            throw ERROR_SAME_WARNING_TIME.create();
        } else {
            worldBorder.setWarningTime(time);
            source.sendSuccess(() -> Component.translatable("commands.worldborder.warning.time.success", formatTicksToSeconds(time)), true);
            return; // Folia - region threading
        }
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        });
        return 1;
        // Folia end - region threading
    }

    private static int setWarningDistance(CommandSourceStack source, int distance) throws CommandSyntaxException {
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            try {
                // Folia end - region threading
        WorldBorder worldBorder = source.getLevel().getWorldBorder();
        if (worldBorder.getWarningBlocks() == distance) {
            throw ERROR_SAME_WARNING_DISTANCE.create();
        } else {
            worldBorder.setWarningBlocks(distance);
            source.sendSuccess(() -> Component.translatable("commands.worldborder.warning.distance.success", distance), true);
            return; // Folia - region threading
        }
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        });
        return 1;
        // Folia end - region threading
    }

    private static int getSize(CommandSourceStack source) {
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            // Folia end - region threading
        double size = source.getLevel().getWorldBorder().getSize();
        source.sendSuccess(() -> Component.translatable("commands.worldborder.get", String.format(Locale.ROOT, "%.0f", size)), false);
        return; // Folia - region threading
        // Folia start - region threading
        });
        return 1;
        // Folia end - region threading
    }

    private static int setCenter(CommandSourceStack source, Vec2 pos) throws CommandSyntaxException {
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            try {
                // Folia end - region threading
        WorldBorder worldBorder = source.getLevel().getWorldBorder();
        if (worldBorder.getCenterX() == pos.x && worldBorder.getCenterZ() == pos.y) {
            throw ERROR_SAME_CENTER.create();
        } else if (!(Math.abs(pos.x) > 2.9999984E7) && !(Math.abs(pos.y) > 2.9999984E7)) {
            worldBorder.setCenter(pos.x, pos.y);
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.worldborder.center.success", String.format(Locale.ROOT, "%.2f", pos.x), String.format(Locale.ROOT, "%.2f", pos.y)
                ),
                true
            );
            return; // Folia - region threading
        } else {
            throw ERROR_TOO_FAR_OUT.create();
        }
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        });
        return 1;
        // Folia end - region threading
    }

    private static int setSize(CommandSourceStack source, double newSize, long time) throws CommandSyntaxException {
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            try {
                // Folia end - region threading
        ServerLevel level = source.getLevel();
        WorldBorder worldBorder = level.getWorldBorder();
        double size = worldBorder.getSize();
        if (size == newSize) {
            throw ERROR_SAME_SIZE.create();
        } else if (newSize < 1.0) {
            throw ERROR_TOO_SMALL.create();
        } else if (newSize > 5.999997E7F) {
            throw ERROR_TOO_BIG.create();
        } else {
            String string = String.format(Locale.ROOT, "%.1f", newSize);
            if (time > 0L) {
                worldBorder.lerpSizeBetween(size, newSize, time, level.getGameTime());
                if (newSize > size) {
                    source.sendSuccess(() -> Component.translatable("commands.worldborder.set.grow", string, formatTicksToSeconds(time)), true);
                } else {
                    source.sendSuccess(() -> Component.translatable("commands.worldborder.set.shrink", string, formatTicksToSeconds(time)), true);
                }
            } else {
                worldBorder.setSize(newSize);
                source.sendSuccess(() -> Component.translatable("commands.worldborder.set.immediate", string), true);
            }

            return; // Folia - region threading
        }
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        });
        return 1;
        // Folia end - region threading
    }

    private static String formatTicksToSeconds(long time) {
        return String.format(Locale.ROOT, "%.2f", time / 20.0);
    }
}
