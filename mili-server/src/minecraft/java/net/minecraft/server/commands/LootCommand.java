package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.SlotArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public class LootCommand {
    private static final DynamicCommandExceptionType ERROR_NO_HELD_ITEMS = new DynamicCommandExceptionType(
        object -> Component.translatableEscape("commands.drop.no_held_items", object)
    );
    private static final DynamicCommandExceptionType ERROR_NO_ENTITY_LOOT_TABLE = new DynamicCommandExceptionType(
        target -> Component.translatableEscape("commands.drop.no_loot_table.entity", target)
    );
    private static final DynamicCommandExceptionType ERROR_NO_BLOCK_LOOT_TABLE = new DynamicCommandExceptionType(
        target -> Component.translatableEscape("commands.drop.no_loot_table.block", target)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            addTargets(
                Commands.literal("loot").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)),
                (builder, dropConsumer) -> builder.then(
                        Commands.literal("fish")
                            .then(
                                Commands.argument("loot_table", ResourceOrIdArgument.lootTable(context))
                                    .then(
                                        Commands.argument("pos", BlockPosArgument.blockPos())
                                            .executes(
                                                commandContext -> dropFishingLoot(
                                                    commandContext,
                                                    ResourceOrIdArgument.getLootTable(commandContext, "loot_table"),
                                                    BlockPosArgument.getLoadedBlockPos(commandContext, "pos"),
                                                    ItemStack.EMPTY,
                                                    dropConsumer
                                                )
                                            )
                                            .then(
                                                Commands.argument("tool", ItemArgument.item(context))
                                                    .executes(
                                                        commandContext -> dropFishingLoot(
                                                            commandContext,
                                                            ResourceOrIdArgument.getLootTable(commandContext, "loot_table"),
                                                            BlockPosArgument.getLoadedBlockPos(commandContext, "pos"),
                                                            ItemArgument.getItem(commandContext, "tool").createItemStack(1, false),
                                                            dropConsumer
                                                        )
                                                    )
                                            )
                                            .then(
                                                Commands.literal("mainhand")
                                                    .executes(
                                                        context1 -> dropFishingLoot(
                                                            context1,
                                                            ResourceOrIdArgument.getLootTable(context1, "loot_table"),
                                                            BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                            getSourceHandItem(context1.getSource(), EquipmentSlot.MAINHAND),
                                                            dropConsumer
                                                        )
                                                    )
                                            )
                                            .then(
                                                Commands.literal("offhand")
                                                    .executes(
                                                        context1 -> dropFishingLoot(
                                                            context1,
                                                            ResourceOrIdArgument.getLootTable(context1, "loot_table"),
                                                            BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                            getSourceHandItem(context1.getSource(), EquipmentSlot.OFFHAND),
                                                            dropConsumer
                                                        )
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        Commands.literal("loot")
                            .then(
                                Commands.argument("loot_table", ResourceOrIdArgument.lootTable(context))
                                    .executes(context1 -> dropChestLoot(context1, ResourceOrIdArgument.getLootTable(context1, "loot_table"), dropConsumer))
                            )
                    )
                    .then(
                        Commands.literal("kill")
                            .then(
                                Commands.argument("target", EntityArgument.entity())
                                    .executes(context1 -> dropKillLoot(context1, EntityArgument.getEntity(context1, "target"), dropConsumer))
                            )
                    )
                    .then(
                        Commands.literal("mine")
                            .then(
                                Commands.argument("pos", BlockPosArgument.blockPos())
                                    .executes(
                                        context1 -> dropBlockLoot(context1, BlockPosArgument.getLoadedBlockPos(context1, "pos"), ItemStack.EMPTY, dropConsumer)
                                    )
                                    .then(
                                        Commands.argument("tool", ItemArgument.item(context))
                                            .executes(
                                                context1 -> dropBlockLoot(
                                                    context1,
                                                    BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                    ItemArgument.getItem(context1, "tool").createItemStack(1, false),
                                                    dropConsumer
                                                )
                                            )
                                    )
                                    .then(
                                        Commands.literal("mainhand")
                                            .executes(
                                                context1 -> dropBlockLoot(
                                                    context1,
                                                    BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                    getSourceHandItem(context1.getSource(), EquipmentSlot.MAINHAND),
                                                    dropConsumer
                                                )
                                            )
                                    )
                                    .then(
                                        Commands.literal("offhand")
                                            .executes(
                                                context1 -> dropBlockLoot(
                                                    context1,
                                                    BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                    getSourceHandItem(context1.getSource(), EquipmentSlot.OFFHAND),
                                                    dropConsumer
                                                )
                                            )
                                    )
                            )
                    )
            )
        );
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T addTargets(T builder, LootCommand.TailProvider tailProvider) {
        return builder.then(
                Commands.literal("replace")
                    .then(
                        Commands.literal("entity")
                            .then(
                                Commands.argument("entities", EntityArgument.entities())
                                    .then(
                                        tailProvider.construct(
                                                Commands.argument("slot", SlotArgument.slot()),
                                                (context, items, callback) -> entityReplace(
                                                    EntityArgument.getEntities(context, "entities"),
                                                    SlotArgument.getSlot(context, "slot"),
                                                    items.size(),
                                                    items,
                                                    callback
                                                )
                                            )
                                            .then(
                                                tailProvider.construct(
                                                    Commands.argument("count", IntegerArgumentType.integer(0)),
                                                    (context, items, callback) -> entityReplace(
                                                        EntityArgument.getEntities(context, "entities"),
                                                        SlotArgument.getSlot(context, "slot"),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        items,
                                                        callback
                                                    )
                                                )
                                            )
                                    )
                            )
                    )
                    .then(
                        Commands.literal("block")
                            .then(
                                Commands.argument("targetPos", BlockPosArgument.blockPos())
                                    .then(
                                        tailProvider.construct(
                                                Commands.argument("slot", SlotArgument.slot()),
                                                (context, items, callback) -> blockReplace(
                                                    context.getSource(),
                                                    BlockPosArgument.getLoadedBlockPos(context, "targetPos"),
                                                    SlotArgument.getSlot(context, "slot"),
                                                    items.size(),
                                                    items,
                                                    callback
                                                )
                                            )
                                            .then(
                                                tailProvider.construct(
                                                    Commands.argument("count", IntegerArgumentType.integer(0)),
                                                    (context, items, callback) -> blockReplace(
                                                        context.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "targetPos"),
                                                        IntegerArgumentType.getInteger(context, "slot"),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        items,
                                                        callback
                                                    )
                                                )
                                            )
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("insert")
                    .then(
                        tailProvider.construct(
                            Commands.argument("targetPos", BlockPosArgument.blockPos()),
                            (context, items, callback) -> blockDistribute(
                                context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "targetPos"), items, callback
                            )
                        )
                    )
            )
            .then(
                Commands.literal("give")
                    .then(
                        tailProvider.construct(
                            Commands.argument("players", EntityArgument.players()),
                            (context, items, callback) -> playerGive(EntityArgument.getPlayers(context, "players"), items, callback)
                        )
                    )
            )
            .then(
                Commands.literal("spawn")
                    .then(
                        tailProvider.construct(
                            Commands.argument("targetPos", Vec3Argument.vec3()),
                            (context, items, callback) -> dropInWorld(context.getSource(), Vec3Argument.getVec3(context, "targetPos"), items, callback)
                        )
                    )
            );
    }

    private static Container getContainer(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        BlockEntity blockEntity = source.getLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof Container)) {
            throw ItemCommands.ERROR_TARGET_NOT_A_CONTAINER.create(pos.getX(), pos.getY(), pos.getZ());
        } else {
            return (Container)blockEntity;
        }
    }

    private static int blockDistribute(CommandSourceStack source, BlockPos pos, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        Container container = getContainer(source, pos);
        List<ItemStack> list = Lists.newArrayListWithCapacity(items.size());

        for (ItemStack itemStack : items) {
            if (distributeToContainer(container, itemStack.copy())) {
                container.setChanged();
                list.add(itemStack);
            }
        }

        callback.accept(list);
        return list.size();
    }

    private static boolean distributeToContainer(Container container, ItemStack item) {
        boolean flag = false;

        for (int i = 0; i < container.getContainerSize() && !item.isEmpty(); i++) {
            ItemStack item1 = container.getItem(i);
            if (container.canPlaceItem(i, item)) {
                if (item1.isEmpty()) {
                    container.setItem(i, item);
                    flag = true;
                    break;
                }

                if (canMergeItems(item1, item)) {
                    int i1 = item.getMaxStackSize() - item1.getCount();
                    int min = Math.min(item.getCount(), i1);
                    item.shrink(min);
                    item1.grow(min);
                    flag = true;
                }
            }
        }

        return flag;
    }

    private static int blockReplace(CommandSourceStack source, BlockPos pos, int slot, int numSlots, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        Container container = getContainer(source, pos);
        int containerSize = container.getContainerSize();
        if (slot >= 0 && slot < containerSize) {
            List<ItemStack> list = Lists.newArrayListWithCapacity(items.size());

            for (int i = 0; i < numSlots; i++) {
                int i1 = slot + i;
                ItemStack itemStack = i < items.size() ? items.get(i) : ItemStack.EMPTY;
                if (container.canPlaceItem(i1, itemStack)) {
                    container.setItem(i1, itemStack);
                    list.add(itemStack);
                }
            }

            callback.accept(list);
            return list.size();
        } else {
            throw ItemCommands.ERROR_TARGET_INAPPLICABLE_SLOT.create(slot);
        }
    }

    private static boolean canMergeItems(ItemStack first, ItemStack second) {
        return first.getCount() <= first.getMaxStackSize() && ItemStack.isSameItemSameComponents(first, second);
    }

    private static int playerGive(Collection<ServerPlayer> targets, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        List<ItemStack> list = Lists.newArrayListWithCapacity(items.size());

        for (ItemStack itemStack : items) {
            for (ServerPlayer serverPlayer : targets) {
                if (serverPlayer.getInventory().add(itemStack.copy())) {
                    list.add(itemStack);
                }
            }
        }

        callback.accept(list);
        return list.size();
    }

    private static void setSlots(Entity target, List<ItemStack> items, int startSlot, int numSlots, List<ItemStack> setItems) {
        for (int i = 0; i < numSlots; i++) {
            ItemStack itemStack = i < items.size() ? items.get(i) : ItemStack.EMPTY;
            SlotAccess slot = target.getSlot(startSlot + i);
            if (slot != null && slot.set(itemStack.copy())) {
                setItems.add(itemStack);
            }
        }
    }

    private static int entityReplace(Collection<? extends Entity> targets, int startSlot, int numSlots, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        List<ItemStack> list = Lists.newArrayListWithCapacity(items.size());

        for (Entity entity : targets) {
            if (entity instanceof ServerPlayer serverPlayer) {
                setSlots(entity, items, startSlot, numSlots, list);
                serverPlayer.containerMenu.broadcastChanges();
            } else {
                setSlots(entity, items, startSlot, numSlots, list);
            }
        }

        callback.accept(list);
        return list.size();
    }

    private static int dropInWorld(CommandSourceStack source, Vec3 pos, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        items.forEach(itemStack -> {
            ItemEntity itemEntity = new ItemEntity(level, pos.x, pos.y, pos.z, itemStack.copy());
            itemEntity.setDefaultPickUpDelay();
            level.addFreshEntity(itemEntity);
        });
        callback.accept(items);
        return items.size();
    }

    private static void callback(CommandSourceStack source, List<ItemStack> items) {
        if (items.size() == 1) {
            ItemStack itemStack = items.get(0);
            source.sendSuccess(() -> Component.translatable("commands.drop.success.single", itemStack.getCount(), itemStack.getDisplayName()), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.drop.success.multiple", items.size()), false);
        }
    }

    private static void callback(CommandSourceStack source, List<ItemStack> items, ResourceKey<LootTable> lootTable) {
        if (items.size() == 1) {
            ItemStack itemStack = items.get(0);
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.drop.success.single_with_table",
                    itemStack.getCount(),
                    itemStack.getDisplayName(),
                    Component.translationArg(lootTable.identifier())
                ),
                false
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.drop.success.multiple_with_table", items.size(), Component.translationArg(lootTable.identifier())),
                false
            );
        }
    }

    private static ItemStack getSourceHandItem(CommandSourceStack source, EquipmentSlot slot) throws CommandSyntaxException {
        Entity entityOrException = source.getEntityOrException();
        if (entityOrException instanceof LivingEntity) {
            return ((LivingEntity)entityOrException).getItemBySlot(slot);
        } else {
            throw ERROR_NO_HELD_ITEMS.create(entityOrException.getDisplayName());
        }
    }

    private static int dropBlockLoot(CommandContext<CommandSourceStack> context, BlockPos pos, ItemStack tool, LootCommand.DropConsumer dropConsumer) throws CommandSyntaxException {
        CommandSourceStack commandSourceStack = context.getSource();
        ServerLevel level = commandSourceStack.getLevel();
        BlockState blockState = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        Optional<ResourceKey<LootTable>> lootTable = blockState.getBlock().getLootTable();
        if (lootTable.isEmpty()) {
            throw ERROR_NO_BLOCK_LOOT_TABLE.create(blockState.getBlock().getName());
        } else {
            LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.BLOCK_STATE, blockState)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, commandSourceStack.getEntity())
                .withParameter(LootContextParams.TOOL, tool);
            List<ItemStack> drops = blockState.getDrops(builder);
            return dropConsumer.accept(context, drops, items -> callback(commandSourceStack, items, lootTable.get()));
        }
    }

    private static int dropKillLoot(CommandContext<CommandSourceStack> context, Entity entity, LootCommand.DropConsumer dropConsumer) throws CommandSyntaxException {
        Optional<ResourceKey<LootTable>> lootTable = entity.getLootTable();
        if (lootTable.isEmpty()) {
            throw ERROR_NO_ENTITY_LOOT_TABLE.create(entity.getDisplayName());
        } else {
            CommandSourceStack commandSourceStack = context.getSource();
            LootParams.Builder builder = new LootParams.Builder(commandSourceStack.getLevel());
            Entity entity1 = commandSourceStack.getEntity();
            if (entity1 instanceof Player player) {
                builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
            }

            builder.withParameter(LootContextParams.DAMAGE_SOURCE, entity.damageSources().magic());
            builder.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, entity1);
            builder.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, entity1);
            builder.withParameter(LootContextParams.THIS_ENTITY, entity);
            builder.withParameter(LootContextParams.ORIGIN, commandSourceStack.getPosition());
            LootParams lootParams = builder.create(LootContextParamSets.ENTITY);
            LootTable lootTable1 = commandSourceStack.getServer().reloadableRegistries().getLootTable(lootTable.get());
            List<ItemStack> randomItems = lootTable1.getRandomItems(lootParams);
            return dropConsumer.accept(context, randomItems, items -> callback(commandSourceStack, items, lootTable.get()));
        }
    }

    private static int dropChestLoot(CommandContext<CommandSourceStack> context, Holder<LootTable> lootTable, LootCommand.DropConsumer dropConsumer) throws CommandSyntaxException {
        CommandSourceStack commandSourceStack = context.getSource();
        LootParams lootParams = new LootParams.Builder(commandSourceStack.getLevel())
            .withOptionalParameter(LootContextParams.THIS_ENTITY, commandSourceStack.getEntity())
            .withParameter(LootContextParams.ORIGIN, commandSourceStack.getPosition())
            .create(LootContextParamSets.CHEST);
        return drop(context, lootTable, lootParams, dropConsumer);
    }

    private static int dropFishingLoot(
        CommandContext<CommandSourceStack> context, Holder<LootTable> lootTable, BlockPos pos, ItemStack tool, LootCommand.DropConsumer dropConsumer
    ) throws CommandSyntaxException {
        CommandSourceStack commandSourceStack = context.getSource();
        LootParams lootParams = new LootParams.Builder(commandSourceStack.getLevel())
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.TOOL, tool)
            .withOptionalParameter(LootContextParams.THIS_ENTITY, commandSourceStack.getEntity())
            .create(LootContextParamSets.FISHING);
        return drop(context, lootTable, lootParams, dropConsumer);
    }

    private static int drop(CommandContext<CommandSourceStack> context, Holder<LootTable> lootTable, LootParams params, LootCommand.DropConsumer dropConsumer) throws CommandSyntaxException {
        CommandSourceStack commandSourceStack = context.getSource();
        List<ItemStack> randomItems = lootTable.value().getRandomItems(params);
        return dropConsumer.accept(context, randomItems, items -> callback(commandSourceStack, items));
    }

    @FunctionalInterface
    interface Callback {
        void accept(List<ItemStack> items) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface DropConsumer {
        int accept(CommandContext<CommandSourceStack> context, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface TailProvider {
        ArgumentBuilder<CommandSourceStack, ?> construct(ArgumentBuilder<CommandSourceStack, ?> builder, LootCommand.DropConsumer dropConsumer);
    }
}
