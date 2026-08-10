package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public class GiveCommand {
    public static final int MAX_ALLOWED_ITEMSTACKS = 100;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("give")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .then(
                            Commands.argument("item", ItemArgument.item(context))
                                .executes(
                                    commandContext -> giveItem(
                                        commandContext.getSource(),
                                        ItemArgument.getItem(commandContext, "item"),
                                        EntityArgument.getPlayers(commandContext, "targets"),
                                        1
                                    )
                                )
                                .then(
                                    Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(
                                            context1 -> giveItem(
                                                context1.getSource(),
                                                ItemArgument.getItem(context1, "item"),
                                                EntityArgument.getPlayers(context1, "targets"),
                                                IntegerArgumentType.getInteger(context1, "count")
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int giveItem(CommandSourceStack source, ItemInput item, Collection<ServerPlayer> targets, int count) throws CommandSyntaxException {
        ItemStack itemStack = item.createItemStack(1, false);
        int maxStackSize = org.leavesmc.leaves.util.ItemOverstackUtils.getItemStackMaxCount(itemStack); // Mili - item over-stack util
        int i = maxStackSize * 100;
        if (count > i) {
            source.sendFailure(Component.translatable("commands.give.failed.toomanyitems", i, itemStack.getDisplayName()));
            return 0;
        } else {
            for (ServerPlayer serverPlayer : targets) {
                int i1 = count;

                while (i1 > 0) {
                    int min = Math.min(maxStackSize, i1);
                    i1 -= min;
                    ItemStack itemStack1 = item.createItemStack(min, false);
                    serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer nmsEntity) -> { // Folia - region threading
                    boolean flag = nmsEntity.getInventory().add(itemStack1); // Folia - region threading
                    if (flag && itemStack1.isEmpty()) {
                        ItemEntity itemEntity = nmsEntity.drop(itemStack, false, false, false, null); // Paper - do not fire PlayerDropItemEvent for /give command // Folia - region threading
                        if (itemEntity != null) {
                            itemEntity.makeFakeItem();
                        }

                        nmsEntity.level() // Folia - region threading
                            .playSound(
                                null,
                                nmsEntity.getX(), // Folia - region threading
                                nmsEntity.getY(), // Folia - region threading
                                nmsEntity.getZ(), // Folia - region threading
                                SoundEvents.ITEM_PICKUP,
                                SoundSource.PLAYERS,
                                0.2F,
                                ((nmsEntity.getRandom().nextFloat() - nmsEntity.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F // Folia - region threading
                            );
                        nmsEntity.containerMenu.broadcastChanges(); // Folia - region threading
                    } else {
                        ItemEntity itemEntity = nmsEntity.drop(itemStack1, false, false, false, null); // Paper - do not fire PlayerDropItemEvent for /give command // Folia - region threading
                        if (itemEntity != null) {
                            itemEntity.setNoPickUpDelay();
                            itemEntity.setTarget(nmsEntity.getUUID()); // Folia - region threading
                        }
                    }
                    }, null, 1L); // Folia - region threading
                }
            }

            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.give.success.single", count, itemStack.getDisplayName(), targets.iterator().next().getDisplayName()),
                    true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.give.success.multiple", count, itemStack.getDisplayName(), targets.size()), true); // Paper - MC-151857 - correct translation key
            }

            return targets.size();
        }
    }
}
