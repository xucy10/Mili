package net.minecraft.world.ticks;

import it.unimi.dsi.fastutil.Hash.Strategy;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

public record ScheduledTick<T>(T type, BlockPos pos, long triggerTick, TickPriority priority, long subTickOrder) {
    public static final Comparator<ScheduledTick<?>> DRAIN_ORDER = (scheduledTick, scheduledTick1) -> {
        int i = Long.compare(scheduledTick.triggerTick, scheduledTick1.triggerTick);
        if (i != 0) {
            return i;
        } else {
            i = scheduledTick.priority.compareTo(scheduledTick1.priority);
            return i != 0 ? i : Long.compare(scheduledTick.subTickOrder, scheduledTick1.subTickOrder);
        }
    };
    public static final Comparator<ScheduledTick<?>> INTRA_TICK_DRAIN_ORDER = (scheduledTick, scheduledTick1) -> {
        int i = scheduledTick.priority.compareTo(scheduledTick1.priority);
        return i != 0 ? i : Long.compare(scheduledTick.subTickOrder, scheduledTick1.subTickOrder);
    };
    public static final Strategy<ScheduledTick<?>> UNIQUE_TICK_HASH = new Strategy<ScheduledTick<?>>() {
        @Override
        public int hashCode(ScheduledTick<?> scheduledTick) {
            return 31 * scheduledTick.pos().hashCode() + scheduledTick.type().hashCode();
        }

        @Override
        public boolean equals(@Nullable ScheduledTick<?> first, @Nullable ScheduledTick<?> second) {
            return first == second || first != null && second != null && first.type() == second.type() && first.pos().equals(second.pos());
        }
    };

    public ScheduledTick(T type, BlockPos pos, long triggerTick, long subTickOrder) {
        this(type, pos, triggerTick, TickPriority.NORMAL, subTickOrder);
    }

    public ScheduledTick(T type, BlockPos pos, long triggerTick, TickPriority priority, long subTickOrder) {
        pos = pos.immutable();
        this.type = type;
        this.pos = pos;
        this.triggerTick = triggerTick;
        this.priority = priority;
        this.subTickOrder = subTickOrder;
    }

    public static <T> ScheduledTick<T> probe(T type, BlockPos pos) {
        return new ScheduledTick<>(type, pos, 0L, TickPriority.NORMAL, 0L);
    }

    public SavedTick<T> toSavedTick(long gameTime) {
        return new SavedTick<>(this.type, this.pos, (int)(this.triggerTick - gameTime), this.priority);
    }
}
