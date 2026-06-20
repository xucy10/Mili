package net.minecraft.world.entity.ai.sensing;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class MobSensor<T extends LivingEntity> extends Sensor<T> {
    private final BiPredicate<T, LivingEntity> mobTest;
    private final Predicate<T> readyTest;
    private final MemoryModuleType<Boolean> toSet;
    private final int memoryTimeToLive;

    public MobSensor(int scanRate, BiPredicate<T, LivingEntity> mobTest, Predicate<T> readyTest, MemoryModuleType<Boolean> toSet, int memoryTimeToLive) {
        super(scanRate);
        this.mobTest = mobTest;
        this.readyTest = readyTest;
        this.toSet = toSet;
        this.memoryTimeToLive = memoryTimeToLive;
    }

    @Override
    protected void doTick(ServerLevel level, T entity) {
        if (!this.readyTest.test(entity)) {
            this.clearMemory(entity);
        } else {
            this.checkForMobsNearby(entity);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(MemoryModuleType.NEAREST_LIVING_ENTITIES);
    }

    public void checkForMobsNearby(T sensingEntity) {
        Optional<List<LivingEntity>> memory = sensingEntity.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
        if (!memory.isEmpty()) {
            boolean flag = memory.get().stream().anyMatch(livingEntity -> this.mobTest.test(sensingEntity, livingEntity));
            if (flag) {
                this.mobDetected(sensingEntity);
            }
        }
    }

    public void mobDetected(T sensingEntity) {
        sensingEntity.getBrain().setMemoryWithExpiry(this.toSet, true, this.memoryTimeToLive);
    }

    public void clearMemory(T sensingEntity) {
        sensingEntity.getBrain().eraseMemory(this.toSet);
    }
}
