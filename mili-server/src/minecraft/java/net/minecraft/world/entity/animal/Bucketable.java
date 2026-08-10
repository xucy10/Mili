package net.minecraft.world.entity.animal;

import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public interface Bucketable {
    boolean fromBucket();

    void setFromBucket(boolean fromBucket);

    void saveToBucketTag(ItemStack stack);

    void loadFromBucketTag(CompoundTag tag);

    ItemStack getBucketItemStack();

    SoundEvent getPickupSound();

    @Deprecated
    static void saveDefaultDataToBucketTag(Mob mob, ItemStack bucket) {
        bucket.copyFrom(DataComponents.CUSTOM_NAME, mob);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, bucket, compoundTag -> {
            if (mob.isNoAi()) {
                compoundTag.putBoolean("NoAI", mob.isNoAi());
            }

            if (mob.isSilent()) {
                compoundTag.putBoolean("Silent", mob.isSilent());
            }

            if (mob.isNoGravity()) {
                compoundTag.putBoolean("NoGravity", mob.isNoGravity());
            }

            if (mob.hasGlowingTag()) {
                compoundTag.putBoolean("Glowing", mob.hasGlowingTag());
            }

            if (mob.isInvulnerable()) {
                compoundTag.putBoolean("Invulnerable", mob.isInvulnerable());
            }

            compoundTag.putFloat("Health", mob.getHealth());
        });
    }

    @Deprecated
    static void loadDefaultDataFromBucketTag(Mob mob, CompoundTag tag) {
        tag.getBoolean("NoAI").ifPresent(mob::setNoAi);
        tag.getBoolean("Silent").ifPresent(mob::setSilent);
        tag.getBoolean("NoGravity").ifPresent(mob::setNoGravity);
        tag.getBoolean("Glowing").ifPresent(mob::setGlowingTag);
        tag.getBoolean("Invulnerable").ifPresent(mob::setInvulnerable);
        tag.getFloat("Health").ifPresent(mob::setHealth);
    }

    static <T extends LivingEntity & Bucketable> Optional<InteractionResult> bucketMobPickup(Player player, InteractionHand hand, T entity) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.getItem() == Items.WATER_BUCKET && entity.isAlive()) {
            // CraftBukkit start
            // entity.playSound(entity.getPickupSound(), 1.0F, 1.0F); // CraftBukkit - moved down
            ItemStack bucketItemStack = entity.getBucketItemStack();
            entity.saveToBucketTag(bucketItemStack);
            org.bukkit.event.player.PlayerBucketEntityEvent playerBucketFishEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerFishBucketEvent(entity, player, itemInHand, bucketItemStack, hand);
            bucketItemStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(playerBucketFishEvent.getEntityBucket());
            if (playerBucketFishEvent.isCancelled()) {
                // Paper start - Fix inventory desync
                if (player.hasInfiniteMaterials() || itemInHand.getCount() > 1) {
                    player.containerMenu.sendAllDataToRemote();
                } else {
                    player.containerMenu.forceHeldSlot(hand);
                }
                // Paper end - Fix inventory desync
                entity.resendPossiblyDesyncedEntityData((ServerPlayer) player); // Paper
                return Optional.of(InteractionResult.FAIL);
            }
            entity.playSound(entity.getPickupSound(), 1.0F, 1.0F);
            // CraftBukkit end
            ItemStack itemStack = ItemUtils.createFilledResult(itemInHand, player, bucketItemStack, false);
            player.setItemInHand(hand, itemStack);
            Level level = entity.level();
            if (!level.isClientSide()) {
                CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer)player, bucketItemStack);
            }

            entity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            return Optional.of(InteractionResult.SUCCESS);
        } else {
            return Optional.empty();
        }
    }
}
