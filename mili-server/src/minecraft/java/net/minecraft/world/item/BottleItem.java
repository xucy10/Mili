package net.minecraft.world.item;

import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class BottleItem extends Item {
    public BottleItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        List<AreaEffectCloud> entitiesOfClass = level.getEntitiesOfClass(
            AreaEffectCloud.class,
            player.getBoundingBox().inflate(2.0),
            areaEffectCloud1 -> areaEffectCloud1.isAlive() && areaEffectCloud1.getOwner() instanceof EnderDragon
        );
        ItemStack itemInHand = player.getItemInHand(hand);
        if (!entitiesOfClass.isEmpty()) {
            AreaEffectCloud areaEffectCloud = entitiesOfClass.get(0);
            areaEffectCloud.setRadius(areaEffectCloud.getRadius() - 0.5F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL_DRAGONBREATH, SoundSource.NEUTRAL, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, player.position());
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(serverPlayer, itemInHand, areaEffectCloud);
            }

            return InteractionResult.SUCCESS.heldItemTransformedTo(this.turnBottleIntoItem(itemInHand, player, new ItemStack(Items.DRAGON_BREATH)));
        } else {
            BlockHitResult playerPovHitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (playerPovHitResult.getType() == HitResult.Type.MISS) {
                return InteractionResult.PASS;
            } else {
                if (playerPovHitResult.getType() == HitResult.Type.BLOCK) {
                    BlockPos blockPos = playerPovHitResult.getBlockPos();
                    if (!level.mayInteract(player, blockPos)) {
                        return InteractionResult.PASS;
                    }

                    if (level.getFluidState(blockPos).is(FluidTags.WATER)) {
                        level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
                        level.gameEvent(player, GameEvent.FLUID_PICKUP, blockPos);
                        return InteractionResult.SUCCESS
                            .heldItemTransformedTo(this.turnBottleIntoItem(itemInHand, player, PotionContents.createItemStack(Items.POTION, Potions.WATER)));
                    }
                }

                return InteractionResult.PASS;
            }
        }
    }

    protected ItemStack turnBottleIntoItem(ItemStack bottleStack, Player player, ItemStack filledBottleStack) {
        player.awardStat(Stats.ITEM_USED.get(this));
        return ItemUtils.createFilledResult(bottleStack, player, filledBottleStack);
    }
}
