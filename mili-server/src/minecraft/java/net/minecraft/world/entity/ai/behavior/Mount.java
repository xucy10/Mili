package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class Mount {
    private static final int CLOSE_ENOUGH_TO_START_RIDING_DIST = 1;

    public static BehaviorControl<LivingEntity> create(float speedModifier) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.registered(MemoryModuleType.LOOK_TARGET),
                    instance.absent(MemoryModuleType.WALK_TARGET),
                    instance.present(MemoryModuleType.RIDE_TARGET)
                )
                .apply(instance, (lookTarget, walkTarget, rideTarget) -> (level, entity, gameTime) -> {
                    if (entity.isPassenger()) {
                        return false;
                    } else {
                        Entity entity1 = instance.get(rideTarget);
                        if (entity1.closerThan(entity, 1.0)) {
                            entity.startRiding(entity1);
                        } else {
                            lookTarget.set(new EntityTracker(entity1, true));
                            walkTarget.set(new WalkTarget(new EntityTracker(entity1, false), speedModifier, 1));
                        }

                        return true;
                    }
                })
        );
    }
}
