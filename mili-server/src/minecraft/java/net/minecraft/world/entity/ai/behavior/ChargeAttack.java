package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

public class ChargeAttack extends Behavior<Animal> {
    private final int timeBetweenAttacks;
    private final TargetingConditions chargeTargeting;
    private final float speed;
    private final float knockbackForce;
    private final double maxTargetDetectionDistance;
    private final double maxChargeDistance;
    private final SoundEvent chargeSound;
    private Vec3 chargeVelocityVector;
    private Vec3 startPosition;

    public ChargeAttack(
        int timeBetweenAttacks,
        TargetingConditions chargeTargeting,
        float speed,
        float knockbackForce,
        double maxChargeDistance,
        double maxTargetDetectionDistance,
        SoundEvent chargeSound
    ) {
        super(ImmutableMap.of(MemoryModuleType.CHARGE_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
        this.timeBetweenAttacks = timeBetweenAttacks;
        this.chargeTargeting = chargeTargeting;
        this.speed = speed;
        this.knockbackForce = knockbackForce;
        this.maxChargeDistance = maxChargeDistance;
        this.maxTargetDetectionDistance = maxTargetDetectionDistance;
        this.chargeSound = chargeSound;
        this.chargeVelocityVector = Vec3.ZERO;
        this.startPosition = Vec3.ZERO;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Animal owner) {
        return owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Animal entity, long gameTime) {
        Brain<?> brain = entity.getBrain();
        Optional<LivingEntity> memory = brain.getMemory(MemoryModuleType.ATTACK_TARGET);
        if (memory.isEmpty()) {
            return false;
        } else {
            LivingEntity livingEntity = memory.get();
            return !(entity instanceof TamableAnimal tamableAnimal && tamableAnimal.isTame())
                && !(entity.position().subtract(this.startPosition).lengthSqr() >= this.maxChargeDistance * this.maxChargeDistance)
                && !(livingEntity.position().subtract(entity.position()).lengthSqr() >= this.maxTargetDetectionDistance * this.maxTargetDetectionDistance)
                && entity.hasLineOfSight(livingEntity)
                && !brain.hasMemoryValue(MemoryModuleType.CHARGE_COOLDOWN_TICKS);
        }
    }

    @Override
    protected void start(ServerLevel level, Animal entity, long gameTime) {
        Brain<?> brain = entity.getBrain();
        this.startPosition = entity.position();
        LivingEntity livingEntity = brain.getMemory(MemoryModuleType.ATTACK_TARGET).get();
        Vec3 vec3 = livingEntity.position().subtract(entity.position()).normalize();
        this.chargeVelocityVector = vec3.scale(this.speed);
        if (this.canStillUse(level, entity, gameTime)) {
            entity.playSound(this.chargeSound);
        }
    }

    @Override
    protected void tick(ServerLevel level, Animal owner, long gameTime) {
        Brain<?> brain = owner.getBrain();
        LivingEntity livingEntity = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElseThrow();
        owner.lookAt(livingEntity, 360.0F, 360.0F);
        owner.setDeltaMovement(this.chargeVelocityVector);
        List<LivingEntity> list = new ArrayList<>(1);
        level.getEntities(
            EntityTypeTest.forClass(LivingEntity.class),
            owner.getBoundingBox(),
            livingEntity2 -> this.chargeTargeting.test(level, owner, livingEntity2),
            list,
            1
        );
        if (!list.isEmpty()) {
            LivingEntity livingEntity1 = list.get(0);
            if (owner.hasPassenger(livingEntity1)) {
                return;
            }

            this.dealDamageToTarget(level, owner, livingEntity1);
            this.dealKnockBack(owner, livingEntity1);
            this.stop(level, owner, gameTime);
        }
    }

    private void dealDamageToTarget(ServerLevel level, Animal animal, LivingEntity target) {
        DamageSource damageSource = level.damageSources().mobAttack(animal);
        float f = (float)animal.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (target.hurtServer(level, damageSource, f)) {
            EnchantmentHelper.doPostAttackEffects(level, target, damageSource);
        }
    }

    private void dealKnockBack(Animal animal, LivingEntity target) {
        int i = animal.hasEffect(MobEffects.SPEED) ? animal.getEffect(MobEffects.SPEED).getAmplifier() + 1 : 0;
        int i1 = animal.hasEffect(MobEffects.SLOWNESS) ? animal.getEffect(MobEffects.SLOWNESS).getAmplifier() + 1 : 0;
        float f = 0.25F * (i - i1);
        float f1 = Mth.clamp(this.speed * (float)animal.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.2F, 2.0F) + f;
        animal.causeExtraKnockback(target, f1 * this.knockbackForce, animal.getDeltaMovement());
    }

    @Override
    protected void stop(ServerLevel level, Animal entity, long gameTime) {
        entity.getBrain().setMemory(MemoryModuleType.CHARGE_COOLDOWN_TICKS, this.timeBetweenAttacks);
        entity.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }
}
