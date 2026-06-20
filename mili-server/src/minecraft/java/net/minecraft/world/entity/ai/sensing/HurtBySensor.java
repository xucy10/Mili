package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class HurtBySensor extends Sensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY);
    }

    @Override
    protected void doTick(ServerLevel level, LivingEntity entity) {
        Brain<?> brain = entity.getBrain();
        DamageSource lastDamageSource = entity.getLastDamageSource();
        if (lastDamageSource != null) {
            brain.setMemory(MemoryModuleType.HURT_BY, entity.getLastDamageSource());
            Entity entity1 = lastDamageSource.getEntity();
            if (entity1 instanceof LivingEntity) {
                brain.setMemory(MemoryModuleType.HURT_BY_ENTITY, (LivingEntity)entity1);
            }
        } else {
            brain.eraseMemory(MemoryModuleType.HURT_BY);
        }

        brain.getMemory(MemoryModuleType.HURT_BY_ENTITY).ifPresent(attacker -> {
            if (!attacker.isAlive() || attacker.level() != level) {
                brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            }
        });
    }
}
