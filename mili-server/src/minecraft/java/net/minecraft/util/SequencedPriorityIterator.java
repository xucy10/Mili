package net.minecraft.util;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Queues;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import java.util.Deque;
import org.jspecify.annotations.Nullable;

public final class SequencedPriorityIterator<T> extends AbstractIterator<T> {
    private static final int MIN_PRIO = Integer.MIN_VALUE;
    private @Nullable Deque<T> highestPrioQueue = null;
    private int highestPrio = Integer.MIN_VALUE;
    private final Int2ObjectMap<Deque<T>> queuesByPriority = new Int2ObjectOpenHashMap<>();

    public void add(T value, int priority) {
        if (priority == this.highestPrio && this.highestPrioQueue != null) {
            this.highestPrioQueue.addLast(value);
        } else {
            Deque<T> deque = this.queuesByPriority.computeIfAbsent(priority, i -> Queues.newArrayDeque());
            deque.addLast(value);
            if (priority >= this.highestPrio) {
                this.highestPrioQueue = deque;
                this.highestPrio = priority;
            }
        }
    }

    @Override
    protected @Nullable T computeNext() {
        if (this.highestPrioQueue == null) {
            return this.endOfData();
        } else {
            T object = this.highestPrioQueue.removeFirst();
            if (object == null) {
                return this.endOfData();
            } else {
                if (this.highestPrioQueue.isEmpty()) {
                    this.switchCacheToNextHighestPrioQueue();
                }

                return object;
            }
        }
    }

    private void switchCacheToNextHighestPrioQueue() {
        int i = Integer.MIN_VALUE;
        Deque<T> deque = null;

        for (Entry<Deque<T>> entry : Int2ObjectMaps.fastIterable(this.queuesByPriority)) {
            Deque<T> deque1 = entry.getValue();
            int intKey = entry.getIntKey();
            if (intKey > i && !deque1.isEmpty()) {
                i = intKey;
                deque = deque1;
                if (intKey == this.highestPrio - 1) {
                    break;
                }
            }
        }

        this.highestPrio = i;
        this.highestPrioQueue = deque;
    }
}
