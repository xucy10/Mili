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
import net.minecraft.world.item.ItemStack;

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
            // Leaves start - shaveSnowLayers
            if (fun.bm.mili.config.modules.function.OldFeatureConfig.shaveSnowLayers && blockState.is(Blocks.SNOW)) {
                int layers = blockState.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS);
                ItemStack tool = context.getItemInHand();
                boolean hasSilkTouch = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), tool) > 0;
                BlockState shavedBlockState = layers > 1 ? blockState.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, layers - 1) : Blocks.AIR.defaultBlockState();

                level.setBlock(clickedPos, shavedBlockState, Block.UPDATE_ALL_IMMEDIATE);
                level.gameEvent(GameEvent.BLOCK_CHANGE, clickedPos, GameEvent.Context.of(player, shavedBlockState));

                Block.popResource(level, clickedPos, new ItemStack(hasSilkTouch ? Items.SNOW : Items.SNOWBALL));
                level.playSound(player, clickedPos, SoundEvents.SNOW_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);

                if (player != null) {
                    tool.hurtAndBreak(1, player, context.getHand());
                }

                return InteractionResult.SUCCESS;
            }
            // Leaves end -  shaveSnowLayers
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
