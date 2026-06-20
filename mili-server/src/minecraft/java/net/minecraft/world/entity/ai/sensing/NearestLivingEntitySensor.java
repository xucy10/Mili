package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.phys.AABB;

public class NearestLivingEntitySensor<T extends LivingEntity> extends Sensor<T> {
    @Override
    protected void doTick(ServerLevel level, T entity) {
        double attributeValue = entity.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB aabb = entity.getBoundingBox().inflate(attributeValue, attributeValue, attributeValue);
        List<LivingEntity> entitiesOfClass = level.getEntitiesOfClass(
            LivingEntity.class, aabb, matchableEntity -> matchableEntity != entity && matchableEntity.isAlive()
        );
        entitiesOfClass.sort(Comparator.comparingDouble(entity::distanceToSqr));
        Brain<?> brain = entity.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, entitiesOfClass);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(level, entity, entitiesOfClass));
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
    }
}
