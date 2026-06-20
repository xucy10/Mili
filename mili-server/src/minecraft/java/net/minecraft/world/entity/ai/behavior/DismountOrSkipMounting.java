package net.minecraft.world.entity.ai.behavior;

import java.util.function.BiPredicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class DismountOrSkipMounting {
    public static <E extends LivingEntity> BehaviorControl<E> create(int maxDistanceFromVehicle, BiPredicate<E, Entity> shouldStopRiding) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.registered(MemoryModuleType.RIDE_TARGET)).apply(instance, rideTarget -> (level, entity, gameTime) -> {
                Entity vehicle = entity.getVehicle();
                Entity entity1 = instance.<Entity>tryGet(rideTarget).orElse(null);
                if (vehicle == null && entity1 == null) {
                    return false;
                } else {
                    Entity entity2 = vehicle == null ? entity1 : vehicle;
                    if (isVehicleValid(entity, entity2, maxDistanceFromVehicle) && !shouldStopRiding.test(entity, entity2)) {
                        return false;
                    } else {
                        entity.stopRiding();
                        rideTarget.erase();
                        return true;
                    }
                }
            })
        );
    }

    private static boolean isVehicleValid(LivingEntity entity, Entity vehicle, int distance) {
        return vehicle.isAlive() && vehicle.closerThan(entity, distance) && vehicle.level() == entity.level();
    }
}
