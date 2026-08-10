package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

public interface Palette<T> extends ca.spottedleaf.moonrise.patches.fast_palette.FastPalette<T> { // Paper - optimise palette reads
    int idFor(T state, PaletteResize<T> resizeHandler);

    boolean maybeHas(Predicate<T> filter);

    T valueFor(int id);

    void read(FriendlyByteBuf buffer, IdMap<T> map);

    void write(FriendlyByteBuf buffer, IdMap<T> map);

    int getSerializedSize(IdMap<T> map);

    int getSize();

    Palette<T> copy();

    public interface Factory {
        <A> Palette<A> create(int bits, List<A> values);
    }
}
