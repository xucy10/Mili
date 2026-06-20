package net.minecraft.world.entity.animal.equine;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import org.jspecify.annotations.Nullable;

public class SkeletonTrapGoal extends Goal {
    private final SkeletonHorse horse;
    private java.util.List<org.bukkit.entity.HumanEntity> eligiblePlayers; // Paper

    public SkeletonTrapGoal(SkeletonHorse horse) {
        this.horse = horse;
    }

    @Override
    public boolean canUse() {
        return !(this.eligiblePlayers = this.horse.level().findNearbyBukkitPlayers(this.horse.getX(), this.horse.getY(), this.horse.getZ(), 10.0, net.minecraft.world.entity.EntitySelector.PLAYER_AFFECTS_SPAWNING)).isEmpty(); // Paper - Affects Spawning API & SkeletonHorseTrapEvent
    }

    @Override
    public void tick() {
        ServerLevel serverLevel = (ServerLevel)this.horse.level();
        if (!new com.destroystokyo.paper.event.entity.SkeletonHorseTrapEvent((org.bukkit.entity.SkeletonHorse) this.horse.getBukkitEntity(), this.eligiblePlayers).callEvent()) return; // Paper
        DifficultyInstance currentDifficultyAt = serverLevel.getCurrentDifficultyAt(this.horse.blockPosition());
        this.horse.setTrap(false);
        this.horse.setTamed(true);
        this.horse.setAge(0);
        LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(serverLevel, EntitySpawnReason.TRIGGERED);
        if (lightningBolt != null) {
            lightningBolt.snapTo(this.horse.getX(), this.horse.getY(), this.horse.getZ());
            lightningBolt.setVisualOnly(true);
            serverLevel.strikeLightning(lightningBolt, org.bukkit.event.weather.LightningStrikeEvent.Cause.TRAP); // CraftBukkit
            Skeleton skeleton = this.createSkeleton(currentDifficultyAt, this.horse);
            if (skeleton != null) {
                skeleton.startRiding(this.horse);
                serverLevel.addFreshEntityWithPassengers(skeleton, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.TRAP); // CraftBukkit

                for (int i = 0; i < 3; i++) {
                    AbstractHorse abstractHorse = this.createHorse(currentDifficultyAt);
                    if (abstractHorse != null) {
                        Skeleton skeleton1 = this.createSkeleton(currentDifficultyAt, abstractHorse);
                        if (skeleton1 != null) {
                            skeleton1.startRiding(abstractHorse);
                            abstractHorse.push(this.horse.getRandom().triangle(0.0, 1.1485), 0.0, this.horse.getRandom().triangle(0.0, 1.1485));
                            serverLevel.addFreshEntityWithPassengers(abstractHorse, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.JOCKEY); // CraftBukkit
                        }
                    }
                }
            }
        }
    }

    private @Nullable AbstractHorse createHorse(DifficultyInstance difficulty) {
        SkeletonHorse skeletonHorse = EntityType.SKELETON_HORSE.create(this.horse.level(), EntitySpawnReason.TRIGGERED);
        if (skeletonHorse != null) {
            skeletonHorse.finalizeSpawn((ServerLevel)this.horse.level(), difficulty, EntitySpawnReason.TRIGGERED, null);
            skeletonHorse.setPos(this.horse.getX(), this.horse.getY(), this.horse.getZ());
            skeletonHorse.invulnerableTime = 60;
            skeletonHorse.setPersistenceRequired();
            skeletonHorse.setTamed(true);
            skeletonHorse.setAge(0);
        }

        return skeletonHorse;
    }

    private @Nullable Skeleton createSkeleton(DifficultyInstance difficulty, AbstractHorse horse) {
        Skeleton skeleton = EntityType.SKELETON.create(horse.level(), EntitySpawnReason.TRIGGERED);
        if (skeleton != null) {
            skeleton.finalizeSpawn((ServerLevel)horse.level(), difficulty, EntitySpawnReason.TRIGGERED, null);
            skeleton.setPos(horse.getX(), horse.getY(), horse.getZ());
            skeleton.invulnerableTime = 60;
            skeleton.setPersistenceRequired();
            if (skeleton.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            }

            this.enchant(skeleton, EquipmentSlot.MAINHAND, difficulty);
            this.enchant(skeleton, EquipmentSlot.HEAD, difficulty);
        }

        return skeleton;
    }

    private void enchant(Skeleton skeleton, EquipmentSlot slot, DifficultyInstance difficulty) {
        ItemStack itemBySlot = skeleton.getItemBySlot(slot);
        itemBySlot.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        EnchantmentHelper.enchantItemFromProvider(
            itemBySlot, skeleton.level().registryAccess(), VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficulty, skeleton.getRandom()
        );
        skeleton.setItemSlot(slot, itemBySlot);
    }
}
