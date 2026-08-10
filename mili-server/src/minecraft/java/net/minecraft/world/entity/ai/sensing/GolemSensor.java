package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class GolemSensor extends Sensor<LivingEntity> {
    private static final int GOLEM_SCAN_RATE = 200;
    private static final int MEMORY_TIME_TO_LIVE = 599;

    public GolemSensor() {
        this(200);
    }

    public GolemSensor(int scanRate) {
        super(scanRate);
    }

    @Override
    protected void doTick(ServerLevel level, LivingEntity entity) {
        checkForNearbyGolem(entity);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_LIVING_ENTITIES);
    }

    public static void checkForNearbyGolem(LivingEntity livingEntity) {
        Optional<List<LivingEntity>> memory = livingEntity.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
        if (!memory.isEmpty()) {
            boolean flag = memory.get().stream().anyMatch(entity -> entity.getType().equals(EntityType.IRON_GOLEM));
            if (flag) {
                golemDetected(livingEntity);
            }
        }
    }

    public static void golemDetected(LivingEntity livingEntity) {
        livingEntity.getBrain().setMemoryWithExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY, true, 599L);
    }
}
