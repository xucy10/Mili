package fun.bm.mili.util;

import fun.bm.mili.config.modules.misc.ItemEntityPerfConfig;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

public final class HopperIdleHelper {
    // Identity-based weak map: avoids autoboxing and expunge-on-every-call overhead
    private static final ReferenceQueue<HopperBlockEntity> QUEUE = new ReferenceQueue<>();
    private static final ConcurrentHashMap<WeakHopperKey, Integer> idleTickMap = new ConcurrentHashMap<>();
    private static volatile long lastExpunge = 0;

    private HopperIdleHelper() {}

    private static final class WeakHopperKey extends WeakReference<HopperBlockEntity> {
        final int hash;
        WeakHopperKey(HopperBlockEntity ref) {
            super(ref, QUEUE);
            this.hash = System.identityHashCode(ref);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WeakHopperKey other)) return false;
            Object a = this.get(), b = other.get();
            return a != null && a == b;
        }
    }

    private static void expungeStale() {
        long now = System.nanoTime();
        if (now - lastExpunge < 5_000_000_000L) return; // every 5 seconds
        lastExpunge = now;
        WeakHopperKey ref;
        while ((ref = (WeakHopperKey) QUEUE.poll()) != null) {
            idleTickMap.remove(ref);
        }
    }

    public static boolean shouldSkipTick(HopperBlockEntity blockEntity, Level level) {
        int skipTicks = ItemEntityPerfConfig.hopperIdleSkipTicks;
        int threshold = ItemEntityPerfConfig.hopperIdleThreshold;

        if (skipTicks <= 1 || threshold <= 0) {
            return false;
        }

        expungeStale();

        boolean isEmpty = isHopperEmpty(blockEntity);
        WeakHopperKey key = new WeakHopperKey(blockEntity);
        Integer boxed = idleTickMap.get(key);
        int idleTicks = boxed != null ? boxed : 0;

        if (!isEmpty) {
            if (idleTicks > 0) {
                idleTickMap.put(key, 0);
            }
            return false;
        }

        idleTicks++;
        idleTickMap.put(key, idleTicks);

        if (idleTicks <= threshold) {
            return false;
        }

        return level.getGameTime() % skipTicks != 0;
    }

    private static boolean isHopperEmpty(HopperBlockEntity hopper) {
        if (!(hopper instanceof Container container)) {
            return true;
        }
        int size = container.getContainerSize();
        for (int i = 0; i < size; i++) {
            if (!container.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
