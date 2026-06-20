package fun.bm.mili.utils.concurrent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 优化的三维并发表 / Optimized tri-dimensional concurrent table.
 *
 * <p>在 {@link ConcurrentTable} 基础上添加三重索引 (XY, YZ, ZX) 实现 O(1) 查询 /
 * Adds triple indexes (XY, YZ, ZX) on top of {@link ConcurrentTable} for O(1) lookups.
 *
 * <p>线程安全说明 / Thread-safety note:
 * 索引更新使用 get + put 而非嵌套 computeIfPresent 以避免 ConcurrentHashMap 死锁 /
 * Index updates use get + put instead of nested computeIfPresent to avoid CHM deadlocks.
 *
 * @param <X> 第一维键 / First dimension key
 * @param <Y> 第二维键 / Second dimension key
 * @param <Z> 第三维键 / Third dimension key
 */
public class OptimizedConcurrentTable<X, Y, Z> extends ConcurrentTable<X, Y, Z> {
    // 三重索引: X→Y→{Z}, Y→Z→{X}, Z→X→{Y}
    // Triple indexes: X→Y→{Z}, Y→Z→{X}, Z→X→{Y}
    private final ConcurrentHashMap<X, ConcurrentHashMap<Y, Set<Z>>> xyIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Y, ConcurrentHashMap<Z, Set<X>>> yzIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Z, ConcurrentHashMap<X, Set<Y>>> zxIndex = new ConcurrentHashMap<>();

    // putIfAbsent 专用锁，避免在共享的 data 对象上同步
    // Dedicated lock for putIfAbsent, avoids synchronizing on shared data object
    private final Object putIfAbsentLock = new Object();

    public OptimizedConcurrentTable() {
        super();
    }

    public OptimizedConcurrentTable(boolean flagX, boolean flagY, boolean flagZ) {
        super(flagX, flagY, flagZ);
    }

