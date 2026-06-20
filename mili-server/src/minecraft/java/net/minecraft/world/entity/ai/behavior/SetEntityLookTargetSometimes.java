package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

@Deprecated
public class SetEntityLookTargetSometimes {
    public static BehaviorControl<LivingEntity> create(float maxDist, UniformInt interval) {
        return create(maxDist, interval, entity -> true);
    }

    public static BehaviorControl<LivingEntity> create(EntityType<?> entityType, float maxDist, UniformInt interval) {
        return create(maxDist, interval, entity -> entityType.equals(entity.getType()));
    }

    private static BehaviorControl<LivingEntity> create(float maxDist, UniformInt interval, Predicate<LivingEntity> canLookAtTarget) {
        float f = maxDist * maxDist;
        SetEntityLookTargetSometimes.Ticker ticker = new SetEntityLookTargetSometimes.Ticker(interval);
        return BehaviorBuilder.create(
            instance -> instance.group(instance.absent(MemoryModuleType.LOOK_TARGET), instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES))
                .apply(
                    instance,
                    (lookTarget, nearestVisibleLivingEntities) -> (level, entity, gameTime) -> {
                        Optional<LivingEntity> optional = instance.<NearestVisibleLivingEntities>get(nearestVisibleLivingEntities)
                            .findClosest(canLookAtTarget.and(target -> target.distanceToSqr(entity) <= f));
                        if (optional.isEmpty()) {
                            return false;
                        } else if (!ticker.tickDownAndCheck(level.random)) {
                            return false;
                        } else {
                            lookTarget.set(new EntityTracker(optional.get(), true));
                            return true;
                        }
                    }
                )
        );
    }

    public static final class Ticker {
        private final UniformInt interval;
        private int ticksUntilNextStart;

        public Ticker(UniformInt interval) {
            if (interval.getMinValue() <= 1) {
                throw new IllegalArgumentException();
            } else {
                this.interval = interval;
            }
        }

        public boolean tickDownAndCheck(RandomSource random) {
            if (this.ticksUntilNextStart == 0) {
                this.ticksUntilNextStart = this.interval.sample(random) - 1;
                return false;
            } else {
                return --this.ticksUntilNextStart == 0;
            }
        }
    }
}
