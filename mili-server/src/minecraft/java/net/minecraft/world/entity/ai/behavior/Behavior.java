package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public abstract class Behavior<E extends LivingEntity> implements BehaviorControl<E> {
    public static final int DEFAULT_DURATION = 60;
    protected final Map<MemoryModuleType<?>, MemoryStatus> entryCondition;
    private Behavior.Status status = Behavior.Status.STOPPED;
    private long endTimestamp;
    private final int minDuration;
    private final int maxDuration;
    private final String configKey; // Paper - configurable behavior tick rate and timings

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition) {
        this(entryCondition, 60);
    }

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition, int duration) {
        this(entryCondition, duration, duration);
    }

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition, int minDuration, int maxDuration) {
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
        this.entryCondition = entryCondition;
        // Paper start - configurable behavior tick rate and timings
        String key = io.papermc.paper.util.MappingEnvironment.reobf() ? io.papermc.paper.util.ObfHelper.INSTANCE.deobfClassName(this.getClass().getName()) : this.getClass().getName();
        int lastSeparator = key.lastIndexOf('.');
        if (lastSeparator != -1) {
            key = key.substring(lastSeparator + 1);
        }
        this.configKey = key.toLowerCase(java.util.Locale.ROOT);
        // Paper end - configurable behavior tick rate and timings
    }

    @Override
    public Behavior.Status getStatus() {
        return this.status;
    }

    @Override
    public final boolean tryStart(ServerLevel level, E owner, long gameTime) {
        // Paper start - configurable behavior tick rate and timings
        int tickRate = java.util.Objects.requireNonNullElse(level.paperConfig().tickRates.behavior.get(owner.getType(), this.configKey), -1);
        if (tickRate > -1 && gameTime < this.endTimestamp + tickRate) {
            return false;
        }
        // Paper end - configurable behavior tick rate and timings
        if (this.hasRequiredMemories(owner) && this.checkExtraStartConditions(level, owner)) {
            this.status = Behavior.Status.RUNNING;
            int i = this.minDuration + level.getRandom().nextInt(this.maxDuration + 1 - this.minDuration);
            this.endTimestamp = gameTime + i;
            this.start(level, owner, gameTime);
            return true;
        } else {
            return false;
        }
    }

    protected void start(ServerLevel level, E entity, long gameTime) {
    }

    @Override
    public final void tickOrStop(ServerLevel level, E entity, long gameTime) {
        if (!this.timedOut(gameTime) && this.canStillUse(level, entity, gameTime)) {
            this.tick(level, entity, gameTime);
        } else {
            this.doStop(level, entity, gameTime);
        }
    }

    protected void tick(ServerLevel level, E owner, long gameTime) {
    }

    @Override
    public final void doStop(ServerLevel level, E entity, long gameTime) {
        this.status = Behavior.Status.STOPPED;
        this.stop(level, entity, gameTime);
    }

    protected void stop(ServerLevel level, E entity, long gameTime) {
    }

    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return false;
    }

    protected boolean timedOut(long gameTime) {
        return gameTime > this.endTimestamp;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, E owner) {
        return true;
    }

    @Override
    public String debugString() {
        return this.getClass().getSimpleName();
    }

    protected boolean hasRequiredMemories(E owner) {
        for (Entry<MemoryModuleType<?>, MemoryStatus> entry : this.entryCondition.entrySet()) {
            MemoryModuleType<?> memoryModuleType = entry.getKey();
            MemoryStatus memoryStatus = entry.getValue();
            if (!owner.getBrain().checkMemory(memoryModuleType, memoryStatus)) {
                return false;
            }
        }

        return true;
    }

    public static enum Status {
        STOPPED,
        RUNNING;
    }
}
