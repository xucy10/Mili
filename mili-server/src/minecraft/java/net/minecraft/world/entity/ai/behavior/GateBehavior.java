package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class GateBehavior<E extends LivingEntity> implements BehaviorControl<E> {
    private final Map<MemoryModuleType<?>, MemoryStatus> entryCondition;
    private final Set<MemoryModuleType<?>> exitErasedMemories;
    private final GateBehavior.OrderPolicy orderPolicy;
    private final GateBehavior.RunningPolicy runningPolicy;
    private final ShufflingList<BehaviorControl<? super E>> behaviors = new ShufflingList<>(false); // Paper - Fix Concurrency issue in ShufflingList during worldgen
    private Behavior.Status status = Behavior.Status.STOPPED;

    public GateBehavior(
        Map<MemoryModuleType<?>, MemoryStatus> entryCondition,
        Set<MemoryModuleType<?>> exitErasedMemories,
        GateBehavior.OrderPolicy orderPolicy,
        GateBehavior.RunningPolicy runningPolicy,
        List<Pair<? extends BehaviorControl<? super E>, Integer>> durations
    ) {
        this.entryCondition = entryCondition;
        this.exitErasedMemories = exitErasedMemories;
        this.orderPolicy = orderPolicy;
        this.runningPolicy = runningPolicy;
        durations.forEach(pair -> this.behaviors.add(pair.getFirst(), pair.getSecond()));
    }

    @Override
    public Behavior.Status getStatus() {
        return this.status;
    }

    private boolean hasRequiredMemories(E entity) {
        for (Entry<MemoryModuleType<?>, MemoryStatus> entry : this.entryCondition.entrySet()) {
            MemoryModuleType<?> memoryModuleType = entry.getKey();
            MemoryStatus memoryStatus = entry.getValue();
            if (!entity.getBrain().checkMemory(memoryModuleType, memoryStatus)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public final boolean tryStart(ServerLevel level, E entity, long gameTime) {
        if (this.hasRequiredMemories(entity)) {
            this.status = Behavior.Status.RUNNING;
            this.orderPolicy.apply(this.behaviors);
            this.runningPolicy.apply(this.behaviors, level, entity, gameTime); // Paper - Perf: Remove streams from hot code
            return true;
        } else {
            return false;
        }
    }

    @Override
    public final void tickOrStop(ServerLevel level, E entity, long gameTime) {
        // Paper start - Perf: Remove streams from hot code
        for (final BehaviorControl<? super E> behavior : this.behaviors) {
            if (behavior.getStatus() == Behavior.Status.RUNNING) {
                behavior.tickOrStop(level, entity, gameTime);
            }
        }
        // Paper end - Perf: Remove streams from hot code
        if (this.behaviors.stream().noneMatch(behavior -> behavior.getStatus() == Behavior.Status.RUNNING)) {
            this.doStop(level, entity, gameTime);
        }
    }

    @Override
    public final void doStop(ServerLevel level, E entity, long gameTime) {
        this.status = Behavior.Status.STOPPED;
        // Paper start - Perf: Remove streams from hot code
        for (final BehaviorControl<? super E> behavior : this.behaviors) {
            if (behavior.getStatus() == Behavior.Status.RUNNING) {
                behavior.doStop(level, entity, gameTime);
            }
        }
        for (final MemoryModuleType<?> exitErasedMemory : this.exitErasedMemories) {
            entity.getBrain().eraseMemory(exitErasedMemory);
        }
        // Paper end - Perf: Remove streams from hot code
    }

    @Override
    public String debugString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        Set<? extends BehaviorControl<? super E>> set = this.behaviors
            .stream()
            .filter(behavior -> behavior.getStatus() == Behavior.Status.RUNNING)
            .collect(Collectors.toSet());
        return "(" + this.getClass().getSimpleName() + "): " + set;
    }

    public static enum OrderPolicy {
        ORDERED(consumer -> {}),
        SHUFFLED(ShufflingList::shuffle);

        private final Consumer<ShufflingList<?>> consumer;

        private OrderPolicy(final Consumer<ShufflingList<?>> consumer) {
            this.consumer = consumer;
        }

        public void apply(ShufflingList<?> list) {
            this.consumer.accept(list);
        }
    }

    public static enum RunningPolicy {
        RUN_ONE {
            // Paper start - Perf: Remove streams from hot code
            @Override
            public <E extends LivingEntity> void apply(ShufflingList<BehaviorControl<? super E>> behaviors, ServerLevel level, E owner, long gameTime) {
                for (final BehaviorControl<? super E> behavior : behaviors) {
                    if (behavior.getStatus() == Behavior.Status.STOPPED && behavior.tryStart(level, owner, gameTime)) {
                        break;
                    }
                }
                // Paper end - Perf: Remove streams from hot code
            }
        },
        TRY_ALL {
            // Paper start - Perf: Remove streams from hot code
            @Override
            public <E extends LivingEntity> void apply(ShufflingList<BehaviorControl<? super E>> behaviors, ServerLevel level, E owner, long gameTime) {
                for (final BehaviorControl<? super E> behavior : behaviors) {
                    if (behavior.getStatus() == Behavior.Status.STOPPED) {
                        behavior.tryStart(level, owner, gameTime);
                    }
                }
                // Paper end - Perf: Remove streams from hot code
            }
        };

        public abstract <E extends LivingEntity> void apply(ShufflingList<BehaviorControl<? super E>> behaviors, ServerLevel level, E owner, long gameTime); // Paper - Perf: Remove streams from hot code
    }
}
