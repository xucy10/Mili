package net.minecraft.world.item;

import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class ShovelItem extends Item {
    protected static final Map<Block, BlockState> FLATTENABLES = Maps.newHashMap(
        new Builder()
            .put(Blocks.GRASS_BLOCK, Blocks.DIRT_PATH.defaultBlockState())
            .put(Blocks.DIRT, Blocks.DIRT_PATH.defaultBlockState())
            .put(Blocks.PODZOL, Blocks.DIRT_PATH.defaultBlockState())
            .put(Blocks.COARSE_DIRT, Blocks.DIRT_PATH.defaultBlockState())
            .put(Blocks.MYCELIUM, Blocks.DIRT_PATH.defaultBlockState())
            .put(Blocks.ROOTED_DIRT, Blocks.DIRT_PATH.defaultBlockState())
            .build()
    );

    public ShovelItem(ToolMaterial material, float attackDamage, float attackSpeed, Item.Properties properties) {
        super(properties.shovel(material, attackDamage, attackSpeed));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(clickedPos);
        if (context.getClickedFace() == Direction.DOWN) {
            return InteractionResult.PASS;
        } else {
            Player player = context.getPlayer();
            BlockState blockState1 = FLATTENABLES.get(blockState.getBlock());
            BlockState blockState2 = null;
            Runnable afterAction = null; // Paper
            if (blockState1 != null && level.getBlockState(clickedPos.above()).isAir()) {
                afterAction = () -> level.playSound(player, clickedPos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0F, 1.0F); // Paper
                blockState2 = blockState1;
            } else if (blockState.getBlock() instanceof CampfireBlock && blockState.getValue(CampfireBlock.LIT)) {
                afterAction = () -> { // Paper
                if (!level.isClientSide()) {
                    level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, clickedPos, 0);
                }

                CampfireBlock.dowse(context.getPlayer(), level, clickedPos, blockState);
                }; // Paper
                blockState2 = blockState.setValue(CampfireBlock.LIT, false);
            }

            if (blockState2 != null) {
                if (!level.isClientSide()) {
                    // Paper start
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(context.getPlayer(), clickedPos, blockState2)) {
                        return InteractionResult.PASS;
                    }
                    afterAction.run();
                    // Paper end
                    level.setBlock(clickedPos, blockState2, Block.UPDATE_ALL_IMMEDIATE);
                    level.gameEvent(GameEvent.BLOCK_CHANGE, clickedPos, GameEvent.Context.of(player, blockState2));
                    if (player != null) {
                        context.getItemInHand().hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
                    }
                }

                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.PASS;
            }
        }
    }
}
