package net.minecraft.world.level.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;

public class HashMapPalette<T> implements Palette<T>, ca.spottedleaf.moonrise.patches.fast_palette.FastPalette<T> { // Paper - optimise palette reads
    private final CrudeIncrementalIntIdentityHashBiMap<T> values;
    private final int bits;

    // Paper start - optimise palette reads
    @Override
    public final T[] moonrise$getRawPalette(final ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<T> container) {
        return ((ca.spottedleaf.moonrise.patches.fast_palette.FastPalette<T>)this.values).moonrise$getRawPalette(container);
    }
    // Paper end - optimise palette reads

    public HashMapPalette(int bits, List<T> values) {
        this(bits);
        values.forEach(this.values::add);
    }

    public HashMapPalette(int bits) {
        this(bits, CrudeIncrementalIntIdentityHashBiMap.create((1 << bits) + 1)); // Paper - Perf: Avoid unnecessary resize operation in CrudeIncrementalIntIdentityHashBiMap
    }

    private HashMapPalette(int bits, CrudeIncrementalIntIdentityHashBiMap<T> values) {
        this.bits = bits;
        this.values = values;
    }

    public static <A> Palette<A> create(int bits, List<A> values) {
        return new HashMapPalette<>(bits, values);
    }

    @Override
    public int idFor(T state, PaletteResize<T> resizeHandler) {
        int id = this.values.getId(state);
        if (id == -1) {
            // Paper start - Perf: Avoid unnecessary resize operation in CrudeIncrementalIntIdentityHashBiMap and optimize
            // We use size() instead of the result from add(K)
            // This avoids adding another object unnecessarily
            // Without this change, + 2 would be required in the constructor
            if (this.values.size() >= 1 << this.bits) {
                id = resizeHandler.onResize(this.bits + 1, state);
            } else {
                id = this.values.add(state);
            }
            // Paper end - Perf: Avoid unnecessary resize operation in CrudeIncrementalIntIdentityHashBiMap and optimize
        }

        return id;
    }

    @Override
    public boolean maybeHas(Predicate<T> filter) {
        for (int i = 0; i < this.getSize(); i++) {
            if (filter.test(this.values.byId(i))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public T valueFor(int id) {
        T object = this.values.byId(id);
        if (object == null) {
            throw new MissingPaletteEntryException(id);
        } else {
            return object;
        }
    }

    @Override
    public void read(FriendlyByteBuf buffer, IdMap<T> map) {
        this.values.clear();
        int varInt = buffer.readVarInt();

        for (int i = 0; i < varInt; i++) {
            this.values.add(map.byIdOrThrow(buffer.readVarInt()));
        }
    }

    @Override
    public void write(FriendlyByteBuf buffer, IdMap<T> map) {
        int size = this.getSize();
        buffer.writeVarInt(size);

        for (int i = 0; i < size; i++) {
            buffer.writeVarInt(map.getId(this.values.byId(i)));
        }
    }

    @Override
    public int getSerializedSize(IdMap<T> map) {
        int byteSize = VarInt.getByteSize(this.getSize());

        for (int i = 0; i < this.getSize(); i++) {
            byteSize += VarInt.getByteSize(map.getId(this.values.byId(i)));
        }

        return byteSize;
    }

    public List<T> getEntries() {
        ArrayList<T> list = new ArrayList<>();
        this.values.iterator().forEachRemaining(list::add);
        return list;
    }

    @Override
    public int getSize() {
        return this.values.size();
    }

    @Override
    public Palette<T> copy() {
        return new HashMapPalette<>(this.bits, this.values.copy());
    }
}
