package net.minecraft.world.entity.ai.behavior;

import java.util.function.Function;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class BabyFollowAdult {
    public static OneShot<LivingEntity> create(UniformInt followRange, float speedModifier) {
        return create(followRange, entity -> speedModifier, MemoryModuleType.NEAREST_VISIBLE_ADULT, false);
    }

    public static OneShot<LivingEntity> create(
        UniformInt followRange,
        Function<LivingEntity, Float> speedModifier,
        MemoryModuleType<? extends LivingEntity> nearestVisibleAdult,
        boolean targetEyeHeight
    ) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.present(nearestVisibleAdult), instance.registered(MemoryModuleType.LOOK_TARGET), instance.absent(MemoryModuleType.WALK_TARGET)
                )
                .apply(
                    instance,
                    (memoryAccessor, memoryAccessor1, memoryAccessor2) -> (level, entity, gameTime) -> {
                        if (!entity.isBaby()) {
                            return false;
                        } else {
                            LivingEntity livingEntity = instance.get(memoryAccessor);
                            if (entity.closerThan(livingEntity, followRange.getMaxValue() + 1) && !entity.closerThan(livingEntity, followRange.getMinValue())) {
                                // CraftBukkit start
                                org.bukkit.event.entity.EntityTargetLivingEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTargetLivingEvent(entity, livingEntity, org.bukkit.event.entity.EntityTargetEvent.TargetReason.FOLLOW_LEADER);
                                if (event.isCancelled()) {
                                    return false;
                                }
                                if (event.getTarget() == null) {
                                    memoryAccessor.erase();
                                    return true;
                                }
                                livingEntity = ((org.bukkit.craftbukkit.entity.CraftLivingEntity) event.getTarget()).getHandle();
                                // CraftBukkit end
                                WalkTarget walkTarget = new WalkTarget(
                                    new EntityTracker(livingEntity, targetEyeHeight, targetEyeHeight),
                                    speedModifier.apply(entity),
                                    followRange.getMinValue() - 1
                                );
                                memoryAccessor1.set(new EntityTracker(livingEntity, true, targetEyeHeight));
                                memoryAccessor2.set(walkTarget);
                                return true;
                            } else {
                                return false;
                            }
                        }
                    }
                )
        );
    }
}
