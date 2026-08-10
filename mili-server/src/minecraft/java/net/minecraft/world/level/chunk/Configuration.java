package net.minecraft.world.level.chunk;

import java.util.List;

public interface Configuration {
    boolean alwaysRepack();

    int bitsInMemory();

    int bitsInStorage();

    <T> Palette<T> createPalette(Strategy<T> strategy, List<T> values);

    public record Global(@Override int bitsInMemory, @Override int bitsInStorage) implements Configuration {
        @Override
        public boolean alwaysRepack() {
            return true;
        }

        @Override
        public <T> Palette<T> createPalette(Strategy<T> strategy, List<T> values) {
            return strategy.globalPalette();
        }
    }

    public record Simple(Palette.Factory factory, int bits) implements Configuration {
        @Override
        public boolean alwaysRepack() {
            return false;
        }

        @Override
        public <T> Palette<T> createPalette(Strategy<T> strategy, List<T> values) {
            return this.factory.create(this.bits, values);
        }

        @Override
        public int bitsInMemory() {
            return this.bits;
        }

        @Override
        public int bitsInStorage() {
            return this.bits;
        }
    }
}
