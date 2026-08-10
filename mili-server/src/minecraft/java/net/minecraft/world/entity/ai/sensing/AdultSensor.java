package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class AdultSensor extends Sensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
    }

    @Override
    protected void doTick(ServerLevel level, LivingEntity entity) {
        entity.getBrain()
            .getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
            .ifPresent(nearestVisibleLivingEntities -> this.setNearestVisibleAdult(entity, nearestVisibleLivingEntities));
    }

    protected void setNearestVisibleAdult(LivingEntity entity, NearestVisibleLivingEntities nearestVisibleLivingEntities) {
        Optional<LivingEntity> optional = nearestVisibleLivingEntities.findClosest(
            livingEntity -> livingEntity.getType() == entity.getType() && !livingEntity.isBaby()
        );
        entity.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT, optional);
    }
}
