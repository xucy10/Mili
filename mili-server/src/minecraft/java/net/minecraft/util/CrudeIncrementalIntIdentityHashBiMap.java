package net.minecraft.util;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import java.util.Arrays;
import java.util.Iterator;
import net.minecraft.core.IdMap;
import org.jspecify.annotations.Nullable;

public class CrudeIncrementalIntIdentityHashBiMap<K> implements IdMap<K>, ca.spottedleaf.moonrise.patches.fast_palette.FastPalette<K> { // Paper - optimise palette reads
    private static final int NOT_FOUND = -1;
    private static final Object EMPTY_SLOT = null;
    private static final float LOADFACTOR = 0.8F;
    private @Nullable K[] keys;
    private int[] values;
    private @Nullable K[] byId;
    private int nextId;
    private int size;

    // Paper start - optimise palette reads
    private ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<K> reference;

    @Override
    public final K[] moonrise$getRawPalette(final ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<K> src) {
        this.reference = src;
        return this.byId;
    }
    // Paper end - optimise palette reads

    private CrudeIncrementalIntIdentityHashBiMap(int size) {
        this.keys = (K[])(new Object[size]);
        this.values = new int[size];
        this.byId = (K[])(new Object[size]);
    }

    private CrudeIncrementalIntIdentityHashBiMap(K[] keys, int[] values, K[] byId, int nextId, int size) {
        this.keys = keys;
        this.values = values;
        this.byId = byId;
        this.nextId = nextId;
        this.size = size;
    }

    public static <A> CrudeIncrementalIntIdentityHashBiMap<A> create(int size) {
        return new CrudeIncrementalIntIdentityHashBiMap((int)(size / 0.8F));
    }

    @Override
    public int getId(@Nullable K value) {
        return this.getValue(this.indexOf(value, this.hash(value)));
    }

    @Override
    public @Nullable K byId(int id) {
        return id >= 0 && id < this.byId.length ? this.byId[id] : null;
    }

    private int getValue(int key) {
        return key == -1 ? -1 : this.values[key];
    }

    public boolean contains(K value) {
        return this.getId(value) != -1;
    }

    public boolean contains(int id) {
        return this.byId(id) != null;
    }

    public int add(K object) {
        int i = this.nextId();
        this.addMapping(object, i);
        return i;
    }

    private int nextId() {
        while (this.nextId < this.byId.length && this.byId[this.nextId] != null) {
            this.nextId++;
        }

        return this.nextId;
    }

    private void grow(int capacity) {
        K[] objects = this.keys;
        int[] ints = this.values;
        CrudeIncrementalIntIdentityHashBiMap<K> crudeIncrementalIntIdentityHashBiMap = new CrudeIncrementalIntIdentityHashBiMap<>(capacity);

        for (int i = 0; i < objects.length; i++) {
            if (objects[i] != null) {
                crudeIncrementalIntIdentityHashBiMap.addMapping(objects[i], ints[i]);
            }
        }

        this.keys = crudeIncrementalIntIdentityHashBiMap.keys;
        this.values = crudeIncrementalIntIdentityHashBiMap.values;
        this.byId = crudeIncrementalIntIdentityHashBiMap.byId;
        this.nextId = crudeIncrementalIntIdentityHashBiMap.nextId;
        this.size = crudeIncrementalIntIdentityHashBiMap.size;
        // Paper start - optimise palette reads
        final ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<K> ref = this.reference;
        if (ref != null) {
            ref.moonrise$setPalette(this.byId);
        }
        // Paper end - optimise palette reads
    }

    public void addMapping(K object, int intKey) {
        int max = Math.max(intKey, this.size + 1);
        if (max >= this.keys.length * 0.8F) {
            int i = this.keys.length << 1;

            while (i < intKey) {
                i <<= 1;
            }

            this.grow(i);
        }

        int i = this.findEmpty(this.hash(object));
        this.keys[i] = object;
        this.values[i] = intKey;
        this.byId[intKey] = object;
        this.size++;
        if (intKey == this.nextId) {
            this.nextId++;
        }
    }

    private int hash(@Nullable K object) {
        return (Mth.murmurHash3Mixer(System.identityHashCode(object)) & 2147483647) % this.keys.length;
    }

    private int indexOf(@Nullable K object, int startIndex) {
        for (int i = startIndex; i < this.keys.length; i++) {
            if (this.keys[i] == object) {
                return i;
            }

            if (this.keys[i] == EMPTY_SLOT) {
                return -1;
            }
        }

        for (int i = 0; i < startIndex; i++) {
            if (this.keys[i] == object) {
                return i;
            }

            if (this.keys[i] == EMPTY_SLOT) {
                return -1;
            }
        }

        return -1;
    }

    private int findEmpty(int startIndex) {
        for (int i = startIndex; i < this.keys.length; i++) {
            if (this.keys[i] == EMPTY_SLOT) {
                return i;
            }
        }

        for (int ix = 0; ix < startIndex; ix++) {
            if (this.keys[ix] == EMPTY_SLOT) {
                return ix;
            }
        }

        throw new RuntimeException("Overflowed :(");
    }

    @Override
    public Iterator<K> iterator() {
        return Iterators.filter(Iterators.forArray(this.byId), Predicates.notNull());
    }

    public void clear() {
        Arrays.fill(this.keys, null);
        Arrays.fill(this.byId, null);
        this.nextId = 0;
        this.size = 0;
    }

    @Override
    public int size() {
        return this.size;
    }

    public CrudeIncrementalIntIdentityHashBiMap<K> copy() {
        return new CrudeIncrementalIntIdentityHashBiMap<>(
            (K[])((Object[])this.keys.clone()), (int[])this.values.clone(), (K[])((Object[])this.byId.clone()), this.nextId, this.size
        );
    }
}
