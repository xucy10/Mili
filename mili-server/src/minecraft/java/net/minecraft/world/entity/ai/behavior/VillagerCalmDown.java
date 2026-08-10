package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class VillagerCalmDown {
    private static final int SAFE_DISTANCE_FROM_DANGER = 36;

    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.registered(MemoryModuleType.HURT_BY),
                    instance.registered(MemoryModuleType.HURT_BY_ENTITY),
                    instance.registered(MemoryModuleType.NEAREST_HOSTILE)
                )
                .apply(
                    instance,
                    (hurtBy, hurtByEntity, nearestHostile) -> (level, entity, gameTime) -> {
                        boolean flag = instance.tryGet(hurtBy).isPresent()
                            || instance.tryGet(nearestHostile).isPresent()
                            || instance.<LivingEntity>tryGet(hurtByEntity).filter(hurtingEntity -> hurtingEntity.distanceToSqr(entity) <= 36.0).isPresent();
                        if (!flag) {
                            hurtBy.erase();
                            hurtByEntity.erase();
                            entity.getBrain().updateActivityFromSchedule(level.environmentAttributes(), level.getGameTime(), entity.position());
                        }

                        return true;
                    }
                )
        );
    }
}
