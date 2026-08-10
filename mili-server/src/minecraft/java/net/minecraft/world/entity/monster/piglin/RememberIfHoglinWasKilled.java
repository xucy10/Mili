package net.minecraft.world.entity.monster.piglin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class RememberIfHoglinWasKilled {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.present(MemoryModuleType.ATTACK_TARGET), instance.registered(MemoryModuleType.HUNTED_RECENTLY))
                .apply(instance, (attackTarget, huntedRecently) -> (level, entity, gameTime) -> {
                    LivingEntity livingEntity = instance.get(attackTarget);
                    if (livingEntity.getType() == EntityType.HOGLIN && livingEntity.isDeadOrDying()) {
                        huntedRecently.setWithExpiry(true, PiglinAi.TIME_BETWEEN_HUNTS.sample(entity.level().random));
                    }

                    return true;
                })
        );
    }
}
