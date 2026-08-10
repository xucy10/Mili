package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class StayCloseToTarget {
    public static BehaviorControl<LivingEntity> create(
        Function<LivingEntity, Optional<PositionTracker>> targetPositionGetter,
        Predicate<LivingEntity> predicate,
        int closeEnoughDist,
        int tooClose,
        float speedModifier
    ) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.registered(MemoryModuleType.LOOK_TARGET), instance.registered(MemoryModuleType.WALK_TARGET))
                .apply(instance, (lookTarget, walkTarget) -> (level, entity, gameTime) -> {
                    Optional<PositionTracker> optional = targetPositionGetter.apply(entity);
                    if (!optional.isEmpty() && predicate.test(entity)) {
                        PositionTracker positionTracker = optional.get();
                        if (entity.position().closerThan(positionTracker.currentPosition(), tooClose)) {
                            return false;
                        } else {
                            PositionTracker positionTracker1 = optional.get();
                            lookTarget.set(positionTracker1);
                            walkTarget.set(new WalkTarget(positionTracker1, speedModifier, closeEnoughDist));
                            return true;
                        }
                    } else {
                        return false;
                    }
                })
        );
    }
}
