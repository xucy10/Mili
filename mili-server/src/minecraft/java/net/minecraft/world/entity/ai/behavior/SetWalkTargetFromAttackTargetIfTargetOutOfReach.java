package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class SetWalkTargetFromAttackTargetIfTargetOutOfReach {
    private static final int PROJECTILE_ATTACK_RANGE_BUFFER = 1;

    public static BehaviorControl<Mob> create(float speedModifier) {
        return create(entity -> speedModifier);
    }

    public static BehaviorControl<Mob> create(Function<LivingEntity, Float> speedModifier) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.registered(MemoryModuleType.WALK_TARGET),
                    instance.registered(MemoryModuleType.LOOK_TARGET),
                    instance.present(MemoryModuleType.ATTACK_TARGET),
                    instance.registered(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                )
                .apply(instance, (walkTarget, lookTarget, attackTarget, nearestVisibleLivingEntities) -> (level, mob, gameTime) -> {
                    LivingEntity livingEntity = instance.get(attackTarget);
                    Optional<NearestVisibleLivingEntities> optional = instance.tryGet(nearestVisibleLivingEntities);
                    if (optional.isPresent() && optional.get().contains(livingEntity) && BehaviorUtils.isWithinAttackRange(mob, livingEntity, 1)) {
                        walkTarget.erase();
                    } else {
                        lookTarget.set(new EntityTracker(livingEntity, true));
                        walkTarget.set(new WalkTarget(new EntityTracker(livingEntity, false), speedModifier.apply(mob), 0));
                    }

                    return true;
                })
        );
    }
}
