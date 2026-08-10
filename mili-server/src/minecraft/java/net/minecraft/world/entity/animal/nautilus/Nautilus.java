package net.minecraft.world.entity.animal.nautilus;

import com.mojang.serialization.Dynamic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class Nautilus extends AbstractNautilus {
    private static final int NAUTILUS_TOTAL_AIR_SUPPLY = 300;

    public Nautilus(EntityType<? extends Nautilus> type, Level level) {
        super(type, level);
    }

    @Override
    protected Brain.Provider<Nautilus> brainProvider() {
        return NautilusAi.brainProvider();
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return NautilusAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    public Brain<Nautilus> getBrain() {
        return (Brain<Nautilus>)super.getBrain();
    }

    @Override
    public @Nullable Nautilus getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Nautilus nautilus = EntityType.NAUTILUS.create(level, EntitySpawnReason.BREEDING);
        if (nautilus != null && this.isTame()) {
            nautilus.setOwnerReference(this.getOwnerReference());
            nautilus.setTame(true, true);
        }

        return nautilus;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("nautilusBrain");
        this.getBrain().tick(level, this);
        profilerFiller.pop();
        profilerFiller.push("nautilusActivityUpdate");
        NautilusAi.updateActivity(this);
        profilerFiller.pop();
        super.customServerAiStep(level);
    }

    @Override
    public SoundEvent getAmbientSound() {
        if (this.isBaby()) {
            return this.isUnderWater() ? SoundEvents.BABY_NAUTILUS_AMBIENT : SoundEvents.BABY_NAUTILUS_AMBIENT_ON_LAND;
        } else {
            return this.isUnderWater() ? SoundEvents.NAUTILUS_AMBIENT : SoundEvents.NAUTILUS_AMBIENT_ON_LAND;
        }
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        if (this.isBaby()) {
            return this.isUnderWater() ? SoundEvents.BABY_NAUTILUS_HURT : SoundEvents.BABY_NAUTILUS_HURT_ON_LAND;
        } else {
            return this.isUnderWater() ? SoundEvents.NAUTILUS_HURT : SoundEvents.NAUTILUS_HURT_ON_LAND;
        }
    }

    @Override
    public SoundEvent getDeathSound() {
        if (this.isBaby()) {
            return this.isUnderWater() ? SoundEvents.BABY_NAUTILUS_DEATH : SoundEvents.BABY_NAUTILUS_DEATH_ON_LAND;
        } else {
            return this.isUnderWater() ? SoundEvents.NAUTILUS_DEATH : SoundEvents.NAUTILUS_DEATH_ON_LAND;
        }
    }

    @Override
    protected SoundEvent getDashSound() {
        return this.isUnderWater() ? SoundEvents.NAUTILUS_DASH : SoundEvents.NAUTILUS_DASH_ON_LAND;
    }

    @Override
    protected SoundEvent getDashReadySound() {
        return this.isUnderWater() ? SoundEvents.NAUTILUS_DASH_READY : SoundEvents.NAUTILUS_DASH_READY_ON_LAND;
    }

    @Override
    protected void playEatingSound() {
        SoundEvent soundEvent = this.isBaby() ? SoundEvents.BABY_NAUTILUS_EAT : SoundEvents.NAUTILUS_EAT;
        this.makeSound(soundEvent);
    }

    @Override
    public SoundEvent getSwimSound() {
        return this.isBaby() ? SoundEvents.BABY_NAUTILUS_SWIM : SoundEvents.NAUTILUS_SWIM;
    }

    @Override
    public int getMaxAirSupply() {
        return this.maxAirTicks; // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    }
    // CraftBukkit start - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    @Override
    public int getDefaultMaxAirSupply() {
        return NAUTILUS_TOTAL_AIR_SUPPLY;
    }
    // CraftBukkit end

    protected void handleAirSupply(ServerLevel level, int airSupply) {
        if (this.isAlive() && !this.isInWater()) {
            this.setAirSupply(airSupply - 1);
            if (this.getAirSupply() <= -20) {
                this.setAirSupply(0);
                this.hurtServer(level, this.damageSources().dryOut(), 2.0F);
            }
        } else {
            this.setAirSupply(this.maxAirTicks); // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
        }
    }

    @Override
    public void baseTick() {
        int airSupply = this.getAirSupply();
        super.baseTick();
        if (!this.isNoAi() && this.level() instanceof ServerLevel serverLevel) {
            this.handleAirSupply(serverLevel, airSupply);
        }
    }

    @Override
    public boolean canBeLeashed() {
        return !this.isAggravated();
    }
}
