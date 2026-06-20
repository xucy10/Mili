package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.schedule.Activity;

public class WakeUp {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(instance -> instance.point((level, entity, gameTime) -> {
            if (!entity.getBrain().isActive(Activity.REST) && entity.isSleeping()) {
                entity.stopSleeping();
                return true;
            } else {
                return false;
            }
        }));
    }
}
