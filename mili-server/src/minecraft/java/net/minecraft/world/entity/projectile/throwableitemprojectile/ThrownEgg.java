package net.minecraft.world.entity.projectile.throwableitemprojectile;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class ThrownEgg extends ThrowableItemProjectile {
    private static final EntityDimensions ZERO_SIZED_DIMENSIONS = EntityDimensions.fixed(0.0F, 0.0F);

    public ThrownEgg(EntityType<? extends ThrownEgg> type, Level level) {
        super(type, level);
    }

    public ThrownEgg(Level level, LivingEntity owner, ItemStack item) {
        super(EntityType.EGG, owner, level, item);
    }

    public ThrownEgg(Level level, double x, double y, double z, ItemStack item) {
        super(EntityType.EGG, x, y, z, level, item);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.DEATH) {
            double d = 0.08;

            for (int i = 0; i < 8; i++) {
                this.level()
                    .addParticle(
                        new ItemParticleOption(ParticleTypes.ITEM, this.getItem()),
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        (this.random.nextFloat() - 0.5) * 0.08,
                        (this.random.nextFloat() - 0.5) * 0.08,
                        (this.random.nextFloat() - 0.5) * 0.08
                    );
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        result.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide()) {
            // CraftBukkit start
            boolean hatching = this.random.nextInt(8) == 0;
            if (true) {
                // CraftBukkit end
                int i = 1;
                if (this.random.nextInt(32) == 0) {
                    i = 4;
                }
                // CraftBukkit start
                if (!hatching) {
                    i = 0;
                }

                net.minecraft.world.entity.Entity shooter = this.getOwner();
                org.bukkit.entity.EntityType hatchingType = org.bukkit.entity.EntityType.CHICKEN;
                if (shooter instanceof net.minecraft.server.level.ServerPlayer) {
                    org.bukkit.event.player.PlayerEggThrowEvent event = new org.bukkit.event.player.PlayerEggThrowEvent((org.bukkit.entity.Player) shooter.getBukkitEntity(), (org.bukkit.entity.Egg) this.getBukkitEntity(), hatching, (byte) i, hatchingType);
                    event.callEvent();

                    hatching = event.isHatching();
                    i = hatching ? event.getNumHatches() : 0; // If hatching is set to false, ensure child count is 0
                    hatchingType = event.getHatchingType();
                }
                // CraftBukkit end
                // Paper start - Add ThrownEggHatchEvent
                com.destroystokyo.paper.event.entity.ThrownEggHatchEvent event = new com.destroystokyo.paper.event.entity.ThrownEggHatchEvent((org.bukkit.entity.Egg) getBukkitEntity(), hatching, (byte) i, hatchingType);
                event.callEvent();
                hatching = event.isHatching();
                i = hatching ? event.getNumHatches() : 0; // If hatching is set to false, ensure child count is 0
                hatchingType = event.getHatchingType();
                EntityType<?> newEntityType = org.bukkit.craftbukkit.entity.CraftEntityType.bukkitToMinecraft(hatchingType);
                // Paper end - Add ThrownEggHatchEvent

                for (int i1 = 0; i1 < i; i1++) {
                    net.minecraft.world.entity.Entity chicken = newEntityType.create(this.level(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED); // CraftBukkit
                    if (chicken != null) {
                        chicken.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F); // Folia - region threading - moved up
                        // CraftBukkit start
                        if (chicken.getBukkitEntity() instanceof org.bukkit.entity.Ageable ageable) {
                            ageable.setBaby();
                        }
                        // CraftBukkit end
                        // Folia - region threading - move up
                        // CraftBukkit start
                        if (chicken instanceof Chicken realChicken) {
                            Optional.ofNullable(this.getItem().get(DataComponents.CHICKEN_VARIANT))
                                .flatMap(eitherHolder -> eitherHolder.unwrap(this.registryAccess()))
                                .ifPresent(realChicken::setVariant);
                        }
                        // CraftBukkit end
                        if (!chicken.fudgePositionAfterSizeChange(ZERO_SIZED_DIMENSIONS)) {
                            break;
                        }

                        this.level().addFreshEntity(chicken, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EGG); // CraftBukkit
                    }
                }
            }

            this.level().broadcastEntityEvent(this, EntityEvent.DEATH);
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }
    }

    @Override
    public Item getDefaultItem() {
        return Items.EGG;
    }
}
