package net.minecraft.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

public class FlintAndSteelItem extends Item {
    public FlintAndSteelItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(clickedPos);
        if (!CampfireBlock.canLight(blockState) && !CandleBlock.canLight(blockState) && !CandleCakeBlock.canLight(blockState)) {
            BlockPos blockPos = clickedPos.relative(context.getClickedFace());
            if (BaseFireBlock.canBePlacedAt(level, blockPos, context.getHorizontalDirection())) {
                // CraftBukkit start - Store the clicked block
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(level, blockPos, org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, player).isCancelled()) {
                    context.getItemInHand().hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
                    return InteractionResult.PASS;
                }
                // CraftBukkit end
                level.playSound(player, blockPos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
                BlockState state = BaseFireBlock.getState(level, blockPos);
                level.setBlock(blockPos, state, Block.UPDATE_ALL_IMMEDIATE);
                level.gameEvent(player, GameEvent.BLOCK_PLACE, clickedPos);
                ItemStack itemInHand = context.getItemInHand();
                if (player instanceof ServerPlayer) {
                    CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer)player, blockPos, itemInHand);
                    itemInHand.hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
                }

                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.FAIL;
            }
        } else {
            // CraftBukkit start - Store the clicked block
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(level, clickedPos, org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, player).isCancelled()) {
                context.getItemInHand().hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
                return InteractionResult.PASS;
            }
            // CraftBukkit end
            level.playSound(player, clickedPos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
            level.setBlock(clickedPos, blockState.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, clickedPos);
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
            }

            return InteractionResult.SUCCESS;
        }
    }
}
