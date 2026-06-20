package net.minecraft.world.entity.monster.breeze;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.item.ItemStack;

public class Shoot extends Behavior<Breeze> {
    private static final int ATTACK_RANGE_MAX_SQRT = 256;
    private static final int UNCERTAINTY_BASE = 5;
    private static final int UNCERTAINTY_MULTIPLIER = 4;
    private static final float PROJECTILE_MOVEMENT_SCALE = 0.7F;
    private static final int SHOOT_INITIAL_DELAY_TICKS = Math.round(15.0F);
    private static final int SHOOT_RECOVER_DELAY_TICKS = Math.round(4.0F);
    private static final int SHOOT_COOLDOWN_TICKS = Math.round(10.0F);

    @VisibleForTesting
    public Shoot() {
        super(
            ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET,
                MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.BREEZE_SHOOT_COOLDOWN,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.BREEZE_SHOOT_CHARGING,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.BREEZE_SHOOT_RECOVERING,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.BREEZE_SHOOT,
                MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.BREEZE_JUMP_TARGET,
                MemoryStatus.VALUE_ABSENT
            ),
            SHOOT_INITIAL_DELAY_TICKS + 1 + SHOOT_RECOVER_DELAY_TICKS
        );
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Breeze owner) {
        return owner.getPose() == Pose.STANDING
            && owner.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).map(livingEntity -> isTargetWithinRange(owner, livingEntity)).map(_boolean -> {
                if (!_boolean) {
                    owner.getBrain().eraseMemory(MemoryModuleType.BREEZE_SHOOT);
                }

                return (Boolean)_boolean;
            }).orElse(false);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Breeze entity, long gameTime) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && entity.getBrain().hasMemoryValue(MemoryModuleType.BREEZE_SHOOT);
    }

    @Override
    protected void start(ServerLevel level, Breeze entity, long gameTime) {
        entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(livingEntity -> entity.setPose(Pose.SHOOTING));
        entity.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT_CHARGING, Unit.INSTANCE, SHOOT_INITIAL_DELAY_TICKS);
        entity.playSound(SoundEvents.BREEZE_INHALE, 1.0F, 1.0F);
    }

    @Override
    protected void stop(ServerLevel level, Breeze entity, long gameTime) {
        if (entity.getPose() == Pose.SHOOTING) {
            entity.setPose(Pose.STANDING);
        }

        entity.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT_COOLDOWN, Unit.INSTANCE, SHOOT_COOLDOWN_TICKS);
        entity.getBrain().eraseMemory(MemoryModuleType.BREEZE_SHOOT);
    }

    @Override
    protected void tick(ServerLevel level, Breeze owner, long gameTime) {
        Brain<Breeze> brain = owner.getBrain();
        LivingEntity livingEntity = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (livingEntity != null) {
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, livingEntity.position());
            if (!brain.getMemory(MemoryModuleType.BREEZE_SHOOT_CHARGING).isPresent() && !brain.getMemory(MemoryModuleType.BREEZE_SHOOT_RECOVERING).isPresent()) {
                brain.setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT_RECOVERING, Unit.INSTANCE, SHOOT_RECOVER_DELAY_TICKS);
                double d = livingEntity.getX() - owner.getX();
                double d1 = livingEntity.getY(livingEntity.isPassenger() ? 0.8 : 0.3) - owner.getFiringYPosition();
                double d2 = livingEntity.getZ() - owner.getZ();
                Projectile.spawnProjectileUsingShoot(
                    new BreezeWindCharge(owner, level), level, ItemStack.EMPTY, d, d1, d2, 0.7F, 5 - level.getDifficulty().getId() * 4
                );
                owner.playSound(SoundEvents.BREEZE_SHOOT, 1.5F, 1.0F);
            }
        }
    }

    private static boolean isTargetWithinRange(Breeze breeze, LivingEntity target) {
        double d = breeze.position().distanceToSqr(target.position());
        return d < 256.0;
    }
}
