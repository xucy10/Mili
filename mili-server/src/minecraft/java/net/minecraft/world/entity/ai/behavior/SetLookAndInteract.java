package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class SetLookAndInteract {
    public static BehaviorControl<LivingEntity> create(EntityType<?> entityType, int maxDist) {
        int i = maxDist * maxDist;
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.registered(MemoryModuleType.LOOK_TARGET),
                    instance.absent(MemoryModuleType.INTERACTION_TARGET),
                    instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                )
                .apply(
                    instance,
                    (lookTarget, interactionTarget, nearestVisibleLivingEntities) -> (level, entity, gameTime) -> {
                        Optional<LivingEntity> optional = instance.<NearestVisibleLivingEntities>get(nearestVisibleLivingEntities)
                            .findClosest(nearEntity -> nearEntity.distanceToSqr(entity) <= i && entityType.equals(nearEntity.getType()));
                        if (optional.isEmpty()) {
                            return false;
                        } else {
                            LivingEntity livingEntity = optional.get();
                            interactionTarget.set(livingEntity);
                            lookTarget.set(new EntityTracker(livingEntity, true));
                            return true;
                        }
                    }
                )
        );
    }
}
