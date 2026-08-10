package net.minecraft.world.entity.ai.sensing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class AxolotlAttackablesSensor extends NearestVisibleLivingEntitySensor {
    public static final float TARGET_DETECTION_DISTANCE = 8.0F;

    @Override
    protected boolean isMatchingEntity(ServerLevel level, LivingEntity entity, LivingEntity target) {
        return this.isClose(entity, target)
            && target.isInWater()
            && (this.isHostileTarget(target) || this.isHuntTarget(entity, target))
            && Sensor.isEntityAttackable(level, entity, target);
    }

    private boolean isHuntTarget(LivingEntity attacker, LivingEntity target) {
        return !attacker.getBrain().hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN) && target.getType().is(EntityTypeTags.AXOLOTL_HUNT_TARGETS);
    }

    private boolean isHostileTarget(LivingEntity target) {
        return target.getType().is(EntityTypeTags.AXOLOTL_ALWAYS_HOSTILES);
    }

    private boolean isClose(LivingEntity attacker, LivingEntity target) {
        return target.distanceToSqr(attacker) <= 64.0;
    }

    @Override
    protected MemoryModuleType<LivingEntity> getMemory() {
        return MemoryModuleType.NEAREST_ATTACKABLE;
    }
}
