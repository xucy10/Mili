package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import org.apache.commons.lang3.Validate;

public class LinearPalette<T> implements Palette<T>, ca.spottedleaf.moonrise.patches.fast_palette.FastPalette<T> { // Paper - optimise palette reads
    private final T[] values;
    private final int bits;
    private int size;

    // Paper start - optimise palette reads
    @Override
    public final T[] moonrise$getRawPalette(final ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<T> container) {
        return this.values;
    }
    // Paper end - optimise palette reads

    private LinearPalette(int bits, List<T> values) {
        this.values = (T[])(new Object[1 << bits]);
        this.bits = bits;
        Validate.isTrue(values.size() <= this.values.length, "Can't initialize LinearPalette of size %d with %d entries", this.values.length, values.size());

        for (int i = 0; i < values.size(); i++) {
            this.values[i] = values.get(i);
        }

        this.size = values.size();
    }

    private LinearPalette(T[] values, int bits, int size) {
        this.values = values;
        this.bits = bits;
        this.size = size;
    }

    public static <A> Palette<A> create(int bits, List<A> values) {
        return new LinearPalette<>(bits, values);
    }

    @Override
    public int idFor(T state, PaletteResize<T> resizeHandler) {
        for (int i = 0; i < this.size; i++) {
            if (this.values[i] == state) {
                return i;
            }
        }

        int ix = this.size;
        if (ix < this.values.length) {
            this.values[ix] = state;
            this.size++;
            return ix;
        } else {
            return resizeHandler.onResize(this.bits + 1, state);
        }
    }

    @Override
    public boolean maybeHas(Predicate<T> filter) {
        for (int i = 0; i < this.size; i++) {
            if (filter.test(this.values[i])) {
                return true;
            }
        }

        return false;
    }

    @Override
    public T valueFor(int id) {
        if (id >= 0 && id < this.size) {
            return this.values[id];
        } else {
            throw new MissingPaletteEntryException(id);
        }
    }

    @Override
    public void read(FriendlyByteBuf buffer, IdMap<T> map) {
        this.size = buffer.readVarInt();

        for (int i = 0; i < this.size; i++) {
            this.values[i] = map.byIdOrThrow(buffer.readVarInt());
        }
    }

    @Override
    public void write(FriendlyByteBuf buffer, IdMap<T> map) {
        buffer.writeVarInt(this.size);

        for (int i = 0; i < this.size; i++) {
            buffer.writeVarInt(map.getId(this.values[i]));
        }
    }

    @Override
    public int getSerializedSize(IdMap<T> map) {
        int byteSize = VarInt.getByteSize(this.getSize());

        for (int i = 0; i < this.getSize(); i++) {
            byteSize += VarInt.getByteSize(map.getId(this.values[i]));
        }

        return byteSize;
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public Palette<T> copy() {
        return new LinearPalette<>((T[])((Object[])this.values.clone()), this.bits, this.size);
    }
}
