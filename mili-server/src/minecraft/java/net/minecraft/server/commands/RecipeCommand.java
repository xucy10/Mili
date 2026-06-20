package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeCommand {
    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.recipe.give.failed"));
    private static final SimpleCommandExceptionType ERROR_TAKE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.recipe.take.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("recipe")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("give")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.argument("recipe", ResourceKeyArgument.key(Registries.RECIPE))
                                        .executes(
                                            commandContext -> giveRecipes(
                                                commandContext.getSource(),
                                                EntityArgument.getPlayers(commandContext, "targets"),
                                                Collections.singleton(ResourceKeyArgument.getRecipe(commandContext, "recipe"))
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("*")
                                        .executes(
                                            context -> giveRecipes(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "targets"),
                                                context.getSource().getServer().getRecipeManager().getRecipes()
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("take")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.argument("recipe", ResourceKeyArgument.key(Registries.RECIPE))
                                        .executes(
                                            context -> takeRecipes(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "targets"),
                                                Collections.singleton(ResourceKeyArgument.getRecipe(context, "recipe"))
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("*")
                                        .executes(
                                            context -> takeRecipes(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "targets"),
                                                context.getSource().getServer().getRecipeManager().getRecipes()
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int giveRecipes(CommandSourceStack source, Collection<ServerPlayer> targets, Collection<RecipeHolder<?>> recipes) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverPlayer : targets) {
            // Folia start - region threading
            ++i;
            serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> {
                player.awardRecipes(recipes);
            }, null, 1L);
            // Folia end - region threading
        }

        if (i == 0) {
            throw ERROR_GIVE_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.recipe.give.success.single", recipes.size(), targets.iterator().next().getDisplayName()), true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.recipe.give.success.multiple", recipes.size(), targets.size()), true);
            }

            return i;
        }
    }

    private static int takeRecipes(CommandSourceStack source, Collection<ServerPlayer> targets, Collection<RecipeHolder<?>> recipes) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverPlayer : targets) {
            // Folia start - region threading
            ++i;
            serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> {
                player.resetRecipes(recipes);
            }, null, 1L);
            // Folia end - region threading
        }

        if (i == 0) {
            throw ERROR_TAKE_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.recipe.take.success.single", recipes.size(), targets.iterator().next().getDisplayName()), true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.recipe.take.success.multiple", recipes.size(), targets.size()), true);
            }

            return i;
        }
    }
}
