package net.minecraft.world.entity.ai.behavior.warden;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;

public class SetRoarTarget {
    public static <E extends Warden> BehaviorControl<E> create(Function<E, Optional<? extends LivingEntity>> targetFinder) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.absent(MemoryModuleType.ROAR_TARGET),
                    instance.absent(MemoryModuleType.ATTACK_TARGET),
                    instance.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                )
                .apply(instance, (roarTarget, attackTarget, cantReachWalkTargetSince) -> (level, warden, gameTime) -> {
                    Optional<? extends LivingEntity> optional = targetFinder.apply(warden);
                    if (optional.filter(warden::canTargetEntity).isEmpty()) {
                        return false;
                    } else {
                        roarTarget.set(optional.get());
                        cantReachWalkTargetSince.erase();
                        return true;
                    }
                })
        );
    }
}
