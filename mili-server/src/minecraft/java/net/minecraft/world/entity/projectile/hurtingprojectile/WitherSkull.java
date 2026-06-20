package net.minecraft.world.entity.projectile.hurtingprojectile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class WitherSkull extends AbstractHurtingProjectile {
    private static final EntityDataAccessor<Boolean> DATA_DANGEROUS = SynchedEntityData.defineId(WitherSkull.class, EntityDataSerializers.BOOLEAN);
    private static final boolean DEFAULT_DANGEROUS = false;

    public WitherSkull(EntityType<? extends WitherSkull> type, Level level) {
        super(type, level);
    }

    public WitherSkull(Level level, LivingEntity owner, Vec3 movement) {
        super(EntityType.WITHER_SKULL, owner, movement, level);
    }

    @Override
    protected float getInertia() {
        return this.isDangerous() ? 0.73F : super.getInertia();
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public float getBlockExplosionResistance(
        Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState fluidState, float explosionPower
    ) {
        return this.isDangerous() && WitherBoss.canDestroy(state) ? Math.min(0.8F, explosionPower) : explosionPower;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            Entity var8 = result.getEntity();
            boolean flag;
            if (this.getOwner() instanceof LivingEntity livingEntity) {
                DamageSource damageSource = this.damageSources().witherSkull(this, livingEntity);
                flag = var8.hurtServer(serverLevel, damageSource, 8.0F);
                if (flag) {
                    if (var8.isAlive()) {
                        EnchantmentHelper.doPostAttackEffects(serverLevel, var8, damageSource);
                    } else {
                        livingEntity.heal(5.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.WITHER); // CraftBukkit
                    }
                }
            } else {
                flag = var8.hurtServer(serverLevel, this.damageSources().magic().eventEntityDamager(this), 5.0F); // Paper - Fire EntityDamageByEntityEvent for unowned wither skulls // Paper - fix DamageSource API
            }

            if (flag && var8 instanceof LivingEntity livingEntityx) {
                int i = 0;
                if (this.level().getDifficulty() == Difficulty.NORMAL) {
                    i = 10;
                } else if (this.level().getDifficulty() == Difficulty.HARD) {
                    i = 40;
                }

                if (i > 0) {
                    livingEntityx.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * i, 1), this.getEffectSource(), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
                }
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide()) {
            // CraftBukkit start
            org.bukkit.event.entity.ExplosionPrimeEvent event = new org.bukkit.event.entity.ExplosionPrimeEvent(this.getBukkitEntity(), 1.0F, false);
            if (event.callEvent()) {
                this.level().explode(this, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), Level.ExplosionInteraction.MOB);
            }
            // CraftBukkit end
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DANGEROUS, false);
    }

    public boolean isDangerous() {
        return this.entityData.get(DATA_DANGEROUS);
    }

    public void setDangerous(boolean invulnerable) {
        this.entityData.set(DATA_DANGEROUS, invulnerable);
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("dangerous", this.isDangerous());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setDangerous(input.getBooleanOr("dangerous", false));
    }
}
