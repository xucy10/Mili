package fun.bm.mili.utils.concurrent;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 并发弱引用哈希映射 / Concurrent weak-reference hash map.
 *
 * <p>键通过 {@link WeakReference} 持有，被 GC 回收后自动从映射中移除 / Keys are held via
 * {@link WeakReference} and automatically removed when GC collects them.
 * 使用 {@link System#identityHashCode} 做哈希比较 (非 equals) / Uses identity hash code
 * for hashing (not equals-based).
 *
 * <p>线程安全: 基于 {@link ConcurrentHashMap} / Thread-safe: backed by {@link ConcurrentHashMap}.
 * 清理 (expunge) 按时间间隔触发，非每次操作 / Expunge is time-interval-based, not per-operation.
 *
 * @param <K> 键类型 (弱引用) / Key type (weakly referenced)
 * @param <V> 值类型 / Value type
 */
public final class ConcurrentWeakHashMap<K, V> {
    private final ConcurrentHashMap<WeakKey<K>, V> map = new ConcurrentHashMap<>();
    private final ReferenceQueue<K> queue = new ReferenceQueue<>();

    /** 上次清理时间戳 (纳秒) / Last expunge timestamp (nanos). */
    private volatile long lastExpungeNanos = 0L;
    /** 清理间隔: 5秒 / Expunge interval: 5 seconds. */
    private static final long EXPUNGE_INTERVAL_NANOS = 5_000_000_000L;

    /**
     * 弱引用键 / Weak reference key.
     * 使用 identityHashCode 做哈希，引用相等 (==) 做比较 / Uses identityHashCode for
     * hashing and reference equality (==) for comparison.
     */
    private static final class WeakKey<K> extends WeakReference<K> {
        private final int hash;

        WeakKey(K key, ReferenceQueue<K> queue) {
            super(key, queue);
            this.hash = System.identityHashCode(key);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof WeakKey<?> other)) return false;
            // 如果哈希不同则快速失败 / Fast-fail if hashes differ
            if (this.hash != other.hash) return false;
            Object thisRef = this.get();
            Object otherRef = other.get();
            // 任一引用已被 GC 回收则不匹配 (死引用不应匹配任何查询)
            // If either reference has been GC'd, they don't match (dead refs should never match)
            if (thisRef == null || otherRef == null) {
                return false;
            }
            // 引用相等 (identity) 比较 / Reference identity comparison
            return thisRef == otherRef;
        }
    }

    /**
     * 获取值，不存在时返回默认值 / Get value, returning default if absent.
     */
    public V getOrDefault(K key, V defaultValue) {
        maybeExpunge();
        V value = map.get(new WeakKey<>(key, null));
        return value != null ? value : defaultValue;
    }

    /**
     * 插入键值对 / Put key-value pair.
     */
    public V put(K key, V value) {
        maybeExpunge();
        return map.put(new WeakKey<>(key, queue), value);
    }

    /**
     * 按键移除 / Remove by key.
     */
    public void remove(K key) {
        maybeExpunge();
        map.remove(new WeakKey<>(key, null));
    }

    /**
     * 返回当前映射大小 (触发清理) / Return current map size (triggers expunge).
     */
    public int size() {
        maybeExpunge();
        return map.size();
    }

    /**
     * 间隔式清理已被 GC 回收的键 / Interval-based cleanup of GC'd keys.
     * 避免每次操作都遍历 ReferenceQueue / Avoids iterating ReferenceQueue on every operation.
     */
    private void maybeExpunge() {
        long now = System.nanoTime();
        if (now - lastExpungeNanos < EXPUNGE_INTERVAL_NANOS) {
            return;
        }
        lastExpungeNanos = now;
        WeakKey<?> ref;
        while ((ref = (WeakKey<?>) queue.poll()) != null) {
            map.remove(ref);
        }
    }
}
