package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class SetEntityLookTarget {
    public static BehaviorControl<LivingEntity> create(MobCategory category, float maxDist) {
        return create(entity -> category.equals(entity.getType().getCategory()), maxDist);
    }

    public static OneShot<LivingEntity> create(EntityType<?> entityType, float maxDist) {
        return create(entity -> entityType.equals(entity.getType()), maxDist);
    }

    public static OneShot<LivingEntity> create(float maxDist) {
        return create(entity -> true, maxDist);
    }

    public static OneShot<LivingEntity> create(Predicate<LivingEntity> canLootAtTarget, float maxDist) {
        float f = maxDist * maxDist;
        return BehaviorBuilder.create(
            instance -> instance.group(instance.absent(MemoryModuleType.LOOK_TARGET), instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES))
                .apply(
                    instance,
                    (lookTarget, nearestVisibleLivingEntities) -> (level, entity, gameTime) -> {
                        Optional<LivingEntity> optional = instance.<NearestVisibleLivingEntities>get(nearestVisibleLivingEntities)
                            .findClosest(canLootAtTarget.and(target -> target.distanceToSqr(entity) <= f && !entity.hasPassenger(target)));
                        if (optional.isEmpty()) {
                            return false;
                        } else {
                            lookTarget.set(new EntityTracker(optional.get(), true));
                            return true;
                        }
                    }
                )
        );
    }
}