    @Override
    public void put(X x, Y y, Z z) {
        if (flagX) {
            List<X> datas = getX(y, z);
            for (X x1 : datas) {
                if (!x1.equals(x)) {
                    remove(x1, y, z);
                }
            }
        }
        if (flagY) {
            List<Y> datas = getY(x, z);
            for (Y y1 : datas) {
                if (!y1.equals(y)) {
                    remove(x, y1, z);
                }
            }
        }
        if (flagZ) {
            List<Z> datas = getZ(x, y);
            for (Z z1 : datas) {
                if (!z1.equals(z)) {
                    remove(x, y, z1);
                }
            }
        }
        super.put(x, y, z, true);
        // 更新三重索引 (使用 computeIfAbsent 安全地创建嵌套结构)
        // Update triple indexes (use computeIfAbsent to safely create nested structures)
        xyIndex.computeIfAbsent(x, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(y, k -> ConcurrentHashMap.newKeySet()).add(z);
        yzIndex.computeIfAbsent(y, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(z, k -> ConcurrentHashMap.newKeySet()).add(x);
        zxIndex.computeIfAbsent(z, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(x, k -> ConcurrentHashMap.newKeySet()).add(y);
    }

    @Override
    public void remove(X x, Y y, Z z) {
        super.remove(x, y, z);
        // 从索引中移除 (避免嵌套 computeIfPresent 死锁)
        // Remove from indexes (avoid nested computeIfPresent deadlocks)
        removeFromIndex(xyIndex, x, y, z);
        removeFromIndex(yzIndex, y, z, x);
        removeFromIndex(zxIndex, z, x, y);
    }

    /**
     * 从嵌套索引中安全移除一个值 / Safely remove a value from a nested index.
     *
     * <p>使用 get + remove 而非嵌套 computeIfPresent 以避免 ConcurrentHashMap 死锁 /
     * Uses get + remove instead of nested computeIfPresent to avoid CHM deadlock.
     * ConcurrentHashMap 的 compute 方法会持有桶锁，嵌套调用可能导致锁顺序死锁 /
     * CHM's compute method holds bucket locks; nested calls may cause lock-ordering deadlock.
     */
    private <K, V, T> void removeFromIndex(ConcurrentHashMap<K, ConcurrentHashMap<V, Set<T>>> index,
                                           K key1, V key2, T value) {
        ConcurrentHashMap<V, Set<T>> innerMap = index.get(key1);
        if (innerMap == null) return;
        Set<T> set = innerMap.get(key2);
        if (set == null) return;
        set.remove(value);
        // 清理空集合和空内层映射 / Clean up empty sets and empty inner maps
        if (set.isEmpty()) {
            innerMap.remove(key2);
        }
        if (innerMap.isEmpty()) {
            index.remove(key1);
        }
    }

    /**
     * 按条件批量移除 / Batch remove by predicate.
     */
    public void removeAll(Predicate<TableEntry<X, Y, Z>> predicate) {
        data.removeIf(entry -> {
            boolean shouldRemove = predicate.test(entry);
            if (shouldRemove) {
                removeFromIndex(xyIndex, entry.getX(), entry.getY(), entry.getZ());
                removeFromIndex(yzIndex, entry.getY(), entry.getZ(), entry.getX());
                removeFromIndex(zxIndex, entry.getZ(), entry.getX(), entry.getY());
            }
            return shouldRemove;
        });
    }

    /**
     * 如果不存在则插入 (原子性) / Put if absent (atomic).
     *
     * <p>使用 synchronized 保证 check-then-act 原子性 / Uses synchronized to ensure
     * check-then-act atomicity. 避免 TOCTOU 竞态 / Avoids TOCTOU race condition.
     */
    public boolean putIfAbsent(X x, Y y, Z z) {
        synchronized (putIfAbsentLock) {
            // 在锁内重新检查 / Re-check under lock
            if (data.stream().anyMatch(entry ->
                    Objects.equals(entry.getX(), x) &&
                            Objects.equals(entry.getY(), y) &&
                            Objects.equals(entry.getZ(), z))) {
                return false;
            }
            put(x, y, z);
            return true;
        }
    }

    @Override
    public List<Z> getZ(X x, Y y) {
        ConcurrentHashMap<Y, Set<Z>> innerMap = xyIndex.get(x);
        if (innerMap == null) return new ArrayList<>();
        Set<Z> result = innerMap.get(y);
        return result != null ? new ArrayList<>(result) : new ArrayList<>();
    }

    @Override
    public List<Y> getY(X x, Z z) {
        // zxIndex: Z -> X -> {Y}，直接用 z 和 x 查询 / zxIndex: Z -> X -> {Y}, direct lookup by z then x
        ConcurrentHashMap<X, Set<Y>> innerMap = zxIndex.get(z);
        if (innerMap == null) return new ArrayList<>();
        Set<Y> result = innerMap.get(x);
        return result != null ? new ArrayList<>(result) : new ArrayList<>();
    }

    public List<X> getX(Y y, Z z) {
        ConcurrentHashMap<Z, Set<X>> innerMap = yzIndex.get(y);
        if (innerMap == null) return new ArrayList<>();
        Set<X> result = innerMap.get(z);
        return result != null ? new ArrayList<>(result) : new ArrayList<>();
    }

    @Override
    public Map<X, Y> getXY(Z z) {
        return buildMapFromIndex(zxIndex.get(z), Function.identity(), Function.identity());
    }

    @Override
    public Map<Y, Z> getYZ(X x) {
        return buildMapFromIndex(xyIndex.get(x), Function.identity(), Function.identity());
    }

    @Override
    public Map<X, Z> getXZ(Y y) {
        return reverseMapFromIndex(yzIndex.get(y));
    }

    @Override
    public List<X> getAllX() {
        return new ArrayList<>(new HashSet<>(xyIndex.keySet()));
    }

    @Override
    public List<Y> getAllY() {
        return new ArrayList<>(new HashSet<>(yzIndex.keySet()));
    }

    @Override
    public List<Z> getAllZ() {
        return new ArrayList<>(new HashSet<>(zxIndex.keySet()));
    }


    @Override
    public void clearXY(Z z) {
        super.clearXY(z);
        zxIndex.remove(z);
    }

    @Override
    public void clearYZ(X x) {
        super.clearYZ(x);
        xyIndex.remove(x);
    }

    @Override
    public void clearXZ(Y y) {
        super.clearXZ(y);
        yzIndex.remove(y);
    }

    @Override
    public void clearAll() {
        super.clearAll();
        xyIndex.clear();
        yzIndex.clear();
        zxIndex.clear();
    }

    /**
     * 从索引构建映射 / Build a map from an index.
     */
    private <K, V, R, S> Map<R, S> buildMapFromIndex(ConcurrentHashMap<K, Set<V>> indexMap,
                                                      java.util.function.Function<K, R> keyMapper,
                                                      java.util.function.Function<V, S> valueMapper) {
        Map<R, S> result = new HashMap<>();
        if (indexMap != null) {
            for (Map.Entry<K, Set<V>> entry : indexMap.entrySet()) {
                K key = entry.getKey();
                Set<V> valueSet = entry.getValue();
                if (valueSet != null && !valueSet.isEmpty()) {
                    for (V value : valueSet) {
                        result.put(keyMapper.apply(key), valueMapper.apply(value));
                    }
                }
            }
        }
        return result;
    }

    /**
     * 从索引构建反向映射 / Build a reverse map from an index.
     */
    private <K, V> Map<V, K> reverseMapFromIndex(ConcurrentHashMap<K, Set<V>> indexMap) {
        Map<V, K> result = new HashMap<>();
        if (indexMap != null) {
            for (Map.Entry<K, Set<V>> entry : indexMap.entrySet()) {
                K key = entry.getKey();
                Set<V> valueSet = entry.getValue();
                if (valueSet != null && !valueSet.isEmpty()) {
                    for (V value : valueSet) {
                        result.put(value, key);
                    }
                }
            }
        }
        return result;
    }
}
