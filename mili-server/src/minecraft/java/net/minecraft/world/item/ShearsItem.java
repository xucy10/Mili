package net.minecraft.world.item;

import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class ShearsItem extends Item {
    public ShearsItem(Item.Properties properties) {
        super(properties);
    }

    public static Tool createToolProperties() {
        HolderGetter<Block> holderGetter = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        return new Tool(
            List.of(
                Tool.Rule.minesAndDrops(HolderSet.direct(Blocks.COBWEB.builtInRegistryHolder()), 15.0F),
                Tool.Rule.overrideSpeed(holderGetter.getOrThrow(BlockTags.LEAVES), 15.0F),
                Tool.Rule.overrideSpeed(holderGetter.getOrThrow(BlockTags.WOOL), 5.0F),
                Tool.Rule.overrideSpeed(HolderSet.direct(Blocks.VINE.builtInRegistryHolder(), Blocks.GLOW_LICHEN.builtInRegistryHolder()), 2.0F)
            ),
            1.0F,
            1,
            true
        );
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity livingEntity) {
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool == null) {
            return false;
        } else {
            if (!level.isClientSide() && !state.is(BlockTags.FIRE) && tool.damagePerBlock() > 0) {
                stack.hurtAndBreak(tool.damagePerBlock(), livingEntity, EquipmentSlot.MAINHAND);
            }

            return true;
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(clickedPos);
        if (blockState.getBlock() instanceof GrowingPlantHeadBlock growingPlantHeadBlock && !growingPlantHeadBlock.isMaxAge(blockState)) {
            Player player = context.getPlayer();
            ItemStack itemInHand = context.getItemInHand();
            if (player instanceof ServerPlayer) {
                CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger((ServerPlayer)player, clickedPos, itemInHand);
            }

            level.playSound(player, clickedPos, SoundEvents.GROWING_PLANT_CROP, SoundSource.BLOCKS, 1.0F, 1.0F);
            BlockState maxAgeState = growingPlantHeadBlock.getMaxAgeState(blockState);
            level.setBlockAndUpdate(clickedPos, maxAgeState);
            level.gameEvent(GameEvent.BLOCK_CHANGE, clickedPos, GameEvent.Context.of(context.getPlayer(), maxAgeState));
            if (player != null) {
                itemInHand.hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.useOn(context);
        }
    }
}
