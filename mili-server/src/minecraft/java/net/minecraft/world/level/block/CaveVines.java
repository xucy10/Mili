package net.minecraft.world.level.block;

import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface CaveVines {
    VoxelShape SHAPE = Block.column(14.0, 0.0, 16.0);
    BooleanProperty BERRIES = BlockStateProperties.BERRIES;

    static InteractionResult use(Entity entity, BlockState state, Level level, BlockPos pos) {
        if (state.getValue(BERRIES)) {
            if (level instanceof ServerLevel serverLevel) {
                // CraftBukkit start - call entity change block event
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, state.setValue(CaveVines.BERRIES, false))) {
                    return InteractionResult.SUCCESS;
                }
                // CraftBukkit end - call entity change block event
                java.util.List<net.minecraft.world.item.ItemStack> drops = new java.util.ArrayList<>(); // Paper - call player harvest block event - store drops from loottable
                Block.dropFromBlockInteractLootTable(
                    serverLevel,
                    BuiltInLootTables.HARVEST_CAVE_VINE,
                    state,
                    level.getBlockEntity(pos),
                    null,
                    entity,
                    (serverLevel1, itemStack) -> drops.add(itemStack) // Paper - call player harvest block event - store drops from loottable
                );
                // Paper start - call player harvest block event
                if (entity instanceof net.minecraft.world.entity.player.Player player) {
                    org.bukkit.event.player.PlayerHarvestBlockEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerHarvestBlockEvent(
                        level, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND, drops
                    );
                    if (event.isCancelled()) {
                        return InteractionResult.SUCCESS; // We need to return a success either way, because making it PASS or FAIL will result in a bug where cancelling while harvesting w/ block in hand places block
                    }
                    for (org.bukkit.inventory.ItemStack itemStack : event.getItemsHarvested()) {
                        Block.popResource(level, pos, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemStack));
                    }
                } else {
                    for (net.minecraft.world.item.ItemStack itemStack : drops) {
                        Block.popResource(level, pos, itemStack);
                    }
                }
                // Paper end - call player harvest block event
                float f = Mth.randomBetween(serverLevel.random, 0.8F, 1.2F);
                serverLevel.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, f);
                BlockState blockState = state.setValue(BERRIES, false);
                serverLevel.setBlock(pos, blockState, Block.UPDATE_CLIENTS);
                serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, blockState));
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    static boolean hasGlowBerries(BlockState state) {
        return state.hasProperty(BERRIES) && state.getValue(BERRIES);
    }

    static ToIntFunction<BlockState> emission(int berries) {
        return state -> state.getValue(BlockStateProperties.BERRIES) ? berries : 0;
    }
}
