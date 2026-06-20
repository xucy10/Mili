package net.minecraft.world.entity.vehicle.minecart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class MinecartTNT extends AbstractMinecart {
    private static final byte EVENT_PRIME = 10;
    private static final String TAG_EXPLOSION_POWER = "explosion_power";
    private static final String TAG_EXPLOSION_SPEED_FACTOR = "explosion_speed_factor";
    private static final String TAG_FUSE = "fuse";
    private static final float DEFAULT_EXPLOSION_POWER_BASE = 4.0F;
    private static final float DEFAULT_EXPLOSION_SPEED_FACTOR = 1.0F;
    private static final int NO_FUSE = -1;
    private @Nullable DamageSource ignitionSource;
    public int fuse = -1;
    public float explosionPowerBase = 4.0F;
    public float explosionSpeedFactor = 1.0F;
    public boolean isIncendiary = false; // CraftBukkit

    public MinecartTNT(EntityType<? extends MinecartTNT> type, Level level) {
        super(type, level);
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.TNT.defaultBlockState();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fuse > 0) {
            // Paper start - Configurable TNT height nerf
            if (this.level().paperConfig().fixes.tntEntityHeightNerf.test(v -> this.getY() > v)) {
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.OUT_OF_WORLD);
                return;
            }
            // Paper end - Configurable TNT height nerf
            this.fuse--;
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
        } else if (this.fuse == 0) {
            this.explode(this.ignitionSource, this.getDeltaMovement().horizontalDistanceSqr());
        }

        if (this.horizontalCollision) {
            double d = this.getDeltaMovement().horizontalDistanceSqr();
            if (d >= 0.01F) {
                this.explode(this.ignitionSource, d);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (damageSource.getDirectEntity() instanceof AbstractArrow abstractArrow && abstractArrow.isOnFire()) {
            DamageSource damageSource1 = this.damageSources().explosion(this, damageSource.getEntity());
            this.explode(damageSource1, abstractArrow.getDeltaMovement().lengthSqr());
        }

        return super.hurtServer(level, damageSource, amount);
    }

    @Override
    public void destroy(ServerLevel level, DamageSource damageSource) {
        double d = this.getDeltaMovement().horizontalDistanceSqr();
        if (!damageSourceIgnitesTnt(damageSource) && !(d >= 0.01F)) {
            this.destroy(level, this.getDropItem());
        } else {
            if (this.fuse < 0) {
                this.primeFuse(damageSource);
                this.fuse = this.random.nextInt(20) + this.random.nextInt(20);
            }
        }
    }

    @Override
    public Item getDropItem() {
        return Items.TNT_MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.TNT_MINECART);
    }

    public void explode(@Nullable DamageSource damageSource, double radiusModifier) {
        if (this.level() instanceof ServerLevel serverLevel) {
            if (serverLevel.getGameRules().get(GameRules.TNT_EXPLODES)) {
                double min = Math.min(Math.sqrt(radiusModifier), 5.0);
                // CraftBukkit start
                org.bukkit.event.entity.ExplosionPrimeEvent event = new org.bukkit.event.entity.ExplosionPrimeEvent(
                    this.getBukkitEntity(),
                    (float) (this.explosionPowerBase + this.explosionSpeedFactor * this.random.nextDouble() * 1.5 * min),
                    this.isIncendiary
                );
                if (!event.callEvent()) {
                    this.fuse = -1;
                    return;
                }
                // CraftBukkit end
                serverLevel.explode(
                    this,
                    damageSource,
                    null,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    event.getRadius(), // CraftBukkit - explosion prime event
                    event.getFire(), // CraftBukkit - explosion prime event
                    Level.ExplosionInteraction.TNT
                );
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
            } else if (this.isPrimed()) {
                this.discard(null); // CraftBukkit
            }
        }
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
        if (fallDistance >= 3.0) {
            double d = fallDistance / 10.0;
            this.explode(this.ignitionSource, d * d);
        }

        return super.causeFallDamage(fallDistance, damageMultiplier, damageSource);
    }

    @Override
    public void activateMinecart(ServerLevel level, int x, int y, int z, boolean receivingPower) {
        if (receivingPower && this.fuse < 0) {
            this.primeFuse(null);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.EAT_GRASS) {
            this.primeFuse(null);
        } else {
            super.handleEntityEvent(id);
        }
    }

    public void primeFuse(@Nullable DamageSource damageSource) {
        if (!(this.level() instanceof ServerLevel serverLevel && !serverLevel.getGameRules().get(GameRules.TNT_EXPLODES))) {
            this.fuse = 80;
            if (!this.level().isClientSide()) {
                if (damageSource != null && this.ignitionSource == null) {
                    this.ignitionSource = this.damageSources().explosion(this, damageSource.getEntity());
                }

                this.level().broadcastEntityEvent(this, EntityEvent.EAT_GRASS);
                if (!this.isSilent()) {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
        }
    }

    public int getFuse() {
        return this.fuse;
    }

    public boolean isPrimed() {
        return this.fuse > -1;
    }

    @Override
    public float getBlockExplosionResistance(
        Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState fluidState, float explosionPower
    ) {
        return !this.isPrimed() || !state.is(BlockTags.RAILS) && !level.getBlockState(pos.above()).is(BlockTags.RAILS)
            ? super.getBlockExplosionResistance(explosion, level, pos, state, fluidState, explosionPower)
            : 0.0F;
    }

    @Override
    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float explosionPower) {
        return (!this.isPrimed() || !state.is(BlockTags.RAILS) && !level.getBlockState(pos.above()).is(BlockTags.RAILS))
            && super.shouldBlockExplode(explosion, level, pos, state, explosionPower);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.fuse = input.getIntOr("fuse", -1);
        this.explosionPowerBase = Mth.clamp(input.getFloatOr("explosion_power", 4.0F), 0.0F, 128.0F);
        this.explosionSpeedFactor = Mth.clamp(input.getFloatOr("explosion_speed_factor", 1.0F), 0.0F, 128.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("fuse", this.fuse);
        if (this.explosionPowerBase != 4.0F) {
            output.putFloat("explosion_power", this.explosionPowerBase);
        }

        if (this.explosionSpeedFactor != 1.0F) {
            output.putFloat("explosion_speed_factor", this.explosionSpeedFactor);
        }
    }

    @Override
    protected boolean shouldSourceDestroy(DamageSource damageSource) {
        return damageSourceIgnitesTnt(damageSource);
    }

    private static boolean damageSourceIgnitesTnt(DamageSource damageSource) {
        return damageSource.getDirectEntity() instanceof Projectile projectile
            ? projectile.isOnFire()
            : damageSource.is(DamageTypeTags.IS_FIRE) || damageSource.is(DamageTypeTags.IS_EXPLOSION);
    }
}
