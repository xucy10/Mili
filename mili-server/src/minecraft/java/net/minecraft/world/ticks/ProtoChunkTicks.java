package net.minecraft.world.ticks;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;

public class ProtoChunkTicks<T> implements SerializableTickContainer<T>, TickContainerAccess<T> {
    private final List<SavedTick<T>> ticks = Lists.newArrayList();
    private final Set<SavedTick<?>> ticksPerPosition = new ObjectOpenCustomHashSet<>(SavedTick.UNIQUE_TICK_HASH);

    @Override
    public void schedule(ScheduledTick<T> tick) {
        SavedTick<T> savedTick = new SavedTick<>(tick.type(), tick.pos(), 0, tick.priority());
        this.schedule(savedTick);
    }

    private void schedule(SavedTick<T> tick) {
        if (this.ticksPerPosition.add(tick)) {
            this.ticks.add(tick);
        }
    }

    @Override
    public boolean hasScheduledTick(BlockPos pos, T type) {
        return this.ticksPerPosition.contains(SavedTick.probe(type, pos));
    }

    @Override
    public int count() {
        return this.ticks.size();
    }

    @Override
    public List<SavedTick<T>> pack(long gameTime) {
        return this.ticks;
    }

    public List<SavedTick<T>> scheduledTicks() {
        return List.copyOf(this.ticks);
    }

    public static <T> ProtoChunkTicks<T> load(List<SavedTick<T>> ticks) {
        ProtoChunkTicks<T> protoChunkTicks = new ProtoChunkTicks<>();
        ticks.forEach(protoChunkTicks::schedule);
        return protoChunkTicks;
    }
}
