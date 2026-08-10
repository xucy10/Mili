package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class LookAtTargetSink extends Behavior<Mob> {
    public LookAtTargetSink(int minDuration, int maxDuration) {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_PRESENT), minDuration, maxDuration);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Mob entity, long gameTime) {
        return entity.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).filter(positionTracker -> positionTracker.isVisibleBy(entity)).isPresent();
    }

    @Override
    protected void stop(ServerLevel level, Mob entity, long gameTime) {
        entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected void tick(ServerLevel level, Mob owner, long gameTime) {
        owner.getBrain()
            .getMemory(MemoryModuleType.LOOK_TARGET)
            .ifPresent(positionTracker -> owner.getLookControl().setLookAt(positionTracker.currentPosition()));
    }
}
