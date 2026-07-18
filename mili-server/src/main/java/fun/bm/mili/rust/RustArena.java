package fun.bm.mili.rust;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Rust-style Arena allocator for bulk allocation with single-phase deallocation.
 * <p>
 * Instead of allocating objects individually and relying on GC, the Arena
 * allocates objects in batches and releases them all at once when {@link #reset()}
 * is called. This eliminates per-object GC pressure in hot loops.
 * <p>
 * Typical usage:
 * <pre>{@code
 *   Arena<ChunkSnapshot> arena = Arena.of(ChunkSnapshot::new);
 *   for (int i = 0; i < 1000; i++) {
 *       ChunkSnapshot snap = arena.alloc();
 *       snap.init(cx, cz, data);
 *       process(snap);
 *   }
 *   arena.reset();  // all objects recycled at once
 * }</pre>
 *
 * @param <T> the type of objects managed by this arena
 */
public final class RustArena<T> {

    private final Supplier<T> factory;
    private final List<T> allocated;
    private int nextFree;

    private RustArena(Supplier<T> factory, int initialCapacity) {
        this.factory = factory;
        this.allocated = new ArrayList<>(initialCapacity);
        this.nextFree = 0;
        for (int i = 0; i < initialCapacity; i++) {
            allocated.add(factory.get());
        }
    }

    public static <T> RustArena<T> of(Supplier<T> factory) {
        return new RustArena<>(factory, 64);
    }

    public static <T> RustArena<T> of(Supplier<T> factory, int initialCapacity) {
        return new RustArena<>(factory, Math.max(16, initialCapacity));
    }

    public T alloc() {
        if (nextFree >= allocated.size()) {
            for (int i = 0; i < 32; i++) {
                allocated.add(factory.get());
            }
        }
        return allocated.get(nextFree++);
    }

    public int usedCount() {
        return nextFree;
    }

    public int capacity() {
        return allocated.size();
    }

    public void reset() {
        nextFree = 0;
    }

    public void shrink() {
        if (allocated.size() > nextFree + 64) {
            allocated.subList(nextFree + 64, allocated.size()).clear();
        }
    }
}