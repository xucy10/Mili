package net.minecraft.world.entity.ai.sensing;

import java.util.Optional;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class AdultSensorAnyType extends AdultSensor {
    @Override
    protected void setNearestVisibleAdult(LivingEntity entity, NearestVisibleLivingEntities nearestVisibleLivingEntities) {
        Optional<LivingEntity> optional = nearestVisibleLivingEntities.findClosest(
            livingEntity -> livingEntity.getType().is(EntityTypeTags.FOLLOWABLE_FRIENDLY_MOBS) && !livingEntity.isBaby()
        );
        entity.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT, optional);
    }
}
