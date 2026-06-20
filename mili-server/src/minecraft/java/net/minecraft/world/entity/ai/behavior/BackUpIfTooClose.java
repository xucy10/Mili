package net.minecraft.world.entity.ai.behavior;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class BackUpIfTooClose {
    public static OneShot<Mob> create(int tooCloseDistance, float strafeSpeed) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.absent(MemoryModuleType.WALK_TARGET),
                    instance.registered(MemoryModuleType.LOOK_TARGET),
                    instance.present(MemoryModuleType.ATTACK_TARGET),
                    instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                )
                .apply(
                    instance,
                    (walkTarget, lookTarget, attackTarget, nearestVisibleLivingEntities) -> (level, mob, gameTime) -> {
                        LivingEntity livingEntity = instance.get(attackTarget);
                        if (livingEntity.closerThan(mob, tooCloseDistance)
                            && instance.<NearestVisibleLivingEntities>get(nearestVisibleLivingEntities).contains(livingEntity)) {
                            lookTarget.set(new EntityTracker(livingEntity, true));
                            mob.getMoveControl().strafe(-strafeSpeed, 0.0F);
                            mob.setYRot(Mth.rotateIfNecessary(mob.getYRot(), mob.yHeadRot, 0.0F));
                            return true;
                        } else {
                            return false;
                        }
                    }
                )
        );
    }
}
