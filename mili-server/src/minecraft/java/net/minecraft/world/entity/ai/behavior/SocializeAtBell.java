package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class SocializeAtBell {
    private static final float SPEED_MODIFIER = 0.3F;

    public static OneShot<LivingEntity> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.registered(MemoryModuleType.WALK_TARGET),
                    instance.registered(MemoryModuleType.LOOK_TARGET),
                    instance.present(MemoryModuleType.MEETING_POINT),
                    instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES),
                    instance.absent(MemoryModuleType.INTERACTION_TARGET)
                )
                .apply(
                    instance,
                    (walkTarget, lookTarget, meetingPoint, nearestVisibleLivingEntities, interactionTarget) -> (level, entity, gameTime) -> {
                        GlobalPos globalPos = instance.get(meetingPoint);
                        NearestVisibleLivingEntities nearestVisibleLivingEntities1 = instance.get(nearestVisibleLivingEntities);
                        if (level.getRandom().nextInt(100) == 0
                            && level.dimension() == globalPos.dimension()
                            && globalPos.pos().closerToCenterThan(entity.position(), 4.0)
                            && nearestVisibleLivingEntities1.contains(nearEntity -> EntityType.VILLAGER.equals(nearEntity.getType()))) {
                            nearestVisibleLivingEntities1.findClosest(
                                    nearEntity -> EntityType.VILLAGER.equals(nearEntity.getType()) && nearEntity.distanceToSqr(entity) <= 32.0
                                )
                                .ifPresent(nearEntity -> {
                                    interactionTarget.set(nearEntity);
                                    lookTarget.set(new EntityTracker(nearEntity, true));
                                    walkTarget.set(new WalkTarget(new EntityTracker(nearEntity, false), 0.3F, 1));
                                });
                            return true;
                        } else {
                            return false;
                        }
                    }
                )
        );
    }
}
