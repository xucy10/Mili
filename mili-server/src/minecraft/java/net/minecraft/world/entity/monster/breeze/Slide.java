package net.minecraft.world.entity.monster.breeze;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

public class Slide extends Behavior<Breeze> {
    public Slide() {
        super(
            Map.of(
                MemoryModuleType.ATTACK_TARGET,
                MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.BREEZE_JUMP_COOLDOWN,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.BREEZE_SHOOT,
                MemoryStatus.VALUE_ABSENT
            )
        );
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Breeze owner) {
        return owner.onGround() && !owner.isInWater() && owner.getPose() == Pose.STANDING;
    }

    @Override
    protected void start(ServerLevel level, Breeze entity, long gameTime) {
        LivingEntity livingEntity = entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (livingEntity != null) {
            boolean flag = entity.withinInnerCircleRange(livingEntity.position());
            Vec3 vec3 = null;
            if (flag) {
                Vec3 posAway = DefaultRandomPos.getPosAway(entity, 5, 5, livingEntity.position());
                if (posAway != null
                    && BreezeUtil.hasLineOfSight(entity, posAway)
                    && livingEntity.distanceToSqr(posAway.x, posAway.y, posAway.z) > livingEntity.distanceToSqr(entity)) {
                    vec3 = posAway;
                }
            }

            if (vec3 == null) {
                vec3 = entity.getRandom().nextBoolean()
                    ? BreezeUtil.randomPointBehindTarget(livingEntity, entity.getRandom())
                    : randomPointInMiddleCircle(entity, livingEntity);
            }

            entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(BlockPos.containing(vec3), 0.6F, 1));
        }
    }

    private static Vec3 randomPointInMiddleCircle(Breeze breeze, LivingEntity target) {
        Vec3 vec3 = target.position().subtract(breeze.position());
        double d = vec3.length() - Mth.lerp(breeze.getRandom().nextDouble(), 8.0, 4.0);
        Vec3 vec31 = vec3.normalize().multiply(d, d, d);
        return breeze.position().add(vec31);
    }
}
