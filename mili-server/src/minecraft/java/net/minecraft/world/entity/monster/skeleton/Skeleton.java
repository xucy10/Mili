package net.minecraft.world.entity.monster.skeleton;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class Skeleton extends AbstractSkeleton {
    private static final int TOTAL_CONVERSION_TIME = 300;
    public static final EntityDataAccessor<Boolean> DATA_STRAY_CONVERSION_ID = SynchedEntityData.defineId(Skeleton.class, EntityDataSerializers.BOOLEAN);
    public static final String CONVERSION_TAG = "StrayConversionTime";
    private static final int NOT_CONVERTING = -1;
    public int inPowderSnowTime;
    public int conversionTime;

    public Skeleton(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STRAY_CONVERSION_ID, false);
    }

    public boolean isFreezeConverting() {
        return this.getEntityData().get(DATA_STRAY_CONVERSION_ID);
    }

    public void setFreezeConverting(boolean isFrozen) {
        this.entityData.set(DATA_STRAY_CONVERSION_ID, isFrozen);
    }

    @Override
    public boolean isShaking() {
        return this.isFreezeConverting();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.isAlive() && !this.isNoAi()) {
            if (this.isInPowderSnow) {
                if (this.isFreezeConverting()) {
                    this.conversionTime--;
                    if (this.conversionTime < 0) {
                        this.doFreezeConversion();
                    }
                } else {
                    this.inPowderSnowTime++;
                    if (this.inPowderSnowTime >= 140) {
                        this.startFreezeConversion(300);
                    }
                }
            } else {
                this.inPowderSnowTime = -1;
                this.setFreezeConverting(false);
            }
        }

        super.tick();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("StrayConversionTime", this.isFreezeConverting() ? this.conversionTime : -1);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int intOr = input.getIntOr("StrayConversionTime", -1);
        if (intOr != -1) {
            this.startFreezeConversion(intOr);
        } else {
            this.setFreezeConverting(false);
        }
    }

    @VisibleForTesting
    public void startFreezeConversion(int conversionTime) {
        this.conversionTime = conversionTime;
        this.setFreezeConverting(true);
    }

    protected void doFreezeConversion() {
        final Stray stray = this.convertTo(EntityType.STRAY, ConversionParams.single(this, true, true), mob -> { // Paper - Fix issues with mob conversion; reset conversion time to prevent event spam
            if (!this.isSilent()) {
                this.level().levelEvent(null, LevelEvent.SOUND_SKELETON_TO_STRAY, this.blockPosition(), 0);
            }
        // Paper start - add spawn and transform reasons
        }, org.bukkit.event.entity.EntityTransformEvent.TransformReason.FROZEN, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.FROZEN);
        if (stray == null) {
            // Reset conversion time to prevent event spam
            this.conversionTime = 300;
        }
        // Paper end - add spawn and transform reasons
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    SoundEvent getStepSound() {
        return SoundEvents.SKELETON_STEP;
    }
}
