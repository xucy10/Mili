package net.minecraft.world.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class FishingRodItem extends Item {
    public FishingRodItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (player.fishing != null) {
            if (!level.isClientSide()) {
                int i = player.fishing.retrieve(itemInHand, hand); // Paper - Add hand parameter to PlayerFishEvent
                itemInHand.hurtAndBreak(i, player, hand.asEquipmentSlot());
            }

            level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.NEUTRAL,
                1.0F,
                0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
            );
            itemInHand.causeUseVibration(player, GameEvent.ITEM_INTERACT_FINISH);
        } else {
            // CraftBukkit - moved down
            if (level instanceof ServerLevel serverLevel) {
                int i1 = (int)(EnchantmentHelper.getFishingTimeReduction(serverLevel, itemInHand, player) * 20.0F);
                int fishingLuckBonus = EnchantmentHelper.getFishingLuckBonus(serverLevel, itemInHand, player);
                // CraftBukkit start
                FishingHook fishingHook = new FishingHook(player, level, fishingLuckBonus, i1);
                org.bukkit.event.player.PlayerFishEvent playerFishEvent = new org.bukkit.event.player.PlayerFishEvent((org.bukkit.entity.Player) player.getBukkitEntity(), null, (org.bukkit.entity.FishHook) fishingHook.getBukkitEntity(), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand), org.bukkit.event.player.PlayerFishEvent.State.FISHING);
                level.getCraftServer().getPluginManager().callEvent(playerFishEvent);

                if (playerFishEvent.isCancelled()) {
                    player.fishing = null;
                    return InteractionResult.PASS;
                }
                level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.NEUTRAL,
                    0.5F,
                    0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
                );
                Projectile.spawnProjectile(fishingHook, serverLevel, itemInHand);
                // CraftBukkit end
            }

            player.awardStat(Stats.ITEM_USED.get(this));
            itemInHand.causeUseVibration(player, GameEvent.ITEM_INTERACT_START);
        }

        return InteractionResult.SUCCESS;
    }
}
