package fun.bm.mili.utils.concurrent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public class OptimizedConcurrentTable<X, Y, Z> extends ConcurrentTable<X, Y, Z> {
    private final ConcurrentHashMap<X, ConcurrentHashMap<Y, Set<Z>>> xyIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Y, ConcurrentHashMap<Z, Set<X>>> yzIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Z, ConcurrentHashMap<X, Set<Y>>> zxIndex = new ConcurrentHashMap<>();

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
        removeFromIndex(xyIndex, x, y, z);
        removeFromIndex(yzIndex, y, z, x);
        removeFromIndex(zxIndex, z, x, y);
    }

    private <K, V, T> void removeFromIndex(ConcurrentHashMap<K, ConcurrentHashMap<V, Set<T>>> index,
                                           K key1, V key2, T value) {
        index.computeIfPresent(key1, (k, map) -> {
            map.computeIfPresent(key2, (k2, set) -> {
                set.remove(value);
                return set.isEmpty() ? null : set;
            });
            return map.isEmpty() ? null : map;
        });
    }

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

    public boolean putIfAbsent(X x, Y y, Z z) {
        Set<Z> existing = null;
        ConcurrentHashMap<Y, Set<Z>> inner = xyIndex.get(x);
        if (inner != null) {
            existing = inner.get(y);
        }
        if (existing != null && existing.contains(z)) {
            return false;
        }
        put(x, y, z);
        return true;
    }

    @Override
    public List<Z> getZ(X x, Y y) {
        ConcurrentHashMap<Y, Set<Z>> inner = xyIndex.get(x);
        if (inner == null) return List.of();
        Set<Z> result = inner.get(y);
        return result != null ? List.copyOf(result) : List.of();
    }

    @Override
    public List<Y> getY(X x, Z z) {
        ConcurrentHashMap<X, Set<Y>> inner = zxIndex.get(z);
        if (inner == null) return List.of();
        Set<Y> result = inner.get(x);
        return result != null ? List.copyOf(result) : List.of();
    }

    @Override
    public List<X> getX(Y y, Z z) {
        ConcurrentHashMap<Z, Set<X>> inner = yzIndex.get(y);
        if (inner == null) return List.of();
        Set<X> result = inner.get(z);
        return result != null ? List.copyOf(result) : List.of();
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
        return List.copyOf(xyIndex.keySet());
    }

    @Override
    public List<Y> getAllY() {
        return List.copyOf(yzIndex.keySet());
    }

    @Override
    public List<Z> getAllZ() {
        return List.copyOf(zxIndex.keySet());
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

    private <K, V, R, S> Map<R, S> buildMapFromIndex(ConcurrentHashMap<K, Set<V>> indexMap, java.util.function.Function<K, R> keyMapper, java.util.function.Function<V, S> valueMapper) {
        Map<R, S> result = new LinkedHashMap<>();
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

    private <K, V> Map<V, K> reverseMapFromIndex(ConcurrentHashMap<K, Set<V>> indexMap) {
        Map<V, K> result = new LinkedHashMap<>();
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