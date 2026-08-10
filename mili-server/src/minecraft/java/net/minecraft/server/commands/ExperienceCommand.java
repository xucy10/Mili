package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class ExperienceCommand {
    private static final SimpleCommandExceptionType ERROR_SET_POINTS_INVALID = new SimpleCommandExceptionType(
        Component.translatable("commands.experience.set.points.invalid")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> literalCommandNode = dispatcher.register(
            Commands.literal("experience")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("target", EntityArgument.players())
                                .then(
                                    Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(
                                            context -> addExperience(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "target"),
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                ExperienceCommand.Type.POINTS
                                            )
                                        )
                                        .then(
                                            Commands.literal("points")
                                                .executes(
                                                    context -> addExperience(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "target"),
                                                        IntegerArgumentType.getInteger(context, "amount"),
                                                        ExperienceCommand.Type.POINTS
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("levels")
                                                .executes(
                                                    context -> addExperience(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "target"),
                                                        IntegerArgumentType.getInteger(context, "amount"),
                                                        ExperienceCommand.Type.LEVELS
                                                    )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("target", EntityArgument.players())
                                .then(
                                    Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(
                                            context -> setExperience(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "target"),
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                ExperienceCommand.Type.POINTS
                                            )
                                        )
                                        .then(
                                            Commands.literal("points")
                                                .executes(
                                                    context -> setExperience(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "target"),
                                                        IntegerArgumentType.getInteger(context, "amount"),
                                                        ExperienceCommand.Type.POINTS
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("levels")
                                                .executes(
                                                    context -> setExperience(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "target"),
                                                        IntegerArgumentType.getInteger(context, "amount"),
                                                        ExperienceCommand.Type.LEVELS
                                                    )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("query")
                        .then(
                            Commands.argument("target", EntityArgument.player())
                                .then(
                                    Commands.literal("points")
                                        .executes(
                                            context -> queryExperience(
                                                context.getSource(), EntityArgument.getPlayer(context, "target"), ExperienceCommand.Type.POINTS
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("levels")
                                        .executes(
                                            context -> queryExperience(
                                                context.getSource(), EntityArgument.getPlayer(context, "target"), ExperienceCommand.Type.LEVELS
                                            )
                                        )
                                )
                        )
                )
        );
        dispatcher.register(Commands.literal("xp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).redirect(literalCommandNode));
    }

    private static int queryExperience(CommandSourceStack source, ServerPlayer player, ExperienceCommand.Type type) {
        player.getBukkitEntity().taskScheduler.schedule((ServerPlayer serverPlayer) -> { // Folia - region threading
        int i = type.query.applyAsInt(serverPlayer); // Folia - region threading
        source.sendSuccess(() -> Component.translatable("commands.experience.query." + type.name, serverPlayer.getDisplayName(), i), false); // Folia - region threading
        }, null, 1L); // Folia - region threading
        return 0; // Folia - region threading
    }

    private static int addExperience(CommandSourceStack source, Collection<? extends ServerPlayer> targets, int amount, ExperienceCommand.Type type) {
        for (ServerPlayer serverPlayer : targets) {
            serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> { // Folia - region threading
                type.add.accept(player, amount);
            }, null, 1L); // Folia - region threading
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable("commands.experience.add." + type.name + ".success.single", amount, targets.iterator().next().getDisplayName()),
                true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.experience.add." + type.name + ".success.multiple", amount, targets.size()), true);
        }

        return targets.size();
    }

    private static int setExperience(CommandSourceStack source, Collection<? extends ServerPlayer> targets, int amount, ExperienceCommand.Type type) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverPlayer : targets) {
            i++; serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> { // Folia - region threading
            if (type.set.test(player, amount)) { // Folia - region threading
                //i++; // Folia - region threading
            }
            }, null, 1L); // Folia - region threading
        }

        if (i == 0) {
            throw ERROR_SET_POINTS_INVALID.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.experience.set." + type.name + ".success.single", amount, targets.iterator().next().getDisplayName()),
                    true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.experience.set." + type.name + ".success.multiple", amount, targets.size()), true);
            }

            return targets.size();
        }
    }

    static enum Type {
        POINTS("points", Player::giveExperiencePoints, (player, experience) -> {
            if (experience >= player.getXpNeededForNextLevel()) {
                return false;
            } else {
                player.setExperiencePoints(experience);
                return true;
            }
        }, player -> Mth.floor(player.experienceProgress * player.getXpNeededForNextLevel())),
        LEVELS("levels", ServerPlayer::giveExperienceLevels, (player, experience) -> {
            player.setExperienceLevels(experience);
            return true;
        }, player -> player.experienceLevel);

        public final BiConsumer<ServerPlayer, Integer> add;
        public final BiPredicate<ServerPlayer, Integer> set;
        public final String name;
        final ToIntFunction<ServerPlayer> query;

        private Type(
            final String name,
            final BiConsumer<ServerPlayer, Integer> add,
            final BiPredicate<ServerPlayer, Integer> set,
            final ToIntFunction<ServerPlayer> query
        ) {
            this.add = add;
            this.name = name;
            this.set = set;
            this.query = query;
        }
    }
}
