package net.minecraft.world.level.chunk;

import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

public class GlobalPalette<T> implements Palette<T> {
    private final IdMap<T> registry;

    public GlobalPalette(IdMap<T> registry) {
        this.registry = registry;
    }

    @Override
    public int idFor(T state, PaletteResize<T> resizeHandler) {
        int id = this.registry.getId(state);
        return id == -1 ? 0 : id;
    }

    @Override
    public boolean maybeHas(Predicate<T> filter) {
        return true;
    }

    @Override
    public T valueFor(int id) {
        T object = this.registry.byId(id);
        if (object == null) {
            throw new MissingPaletteEntryException(id);
        } else {
            return object;
        }
    }

    @Override
    public void read(FriendlyByteBuf buffer, IdMap<T> map) {
    }

    @Override
    public void write(FriendlyByteBuf buffer, IdMap<T> map) {
    }

    @Override
    public int getSerializedSize(IdMap<T> map) {
        return 0;
    }

    @Override
    public int getSize() {
        return this.registry.size();
    }

    @Override
    public Palette<T> copy() {
        return this;
    }
}
