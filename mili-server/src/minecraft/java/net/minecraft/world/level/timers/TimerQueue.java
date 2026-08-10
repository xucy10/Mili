package net.minecraft.world.level.timers;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.primitives.UnsignedLong;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

public class TimerQueue<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CALLBACK_DATA_TAG = "Callback";
    private static final String TIMER_NAME_TAG = "Name";
    private static final String TIMER_TRIGGER_TIME_TAG = "TriggerTime";
    private final TimerCallbacks<T> callbacksRegistry;
    private final Queue<TimerQueue.Event<T>> queue = new PriorityQueue<>(createComparator());
    private UnsignedLong sequentialId = UnsignedLong.ZERO;
    private final Table<String, Long, TimerQueue.Event<T>> events = HashBasedTable.create();

    private static <T> Comparator<TimerQueue.Event<T>> createComparator() {
        return Comparator.<TimerQueue.Event<T>>comparingLong(event -> event.triggerTime).thenComparing(event -> event.sequentialId);
    }

    public TimerQueue(TimerCallbacks<T> callbacksRegistry, Stream<? extends Dynamic<?>> scheduledEventsDynamic) {
        this(callbacksRegistry);
        this.queue.clear();
        this.events.clear();
        this.sequentialId = UnsignedLong.ZERO;
        scheduledEventsDynamic.forEach(scheduledEventDynamic -> {
            Tag tag = scheduledEventDynamic.convert(NbtOps.INSTANCE).getValue();
            if (tag instanceof CompoundTag compoundTag) {
                this.loadEvent(compoundTag);
            } else {
                LOGGER.warn("Invalid format of events: {}", tag);
            }
        });
    }

    public TimerQueue(TimerCallbacks<T> callbacksRegistry) {
        this.callbacksRegistry = callbacksRegistry;
    }

    public void tick(T obj, long gameTime) {
        while (true) {
            TimerQueue.Event<T> event = this.queue.peek();
            if (event == null || event.triggerTime > gameTime) {
                return;
            }

            this.queue.remove();
            this.events.remove(event.id, gameTime);
            event.callback.handle(obj, this, gameTime);
        }
    }

    public void schedule(String id, long triggerTime, TimerCallback<T> callback) {
        if (!this.events.contains(id, triggerTime)) {
            this.sequentialId = this.sequentialId.plus(UnsignedLong.ONE);
            TimerQueue.Event<T> event = new TimerQueue.Event<>(triggerTime, this.sequentialId, id, callback);
            this.events.put(id, triggerTime, event);
            this.queue.add(event);
        }
    }

    public int remove(String eventId) {
        Collection<TimerQueue.Event<T>> collection = this.events.row(eventId).values();
        collection.forEach(this.queue::remove);
        int size = collection.size();
        collection.clear();
        return size;
    }

    public Set<String> getEventsIds() {
        return Collections.unmodifiableSet(this.events.rowKeySet());
    }

    private void loadEvent(CompoundTag tag) {
        TimerCallback<T> timerCallback = tag.read("Callback", this.callbacksRegistry.codec()).orElse(null);
        if (timerCallback != null) {
            String stringOr = tag.getStringOr("Name", "");
            long longOr = tag.getLongOr("TriggerTime", 0L);
            this.schedule(stringOr, longOr, timerCallback);
        }
    }

    private CompoundTag storeEvent(TimerQueue.Event<T> event) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("Name", event.id);
        compoundTag.putLong("TriggerTime", event.triggerTime);
        compoundTag.store("Callback", this.callbacksRegistry.codec(), event.callback);
        return compoundTag;
    }

    public ListTag store() {
        ListTag listTag = new ListTag();
        this.queue.stream().sorted(createComparator()).map(this::storeEvent).forEach(listTag::add);
        return listTag;
    }

    public static class Event<T> {
        public final long triggerTime;
        public final UnsignedLong sequentialId;
        public final String id;
        public final TimerCallback<T> callback;

        Event(long triggerTime, UnsignedLong sequentialId, String id, TimerCallback<T> callback) {
            this.triggerTime = triggerTime;
            this.sequentialId = sequentialId;
            this.id = id;
            this.callback = callback;
        }
    }
}
