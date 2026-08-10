package net.minecraft.world.entity.monster.skeleton;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class Parched extends AbstractSkeleton {
    public Parched(EntityType<? extends AbstractSkeleton> type, Level level) {
        super(type, level);
    }

    @Override
    protected AbstractArrow getArrow(ItemStack arrow, float velocity, @Nullable ItemStack weapon) {
        AbstractArrow abstractArrow = super.getArrow(arrow, velocity, weapon);
        if (abstractArrow instanceof Arrow) {
            ((Arrow)abstractArrow).addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600));
        }

        return abstractArrow;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes().add(Attributes.MAX_HEALTH, 16.0);
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.PARCHED_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PARCHED_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PARCHED_DEATH;
    }

    @Override
    SoundEvent getStepSound() {
        return SoundEvents.PARCHED_STEP;
    }

    @Override
    protected int getHardAttackInterval() {
        return 50;
    }

    @Override
    protected int getAttackInterval() {
        return 70;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        return effectInstance.getEffect() != MobEffects.WEAKNESS && super.canBeAffected(effectInstance);
    }
}
