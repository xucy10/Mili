package net.minecraft.world.entity.projectile.throwableitemprojectile;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public abstract class AbstractThrownPotion extends ThrowableItemProjectile {
    public static final double SPLASH_RANGE = 4.0;
    protected static final double SPLASH_RANGE_SQ = 16.0;
    public static final Predicate<LivingEntity> WATER_SENSITIVE_OR_ON_FIRE = livingEntity -> livingEntity.isSensitiveToWater() || livingEntity.isOnFire();

    public AbstractThrownPotion(EntityType<? extends AbstractThrownPotion> type, Level level) {
        super(type, level);
    }

    public AbstractThrownPotion(EntityType<? extends AbstractThrownPotion> type, Level level, LivingEntity owner, ItemStack item) {
        super(type, owner, level, item);
    }

    public AbstractThrownPotion(EntityType<? extends AbstractThrownPotion> type, Level level, double x, double y, double z, ItemStack item) {
        super(type, x, y, z, level, item);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide()) {
            ItemStack item = this.getItem();
            Direction direction = result.getDirection();
            BlockPos blockPos = result.getBlockPos();
            BlockPos blockPos1 = blockPos.relative(direction);
            PotionContents potionContents = item.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            if (potionContents.is(Potions.WATER)) {
                this.dowseFire(blockPos1);
                this.dowseFire(blockPos1.relative(direction.getOpposite()));

                for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                    this.dowseFire(blockPos1.relative(direction1));
                }
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        // Paper start - More projectile API
        this.splash(result);
    }

    public void splash(HitResult result) {
        // Paper end - More projectile API
        if (this.level() instanceof ServerLevel serverLevel) {
            ItemStack item = this.getItem();
            PotionContents potionContents = item.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            boolean showParticles = true; // Paper - Fix potions splash events
            if (potionContents.is(Potions.WATER)) {
                showParticles = this.onHitAsWater(serverLevel, result); // Paper - Fix potions splash events
            } else if (true || potionContents.hasEffects()) { // CraftBukkit - Call event even if no effects to apply
                showParticles = this.onHitAsPotion(serverLevel, item, result); // Paper - pass HitResult
            }

            if (showParticles) { // Paper - Fix potions splash events
            int i = potionContents.potion().isPresent() && potionContents.potion().get().value().hasInstantEffects()
                ? LevelEvent.PARTICLES_INSTANT_POTION_SPLASH
                : LevelEvent.PARTICLES_SPELL_POTION_SPLASH;
            serverLevel.levelEvent(i, this.blockPosition(), potionContents.getColor());
            } // Paper - Fix potions splash events
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }
    }

    private static final Predicate<LivingEntity> APPLY_WATER_GET_ENTITIES_PREDICATE = AbstractThrownPotion.WATER_SENSITIVE_OR_ON_FIRE.or(Axolotl.class::isInstance); // Paper - Fix potions splash events

    private boolean onHitAsWater(ServerLevel level, HitResult result) { // Paper - Fix potions splash events
        AABB aabb = this.getBoundingBox().inflate(4.0, 2.0, 4.0);

        // Paper start - Fix potions splash events
        java.util.Map<org.bukkit.entity.LivingEntity, Double> affected = new java.util.HashMap<>();
        java.util.Set<org.bukkit.entity.LivingEntity> rehydrate = new java.util.HashSet<>();
        java.util.Set<org.bukkit.entity.LivingEntity> extinguish = new java.util.HashSet<>();
        for (LivingEntity livingEntity : this.level().getEntitiesOfClass(LivingEntity.class, aabb, APPLY_WATER_GET_ENTITIES_PREDICATE)) {
            if (livingEntity instanceof Axolotl axolotl) {
                rehydrate.add(((org.bukkit.entity.Axolotl) axolotl.getBukkitEntity()));
            }
            // Paper end - Fix potions splash events
            double d = this.distanceToSqr(livingEntity);
            if (d < 16.0) {
                if (livingEntity.isSensitiveToWater()) {
                    affected.put(livingEntity.getBukkitLivingEntity(), 1.0); // Paper - Fix potions splash events
                }

                if (livingEntity.isOnFire() && livingEntity.isAlive()) {
                    extinguish.add(livingEntity.getBukkitLivingEntity()); // Paper - Fix potions splash events
                }
            }
        }

        // Paper start - Fix potions splash events
        io.papermc.paper.event.entity.WaterBottleSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callWaterBottleSplashEvent(
            this, result, affected, rehydrate, extinguish
        );
        if (!event.isCancelled()) {
            for (org.bukkit.entity.LivingEntity affectedEntity : event.getToDamage()) {
                ((org.bukkit.craftbukkit.entity.CraftLivingEntity) affectedEntity).getHandle().hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()), 1.0F);
            }
            for (org.bukkit.entity.LivingEntity toExtinguish : event.getToExtinguish()) {
                ((org.bukkit.craftbukkit.entity.CraftLivingEntity) toExtinguish).getHandle().extinguishFire();
            }
            for (org.bukkit.entity.LivingEntity toRehydrate : event.getToRehydrate()) {
                if (((org.bukkit.craftbukkit.entity.CraftLivingEntity) toRehydrate).getHandle() instanceof Axolotl axolotl) {
                    axolotl.rehydrate();
                }
            }
            // Paper end - Fix potions splash events
        }
        return !event.isCancelled(); // Paper - Fix potions splash events
    }

    protected abstract boolean onHitAsPotion(ServerLevel level, ItemStack stack, HitResult hitResult); // Paper - Fix potions splash events & More Projectile API

    private void dowseFire(BlockPos pos) {
        BlockState blockState = this.level().getBlockState(pos);
        if (blockState.is(BlockTags.FIRE)) {
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, pos, blockState.getFluidState().createLegacyBlock())) { // Paper - fix wrong block state
            this.level().destroyBlock(pos, false, this);
            } // CraftBukkit
        } else if (AbstractCandleBlock.isLit(blockState)) {
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, pos, blockState.setValue(AbstractCandleBlock.LIT, false))) { // CraftBukkit
            AbstractCandleBlock.extinguish(null, blockState, this.level(), pos);
            } // CraftBukkit
        } else if (CampfireBlock.isLitCampfire(blockState)) {
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, pos, blockState.setValue(CampfireBlock.LIT, false))) { // CraftBukkit
            this.level().levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0);
            CampfireBlock.dowse(this.getOwner(), this.level(), pos, blockState);
            this.level().setBlockAndUpdate(pos, blockState.setValue(CampfireBlock.LIT, false));
            } // CraftBukkit
        }
    }

    @Override
    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity entity, DamageSource damageSource) {
        double d = entity.position().x - this.position().x;
        double d1 = entity.position().z - this.position().z;
        return DoubleDoubleImmutablePair.of(d, d1);
    }
}
