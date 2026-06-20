package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StopAttackingIfTargetInvalid {
    private static final int TIMEOUT_TO_GET_WITHIN_ATTACK_RANGE = 200;

    public static <E extends Mob> BehaviorControl<E> create(StopAttackingIfTargetInvalid.TargetErasedCallback<E> onStopAttacking) {
        return create((level, entity) -> false, onStopAttacking, true);
    }

    public static <E extends Mob> BehaviorControl<E> create(StopAttackingIfTargetInvalid.StopAttackCondition canStopAttacking) {
        return create(canStopAttacking, (level, entity, target) -> {}, true);
    }

    public static <E extends Mob> BehaviorControl<E> create() {
        return create((level, entity) -> false, (level, entity, target) -> {}, true);
    }

    public static <E extends Mob> BehaviorControl<E> create(
        StopAttackingIfTargetInvalid.StopAttackCondition canStopAttacking,
        StopAttackingIfTargetInvalid.TargetErasedCallback<E> onStopAttacking,
        boolean canGrowTiredOfTryingToReachTarget
    ) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.present(MemoryModuleType.ATTACK_TARGET), instance.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE))
                .apply(
                    instance,
                    (memoryAccessor, memoryAccessor1) -> (level, entity, l) -> {
                        LivingEntity livingEntity = instance.get(memoryAccessor);
                        if (entity.canAttack(livingEntity)
                            && (!canGrowTiredOfTryingToReachTarget || !isTiredOfTryingToReachTarget(entity, instance.tryGet(memoryAccessor1)))
                            && livingEntity.isAlive()
                            && livingEntity.level() == entity.level()
                            && !canStopAttacking.test(level, livingEntity)) {
                            return true;
                        } else {
                        // Paper start - better track target change reason
                        final org.bukkit.event.entity.EntityTargetEvent.TargetReason reason;
                        if (!entity.canAttack(livingEntity)) {
                            reason = org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_INVALID;
                        } else if (canGrowTiredOfTryingToReachTarget && StopAttackingIfTargetInvalid.isTiredOfTryingToReachTarget(entity, instance.tryGet(memoryAccessor1))) {
                            reason = org.bukkit.event.entity.EntityTargetEvent.TargetReason.FORGOT_TARGET;
                        } else if (!livingEntity.isAlive()) {
                            reason = org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_DIED;
                        } else if (livingEntity.level() != entity.level()) {
                            reason = org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_OTHER_LEVEL;
                        } else {
                            reason = org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_INVALID;
                        }
                        // Paper end
                        // CraftBukkit start
                        org.bukkit.event.entity.EntityTargetEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTargetLivingEvent(entity, null, reason); // Paper
                        if (event.isCancelled()) {
                            return false;
                        }
                        if (event.getTarget() != null) {
                            entity.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, ((org.bukkit.craftbukkit.entity.CraftLivingEntity) event.getTarget()).getHandle());
                            return true;
                        }
                        // CraftBukkit end
                            onStopAttacking.accept(level, entity, livingEntity);
                            memoryAccessor.erase();
                            return true;
                        }
                    }
                )
        );
    }

    private static boolean isTiredOfTryingToReachTarget(LivingEntity entity, Optional<Long> timeSinceInvalidTarget) {
        return timeSinceInvalidTarget.isPresent() && entity.level().getGameTime() - timeSinceInvalidTarget.get() > 200L;
    }

    @FunctionalInterface
    public interface StopAttackCondition {
        boolean test(ServerLevel level, LivingEntity entity);
    }

    @FunctionalInterface
    public interface TargetErasedCallback<E> {
        void accept(ServerLevel level, E entity, LivingEntity target);
    }
}
