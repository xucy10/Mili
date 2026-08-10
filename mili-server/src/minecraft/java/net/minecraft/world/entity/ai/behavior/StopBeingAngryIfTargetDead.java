package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.gamerules.GameRules;

public class StopBeingAngryIfTargetDead {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.present(MemoryModuleType.ANGRY_AT))
                .apply(
                    instance,
                    angryAt -> (level, entity, gameTime) -> {
                        Optional.ofNullable(level.getEntity(instance.get(angryAt)))
                            .map(target -> target instanceof LivingEntity livingEntity ? livingEntity : null)
                            .filter(LivingEntity::isDeadOrDying)
                            .filter(target -> target.getType() != EntityType.PLAYER || level.getGameRules().get(GameRules.FORGIVE_DEAD_PLAYERS))
                            .ifPresent(target -> angryAt.erase());
                        return true;
                    }
                )
        );
    }
}
