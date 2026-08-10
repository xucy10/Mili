package net.minecraft.world.entity.animal.equine;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class Donkey extends AbstractChestedHorse {
    public Donkey(EntityType<? extends Donkey> type, Level level) {
        super(type, level);
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.DONKEY_AMBIENT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.DONKEY_ANGRY;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.DONKEY_DEATH;
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.DONKEY_EAT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.DONKEY_HURT;
    }

    @Override
    public boolean canMate(Animal otherAnimal) {
        return otherAnimal != this
            && (otherAnimal instanceof Donkey || otherAnimal instanceof Horse)
            && this.canParent()
            && ((AbstractHorse)otherAnimal).canParent();
    }

    @Override
    protected void playJumpSound() {
        this.playSound(SoundEvents.DONKEY_JUMP, 0.4F, 1.0F);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        EntityType<? extends AbstractHorse> entityType = partner instanceof Horse ? EntityType.MULE : EntityType.DONKEY;
        AbstractHorse abstractHorse = entityType.create(level, EntitySpawnReason.BREEDING);
        if (abstractHorse != null) {
            this.setOffspringAttributes(partner, abstractHorse);
        }

        return abstractHorse;
    }
}
