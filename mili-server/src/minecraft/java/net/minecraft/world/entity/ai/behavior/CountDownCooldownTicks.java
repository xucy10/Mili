package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class CountDownCooldownTicks extends Behavior<LivingEntity> {
    private final MemoryModuleType<Integer> cooldownTicks;

    public CountDownCooldownTicks(MemoryModuleType<Integer> cooldownTicks) {
        super(ImmutableMap.of(cooldownTicks, MemoryStatus.VALUE_PRESENT));
        this.cooldownTicks = cooldownTicks;
    }

    private Optional<Integer> getCooldownTickMemory(LivingEntity entity) {
        return entity.getBrain().getMemory(this.cooldownTicks);
    }

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, LivingEntity entity, long gameTime) {
        Optional<Integer> cooldownTickMemory = this.getCooldownTickMemory(entity);
        return cooldownTickMemory.isPresent() && cooldownTickMemory.get() > 0;
    }

    @Override
    protected void tick(ServerLevel level, LivingEntity owner, long gameTime) {
        Optional<Integer> cooldownTickMemory = this.getCooldownTickMemory(owner);
        owner.getBrain().setMemory(this.cooldownTicks, cooldownTickMemory.get() - 1);
    }

    @Override
    protected void stop(ServerLevel level, LivingEntity entity, long gameTime) {
        entity.getBrain().eraseMemory(this.cooldownTicks);
    }
}
