package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class InteractWith {
    public static <T extends LivingEntity> BehaviorControl<LivingEntity> of(
        EntityType<? extends T> type, int interactionRange, MemoryModuleType<T> interactMemory, float speedModifier, int maxDist
    ) {
        return of(type, interactionRange, entity -> true, entity -> true, interactMemory, speedModifier, maxDist);
    }

    public static <E extends LivingEntity, T extends LivingEntity> BehaviorControl<E> of(
        EntityType<? extends T> type,
        int interactionRange,
        Predicate<E> selfFilter,
        Predicate<T> targetFilter,
        MemoryModuleType<T> memory,
        float speedModifier,
        int maxDist
    ) {
        int i = interactionRange * interactionRange;
        Predicate<LivingEntity> predicate = entity -> type.equals(entity.getType()) && targetFilter.test((T)entity);
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.registered(memory),
                    instance.registered(MemoryModuleType.LOOK_TARGET),
                    instance.absent(MemoryModuleType.WALK_TARGET),
                    instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                )
                .apply(
                    instance,
                    (interact, lookTarget, walkTarget, nearestVisibleLivingEntities) -> (level, entity, gameTime) -> {
                        NearestVisibleLivingEntities nearestVisibleLivingEntities1 = instance.get(nearestVisibleLivingEntities);
                        if (selfFilter.test(entity) && nearestVisibleLivingEntities1.contains(predicate)) {
                            Optional<LivingEntity> optional = nearestVisibleLivingEntities1.findClosest(
                                testEntity -> testEntity.distanceToSqr(entity) <= i && predicate.test(testEntity)
                            );
                            optional.ifPresent(target -> {
                                interact.set((T)target);
                                lookTarget.set(new EntityTracker(target, true));
                                walkTarget.set(new WalkTarget(new EntityTracker(target, false), speedModifier, maxDist));
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
