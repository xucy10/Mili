package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ClearInventoryCommands {
    private static final DynamicCommandExceptionType ERROR_SINGLE = new DynamicCommandExceptionType(
        player -> Component.translatableEscape("clear.failed.single", player)
    );
    private static final DynamicCommandExceptionType ERROR_MULTIPLE = new DynamicCommandExceptionType(
        playerCount -> Component.translatableEscape("clear.failed.multiple", playerCount)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("clear")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(
                    commandContext -> clearUnlimited(
                        commandContext.getSource(), Collections.singleton(commandContext.getSource().getPlayerOrException()), itemStack -> true
                    )
                )
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .executes(
                            commandContext -> clearUnlimited(
                                commandContext.getSource(), EntityArgument.getPlayers(commandContext, "targets"), itemStack -> true
                            )
                        )
                        .then(
                            Commands.argument("item", ItemPredicateArgument.itemPredicate(context))
                                .executes(
                                    context1 -> clearUnlimited(
                                        context1.getSource(),
                                        EntityArgument.getPlayers(context1, "targets"),
                                        ItemPredicateArgument.getItemPredicate(context1, "item")
                                    )
                                )
                                .then(
                                    Commands.argument("maxCount", IntegerArgumentType.integer(0))
                                        .executes(
                                            context1 -> clearInventory(
                                                context1.getSource(),
                                                EntityArgument.getPlayers(context1, "targets"),
                                                ItemPredicateArgument.getItemPredicate(context1, "item"),
                                                IntegerArgumentType.getInteger(context1, "maxCount")
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int clearUnlimited(CommandSourceStack source, Collection<ServerPlayer> targets, Predicate<ItemStack> filter) throws CommandSyntaxException {
        return clearInventory(source, targets, filter, -1);
    }

    private static int clearInventory(CommandSourceStack source, Collection<ServerPlayer> targetPlayers, Predicate<ItemStack> itemPredicate, int maxCount) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverPlayer : targetPlayers) {
            // Folia start - region threading
            ++i;
            serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> {
                player.getInventory().clearOrCountMatchingItems(itemPredicate, maxCount, player.inventoryMenu.getCraftSlots());
                player.containerMenu.broadcastChanges();
                player.inventoryMenu.slotsChanged(player.getInventory());
            }, null, 1L);
            // Folia end - region threading
        }

        if (i == 0) {
            if (targetPlayers.size() == 1) {
                throw ERROR_SINGLE.create(targetPlayers.iterator().next().getName());
            } else {
                throw ERROR_MULTIPLE.create(targetPlayers.size());
            }
        } else {
            int i1 = i;
            if (maxCount == 0) {
                if (targetPlayers.size() == 1) {
                    source.sendSuccess(() -> Component.translatable("commands.clear.test.single", i1, targetPlayers.iterator().next().getDisplayName()), true);
                } else {
                    source.sendSuccess(() -> Component.translatable("commands.clear.test.multiple", i1, targetPlayers.size()), true);
                }
            } else if (targetPlayers.size() == 1) {
                source.sendSuccess(() -> Component.translatable("commands.clear.success.single", i1, targetPlayers.iterator().next().getDisplayName()), true);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.clear.success.multiple", i1, targetPlayers.size()), true);
            }

            return i;
        }
    }
}
