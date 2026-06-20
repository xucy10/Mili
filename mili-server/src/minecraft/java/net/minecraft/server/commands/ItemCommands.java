package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.SlotArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SlotProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class ItemCommands {
    static final Dynamic3CommandExceptionType ERROR_TARGET_NOT_A_CONTAINER = new Dynamic3CommandExceptionType(
        (x, y, z) -> Component.translatableEscape("commands.item.target.not_a_container", x, y, z)
    );
    static final Dynamic3CommandExceptionType ERROR_SOURCE_NOT_A_CONTAINER = new Dynamic3CommandExceptionType(
        (x, y, z) -> Component.translatableEscape("commands.item.source.not_a_container", x, y, z)
    );
    static final DynamicCommandExceptionType ERROR_TARGET_INAPPLICABLE_SLOT = new DynamicCommandExceptionType(
        invalidSlot -> Component.translatableEscape("commands.item.target.no_such_slot", invalidSlot)
    );
    private static final DynamicCommandExceptionType ERROR_SOURCE_INAPPLICABLE_SLOT = new DynamicCommandExceptionType(
        invalidSlot -> Component.translatableEscape("commands.item.source.no_such_slot", invalidSlot)
    );
    private static final DynamicCommandExceptionType ERROR_TARGET_NO_CHANGES = new DynamicCommandExceptionType(
        slot -> Component.translatableEscape("commands.item.target.no_changes", slot)
    );
    private static final Dynamic2CommandExceptionType ERROR_TARGET_NO_CHANGES_KNOWN_ITEM = new Dynamic2CommandExceptionType(
        (stackName, slot) -> Component.translatableEscape("commands.item.target.no_changed.known_item", stackName, slot)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("item")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("replace")
                        .then(
                            Commands.literal("block")
                                .then(
                                    Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(
                                            Commands.argument("slot", SlotArgument.slot())
                                                .then(
                                                    Commands.literal("with")
                                                        .then(
                                                            Commands.argument("item", ItemArgument.item(context))
                                                                .executes(
                                                                    context1 -> setBlockItem(
                                                                        context1.getSource(),
                                                                        BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                                        SlotArgument.getSlot(context1, "slot"),
                                                                        ItemArgument.getItem(context1, "item").createItemStack(1, false)
                                                                    )
                                                                )
                                                                .then(
                                                                    Commands.argument("count", IntegerArgumentType.integer(1, 99))
                                                                        .executes(
                                                                            context1 -> setBlockItem(
                                                                                context1.getSource(),
                                                                                BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                                                SlotArgument.getSlot(context1, "slot"),
                                                                                ItemArgument.getItem(context1, "item")
                                                                                    .createItemStack(IntegerArgumentType.getInteger(context1, "count"), true)
                                                                            )
                                                                        )
                                                                )
                                                        )
                                                )
                                                .then(
                                                    Commands.literal("from")
                                                        .then(
                                                            Commands.literal("block")
                                                                .then(
                                                                    Commands.argument("source", BlockPosArgument.blockPos())
                                                                        .then(
                                                                            Commands.argument("sourceSlot", SlotArgument.slot())
                                                                                .executes(
                                                                                    context1 -> blockToBlock(
                                                                                        context1.getSource(),
                                                                                        BlockPosArgument.getLoadedBlockPos(context1, "source"),
                                                                                        SlotArgument.getSlot(context1, "sourceSlot"),
                                                                                        BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                                                        SlotArgument.getSlot(context1, "slot")
                                                                                    )
                                                                                )
                                                                                .then(
                                                                                    Commands.argument("modifier", ResourceOrIdArgument.lootModifier(context))
                                                                                        .executes(
                                                                                            context1 -> blockToBlock(
                                                                                                (CommandSourceStack)context1.getSource(),
                                                                                                BlockPosArgument.getLoadedBlockPos(context1, "source"),
                                                                                                SlotArgument.getSlot(context1, "sourceSlot"),
                                                                                                BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                                                                SlotArgument.getSlot(context1, "slot"),
                                                                                                ResourceOrIdArgument.getLootModifier(context1, "modifier")
                                                                                            )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                        .then(
                                                            Commands.literal("entity")
                                                                .then(
                                                                    Commands.argument("source", EntityArgument.entity())
                                                                        .then(
                                                                            Commands.argument("sourceSlot", SlotArgument.slot())
                                                                                .executes(
                                                                                    context1 -> entityToBlock(
                                                                                        context1.getSource(),
                                                                                        EntityArgument.getEntity(context1, "source"),
                                                                                        SlotArgument.getSlot(context1, "sourceSlot"),
                                                                                        BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                                                        SlotArgument.getSlot(context1, "slot")
                                                                                    )
                                                                                )
                                                                                .then(
                                                                                    Commands.argument("modifier", ResourceOrIdArgument.lootModifier(context))
                                                                                        .executes(
                                                                                            context1 -> entityToBlock(
                                                                                                (CommandSourceStack)context1.getSource(),
                                                                                                EntityArgument.getEntity(context1, "source"),
                                                                                                SlotArgument.getSlot(context1, "sourceSlot"),
                                                                                                BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                                                                SlotArgument.getSlot(context1, "slot"),
                                                                                                ResourceOrIdArgument.getLootModifier(context1, "modifier")
                                                                                            )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("entity")
                                .then(
                                    Commands.argument("targets", EntityArgument.entities())
                                        .then(
                                            Commands.argument("slot", SlotArgument.slot())
                                                .then(
                                                    Commands.literal("with")
                                                        .then(
                                                            Commands.argument("item", ItemArgument.item(context))
                                                                .executes(
                                                                    context1 -> setEntityItem(
                                                                        context1.getSource(),
                                                                        EntityArgument.getEntities(context1, "targets"),
                                                                        SlotArgument.getSlot(context1, "slot"),
                                                                        ItemArgument.getItem(context1, "item").createItemStack(1, false)
                                                                    )
                                                                )
                                                                .then(
                                                                    Commands.argument("count", IntegerArgumentType.integer(1, 99))
                                                                        .executes(
                                                                            context1 -> setEntityItem(
                                                                                context1.getSource(),
                                                                                EntityArgument.getEntities(context1, "targets"),
                                                                                SlotArgument.getSlot(context1, "slot"),
                                                                                ItemArgument.getItem(context1, "item")
                                                                                    .createItemStack(IntegerArgumentType.getInteger(context1, "count"), true)
                                                                            )
                                                                        )
                                                                )
                                                        )
                                                )
                                                .then(
                                                    Commands.literal("from")
                                                        .then(
                                                            Commands.literal("block")
                                                                .then(
                                                                    Commands.argument("source", BlockPosArgument.blockPos())
                                                                        .then(
                                                                            Commands.argument("sourceSlot", SlotArgument.slot())
                                                                                .executes(
                                                                                    context1 -> blockToEntities(
                                                                                        context1.getSource(),
                                                                                        BlockPosArgument.getLoadedBlockPos(context1, "source"),
                                                                                        SlotArgument.getSlot(context1, "sourceSlot"),
                                                                                        EntityArgument.getEntities(context1, "targets"),
                                                                                        SlotArgument.getSlot(context1, "slot")
                                                                                    )
                                                                                )
                                                                                .then(
                                                                                    Commands.argument("modifier", ResourceOrIdArgument.lootModifier(context))
                                                                                        .executes(
                                                                                            context1 -> blockToEntities(
                                                                                                (CommandSourceStack)context1.getSource(),
                                                                                                BlockPosArgument.getLoadedBlockPos(context1, "source"),
                                                                                                SlotArgument.getSlot(context1, "sourceSlot"),
                                                                                                EntityArgument.getEntities(context1, "targets"),
                                                                                                SlotArgument.getSlot(context1, "slot"),
                                                                                                ResourceOrIdArgument.getLootModifier(context1, "modifier")
                                                                                            )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                        .then(
                                                            Commands.literal("entity")
                                                                .then(
                                                                    Commands.argument("source", EntityArgument.entity())
                                                                        .then(
                                                                            Commands.argument("sourceSlot", SlotArgument.slot())
                                                                                .executes(
                                                                                    context1 -> entityToEntities(
                                                                                        context1.getSource(),
                                                                                        EntityArgument.getEntity(context1, "source"),
                                                                                        SlotArgument.getSlot(context1, "sourceSlot"),
                                                                                        EntityArgument.getEntities(context1, "targets"),
                                                                                        SlotArgument.getSlot(context1, "slot")
                                                                                    )
                                                                                )
                                                                                .then(
                                                                                    Commands.argument("modifier", ResourceOrIdArgument.lootModifier(context))
                                                                                        .executes(
                                                                                            context1 -> entityToEntities(
                                                                                                (CommandSourceStack)context1.getSource(),
                                                                                                EntityArgument.getEntity(context1, "source"),
                                                                                                SlotArgument.getSlot(context1, "sourceSlot"),
                                                                                                EntityArgument.getEntities(context1, "targets"),
                                                                                                SlotArgument.getSlot(context1, "slot"),
                                                                                                ResourceOrIdArgument.getLootModifier(context1, "modifier")
                                                                                            )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("modify")
                        .then(
                            Commands.literal("block")
                                .then(
                                    Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(
                                            Commands.argument("slot", SlotArgument.slot())
                                                .then(
                                                    Commands.argument("modifier", ResourceOrIdArgument.lootModifier(context))
                                                        .executes(
                                                            context1 -> modifyBlockItem(
                                                                (CommandSourceStack)context1.getSource(),
                                                                BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                                SlotArgument.getSlot(context1, "slot"),
                                                                ResourceOrIdArgument.getLootModifier(context1, "modifier")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("entity")
                                .then(
                                    Commands.argument("targets", EntityArgument.entities())
                                        .then(
                                            Commands.argument("slot", SlotArgument.slot())
                                                .then(
                                                    Commands.argument("modifier", ResourceOrIdArgument.lootModifier(context))
                                                        .executes(
                                                            context1 -> modifyEntityItem(
                                                                (CommandSourceStack)context1.getSource(),
                                                                EntityArgument.getEntities(context1, "targets"),
                                                                SlotArgument.getSlot(context1, "slot"),
                                                                ResourceOrIdArgument.getLootModifier(context1, "modifier")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int modifyBlockItem(CommandSourceStack source, BlockPos pos, int slot, Holder<LootItemFunction> modifier) throws CommandSyntaxException {
        Container container = getContainer(source, pos, ERROR_TARGET_NOT_A_CONTAINER);
        if (slot >= 0 && slot < container.getContainerSize()) {
            ItemStack itemStack = applyModifier(source, modifier, container.getItem(slot));
            container.setItem(slot, itemStack);
            source.sendSuccess(
                () -> Component.translatable("commands.item.block.set.success", pos.getX(), pos.getY(), pos.getZ(), itemStack.getDisplayName()), true
            );
            return 1;
        } else {
            throw ERROR_TARGET_INAPPLICABLE_SLOT.create(slot);
        }
    }

    private static int modifyEntityItem(CommandSourceStack source, Collection<? extends Entity> targets, int sourceSlot, Holder<LootItemFunction> modifier) throws CommandSyntaxException {
        Map<Entity, ItemStack> map = Maps.newHashMapWithExpectedSize(targets.size());

        for (Entity entity : targets) {
            SlotAccess slot = entity.getSlot(sourceSlot);
            if (slot != null) {
                ItemStack itemStack = applyModifier(source, modifier, slot.get().copy());
                if (slot.set(itemStack)) {
                    map.put(entity, itemStack);
                    if (entity instanceof ServerPlayer serverPlayer) {
                        serverPlayer.containerMenu.broadcastChanges();
                    }
                }
            }
        }

        if (map.isEmpty()) {
            throw ERROR_TARGET_NO_CHANGES.create(sourceSlot);
        } else {
            if (map.size() == 1) {
                Entry<Entity, ItemStack> entry = map.entrySet().iterator().next();
                source.sendSuccess(
                    () -> Component.translatable("commands.item.entity.set.success.single", entry.getKey().getDisplayName(), entry.getValue().getDisplayName()),
                    true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.item.entity.set.success.multiple", map.size()), true);
            }

            return map.size();
        }
    }

    private static int setBlockItem(CommandSourceStack source, BlockPos pos, int slot, ItemStack item) throws CommandSyntaxException {
        Container container = getContainer(source, pos, ERROR_TARGET_NOT_A_CONTAINER);
        if (slot >= 0 && slot < container.getContainerSize()) {
            container.setItem(slot, item);
            source.sendSuccess(() -> Component.translatable("commands.item.block.set.success", pos.getX(), pos.getY(), pos.getZ(), item.getDisplayName()), true);
            return 1;
        } else {
            throw ERROR_TARGET_INAPPLICABLE_SLOT.create(slot);
        }
    }

    static Container getContainer(CommandSourceStack source, BlockPos pos, Dynamic3CommandExceptionType exception) throws CommandSyntaxException {
        if (source.getLevel().getBlockEntity(pos) instanceof Container container) {
            return container;
        } else {
            throw exception.create(pos.getX(), pos.getY(), pos.getZ());
        }
    }

    private static int setEntityItem(CommandSourceStack source, Collection<? extends Entity> targets, int slot, ItemStack item) throws CommandSyntaxException {
        List<Entity> list = Lists.newArrayListWithCapacity(targets.size());

        for (Entity entity : targets) {
            SlotAccess slot1 = entity.getSlot(slot);
            if (slot1 != null && slot1.set(item.copy())) {
                list.add(entity);
                if (entity instanceof ServerPlayer serverPlayer) {
                    serverPlayer.containerMenu.broadcastChanges();
                }
            }
        }

        if (list.isEmpty()) {
            throw ERROR_TARGET_NO_CHANGES_KNOWN_ITEM.create(item.getDisplayName(), slot);
        } else {
            if (list.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.item.entity.set.success.single", list.getFirst().getDisplayName(), item.getDisplayName()), true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.item.entity.set.success.multiple", list.size(), item.getDisplayName()), true);
            }

            return list.size();
        }
    }

    private static int blockToEntities(CommandSourceStack source, BlockPos pos, int sourceSlot, Collection<? extends Entity> targets, int slot) throws CommandSyntaxException {
        return setEntityItem(source, targets, slot, getBlockItem(source, pos, sourceSlot));
    }

    private static int blockToEntities(
        CommandSourceStack source, BlockPos pos, int sourceSlot, Collection<? extends Entity> targets, int slot, Holder<LootItemFunction> modifier
    ) throws CommandSyntaxException {
        return setEntityItem(source, targets, slot, applyModifier(source, modifier, getBlockItem(source, pos, sourceSlot)));
    }

    private static int blockToBlock(CommandSourceStack source, BlockPos sourcePos, int sourceSlot, BlockPos pos, int slot) throws CommandSyntaxException {
        return setBlockItem(source, pos, slot, getBlockItem(source, sourcePos, sourceSlot));
    }

    private static int blockToBlock(CommandSourceStack source, BlockPos sourcePos, int sourceSlot, BlockPos pos, int slot, Holder<LootItemFunction> modifier) throws CommandSyntaxException {
        return setBlockItem(source, pos, slot, applyModifier(source, modifier, getBlockItem(source, sourcePos, sourceSlot)));
    }

    private static int entityToBlock(CommandSourceStack source, Entity sourceEntity, int sourceSlot, BlockPos pos, int slot) throws CommandSyntaxException {
        return setBlockItem(source, pos, slot, getItemInSlot(sourceEntity, sourceSlot));
    }

    private static int entityToBlock(CommandSourceStack source, Entity sourceEntity, int sourceSlot, BlockPos pos, int slot, Holder<LootItemFunction> modifier) throws CommandSyntaxException {
        return setBlockItem(source, pos, slot, applyModifier(source, modifier, getItemInSlot(sourceEntity, sourceSlot)));
    }

    private static int entityToEntities(CommandSourceStack source, Entity sourceEntity, int sourceSlot, Collection<? extends Entity> targets, int slot) throws CommandSyntaxException {
        return setEntityItem(source, targets, slot, getItemInSlot(sourceEntity, sourceSlot));
    }

    private static int entityToEntities(
        CommandSourceStack source, Entity sourceEntity, int sourceSlot, Collection<? extends Entity> targets, int slot, Holder<LootItemFunction> modifier
    ) throws CommandSyntaxException {
        return setEntityItem(source, targets, slot, applyModifier(source, modifier, getItemInSlot(sourceEntity, sourceSlot)));
    }

    private static ItemStack applyModifier(CommandSourceStack source, Holder<LootItemFunction> modifier, ItemStack stack) {
        ServerLevel level = source.getLevel();
        LootParams lootParams = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, source.getPosition())
            .withOptionalParameter(LootContextParams.THIS_ENTITY, source.getEntity())
            .create(LootContextParamSets.COMMAND);
        LootContext lootContext = new LootContext.Builder(lootParams).create(Optional.empty());
        lootContext.pushVisitedElement(LootContext.createVisitedEntry(modifier.value()));
        ItemStack itemStack = modifier.value().apply(stack, lootContext);
        itemStack.limitSize(itemStack.getMaxStackSize());
        return itemStack;
    }

    private static ItemStack getItemInSlot(SlotProvider slotProvider, int slotIndex) throws CommandSyntaxException {
        SlotAccess slot = slotProvider.getSlot(slotIndex);
        if (slot == null) {
            throw ERROR_SOURCE_INAPPLICABLE_SLOT.create(slotIndex);
        } else {
            return slot.get().copy();
        }
    }

    private static ItemStack getBlockItem(CommandSourceStack source, BlockPos pos, int slot) throws CommandSyntaxException {
        Container container = getContainer(source, pos, ERROR_SOURCE_NOT_A_CONTAINER);
        return getItemInSlot(container, slot);
    }
}
