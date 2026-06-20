package net.minecraft.world.entity.animal.squid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class GlowSquid extends Squid {
    private static final EntityDataAccessor<Integer> DATA_DARK_TICKS_REMAINING = SynchedEntityData.defineId(GlowSquid.class, EntityDataSerializers.INT);
    private static final int DEFAULT_DARK_TICKS_REMAINING = 0;

    public GlowSquid(EntityType<? extends GlowSquid> type, Level level) {
        super(type, level);
    }

    @Override
    protected ParticleOptions getInkParticle() {
        return ParticleTypes.GLOW_SQUID_INK;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DARK_TICKS_REMAINING, 0);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityType.GLOW_SQUID.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    protected SoundEvent getSquirtSound() {
        return SoundEvents.GLOW_SQUID_SQUIRT;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.GLOW_SQUID_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.GLOW_SQUID_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.GLOW_SQUID_DEATH;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("DarkTicksRemaining", this.getDarkTicksRemaining());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setDarkTicks(input.getIntOr("DarkTicksRemaining", 0));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        int darkTicksRemaining = this.getDarkTicksRemaining();
        if (darkTicksRemaining > 0) {
            this.setDarkTicks(darkTicksRemaining - 1);
        }

        this.level().addParticle(ParticleTypes.GLOW, this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6), 0.0, 0.0, 0.0);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        boolean flag = super.hurtServer(level, damageSource, amount);
        if (flag) {
            this.setDarkTicks(100);
        }

        return flag;
    }

    public void setDarkTicks(int darkTicks) {
        this.entityData.set(DATA_DARK_TICKS_REMAINING, darkTicks);
    }

    public int getDarkTicksRemaining() {
        return this.entityData.get(DATA_DARK_TICKS_REMAINING);
    }

    public static boolean checkGlowSquidSpawnRules(
        EntityType<? extends LivingEntity> entityType, ServerLevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return pos.getY() <= level.getSeaLevel() - 33 && level.getRawBrightness(pos, 0) == 0 && level.getBlockState(pos).is(Blocks.WATER);
    }
}
