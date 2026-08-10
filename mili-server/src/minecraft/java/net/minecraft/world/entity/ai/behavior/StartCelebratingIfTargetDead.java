package net.minecraft.world.entity.ai.behavior;

import java.util.function.BiPredicate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.gamerules.GameRules;

public class StartCelebratingIfTargetDead {
    public static BehaviorControl<LivingEntity> create(int duration, BiPredicate<LivingEntity, LivingEntity> canDance) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.present(MemoryModuleType.ATTACK_TARGET),
                    instance.registered(MemoryModuleType.ANGRY_AT),
                    instance.absent(MemoryModuleType.CELEBRATE_LOCATION),
                    instance.registered(MemoryModuleType.DANCING)
                )
                .apply(instance, (attackTarget, angryAt, celebrateLocation, dancing) -> (level, entity, gameTime) -> {
                    LivingEntity livingEntity = instance.get(attackTarget);
                    if (!livingEntity.isDeadOrDying()) {
                        return false;
                    } else {
                        if (canDance.test(entity, livingEntity)) {
                            dancing.setWithExpiry(true, duration);
                        }

                        celebrateLocation.setWithExpiry(livingEntity.blockPosition(), duration);
                        if (livingEntity.getType() != EntityType.PLAYER || level.getGameRules().get(GameRules.FORGIVE_DEAD_PLAYERS)) {
                            attackTarget.erase();
                            angryAt.erase();
                        }

                        return true;
                    }
                })
        );
    }
}
