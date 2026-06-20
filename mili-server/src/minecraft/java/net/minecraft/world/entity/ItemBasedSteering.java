package net.minecraft.world.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class ItemBasedSteering {
    private static final int MIN_BOOST_TIME = 140;
    private static final int MAX_BOOST_TIME = 700;
    private final SynchedEntityData entityData;
    private final EntityDataAccessor<Integer> boostTimeAccessor;
    public boolean boosting;
    public int boostTime;

    public ItemBasedSteering(SynchedEntityData entityData, EntityDataAccessor<Integer> boostTimeAccessor) {
        this.entityData = entityData;
        this.boostTimeAccessor = boostTimeAccessor;
    }

    public void onSynced() {
        this.boosting = true;
        this.boostTime = 0;
    }

    public boolean boost(RandomSource random) {
        if (this.boosting) {
            return false;
        } else {
            this.boosting = true;
            this.boostTime = 0;
            this.entityData.set(this.boostTimeAccessor, random.nextInt(841) + 140);
            return true;
        }
    }

    public void tickBoost() {
        if (this.boosting && this.boostTime++ > this.boostTimeTotal()) {
            this.boosting = false;
        }
    }

    public float boostFactor() {
        return this.boosting ? 1.0F + 1.15F * Mth.sin((float)this.boostTime / this.boostTimeTotal() * (float) Math.PI) : 1.0F;
    }

    public int boostTimeTotal() {
        return this.entityData.get(this.boostTimeAccessor);
    }

    // CraftBukkit start
    public void setBoostTicks(int ticks) {
        this.boosting = true;
        this.boostTime = 0;
        this.entityData.set(this.boostTimeAccessor, ticks);
    }
    // CraftBukkit end
}
