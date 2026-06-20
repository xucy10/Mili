package net.minecraft.world.entity.ai.behavior;

import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class SetWalkTargetFromLookTarget {
    public static OneShot<LivingEntity> create(float speedModifier, int closeEnoughDist) {
        return create(entity -> true, entity -> speedModifier, closeEnoughDist);
    }

    public static OneShot<LivingEntity> create(Predicate<LivingEntity> canSetWalkTarget, Function<LivingEntity, Float> speedModifier, int closeEnoughDist) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.absent(MemoryModuleType.WALK_TARGET), instance.present(MemoryModuleType.LOOK_TARGET))
                .apply(instance, (walkTarget, lookTarget) -> (level, entity, gameTime) -> {
                    if (!canSetWalkTarget.test(entity)) {
                        return false;
                    } else {
                        walkTarget.set(new WalkTarget(instance.get(lookTarget), speedModifier.apply(entity), closeEnoughDist));
                        return true;
                    }
                })
        );
    }
}
